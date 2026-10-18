package com.store.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.store.common.config.AppProperties;
import com.store.common.util.Json;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shiprocket API client. The backend logs in with the API user's email/password and caches the token,
 * refreshing a day before it expires (tokens currently last 10 days).
 * Verify endpoint names, fields and rate limits against Shiprocket's current docs while building phase 6.
 */
public class ShiprocketProvider implements ShippingProvider {

    private static final Logger log = LoggerFactory.getLogger(ShiprocketProvider.class);
    private static final Duration TOKEN_LIFETIME = Duration.ofDays(9);

    private final HttpClient http;
    private final Json json;
    private final AppProperties.Shiprocket cfg;
    private final Clock clock;
    private volatile String token;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public ShiprocketProvider(HttpClient http, Json json, AppProperties.Shiprocket cfg, Clock clock) {
        this.http = http;
        this.json = json;
        this.cfg = cfg;
        this.clock = clock;
    }

    @Override
    public Serviceability checkServiceability(String pickupPincode, String deliveryPincode, int weightGrams) {
        String kg = BigDecimal.valueOf(Math.max(weightGrams, 100)).divide(BigDecimal.valueOf(1000), 2, RoundingMode.UP)
                .toPlainString();
        String q = "pickup_postcode=" + enc(pickupPincode) + "&delivery_postcode=" + enc(deliveryPincode)
                + "&weight=" + kg + "&cod=0";
        JsonNode res = get("/courier/serviceability/?" + q);
        JsonNode couriers = res.path("data").path("available_courier_companies");
        if (!couriers.isArray() || couriers.isEmpty()) {
            return new Serviceability(false, null, null, null);
        }
        // Cheapest courier; ETA from the same courier.
        JsonNode best = null;
        for (JsonNode c : couriers) {
            if (best == null || c.path("rate").asDouble() < best.path("rate").asDouble()) {
                best = c;
            }
        }
        Integer eta = best.hasNonNull("estimated_delivery_days") ? best.path("estimated_delivery_days").asInt() : null;
        long ratePaise = BigDecimal.valueOf(best.path("rate").asDouble()).movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP).longValue();
        return new Serviceability(true, eta, ratePaise, best.path("courier_name").asText(null));
    }

    private JsonNode get(String path) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token())
                .GET().build();
        return send(req, true);
    }

    private JsonNode send(HttpRequest req, boolean retryOnAuth) {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401 && retryOnAuth) {
                tokenExpiresAt = Instant.EPOCH;
                HttpRequest again = HttpRequest.newBuilder(req.uri()).timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + token()).GET().build();
                return send(again, false);
            }
            if (res.statusCode() >= 300) {
                throw new IllegalStateException("Shiprocket " + res.statusCode() + ": " + res.body());
            }
            return json.read(res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Shiprocket unreachable: " + e.getMessage(), e);
        }
    }

    private synchronized String token() {
        if (token != null && clock.instant().isBefore(tokenExpiresAt)) {
            return token;
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.baseUrl() + "/auth/login"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.write(Map.of("email", cfg.email(), "password", cfg.password()))))
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                throw new IllegalStateException("Shiprocket login failed: " + res.statusCode());
            }
            token = json.read(res.body()).path("token").asText();
            tokenExpiresAt = clock.instant().plus(TOKEN_LIFETIME);
            log.info("Shiprocket token refreshed");
            return token;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Shiprocket unreachable: " + e.getMessage(), e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
