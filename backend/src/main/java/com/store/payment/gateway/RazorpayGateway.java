package com.store.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.store.common.config.AppProperties;
import com.store.common.util.Hashing;
import com.store.common.util.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Razorpay Orders + Payments + Refunds REST API (https://razorpay.com/docs/api/).
 * Plain HttpClient with Basic auth; we never see card data (Standard Checkout runs in the browser).
 * Calls retry with backoff on network errors and 5xx; 4xx are surfaced immediately.
 */
@Component
public class RazorpayGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);
    private static final int MAX_ATTEMPTS = 3;

    private final HttpClient http;
    private final Json json;
    private final AppProperties.Razorpay cfg;

    public RazorpayGateway(HttpClient http, Json json, AppProperties props) {
        this.http = http;
        this.json = json;
        this.cfg = props.razorpay();
    }

    @Override
    public String keyId() {
        return cfg.keyId();
    }

    @Override
    public GatewayOrder createOrder(long amountPaise, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", "INR");
        body.put("receipt", receipt);
        body.put("notes", notes);
        JsonNode n = call("POST", "/orders", body, MAX_ATTEMPTS);
        return new GatewayOrder(n.path("id").asText(), n.path("amount").asLong(), n.path("currency").asText(),
                n.path("receipt").asText());
    }

    @Override
    public GatewayRefund createRefund(String paymentId, long amountPaise, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("speed", "normal");
        body.put("receipt", receipt);
        body.put("notes", notes);
        // No blind retry here: a repeated refund call could refund twice. The outbox retries after
        // checking listRefunds() for our receipt.
        return refund(call("POST", "/payments/" + paymentId + "/refund", body, 1));
    }

    @Override
    public List<GatewayRefund> listRefunds(String paymentId) {
        JsonNode n = call("GET", "/payments/" + paymentId + "/refunds?count=100", null, MAX_ATTEMPTS);
        List<GatewayRefund> out = new ArrayList<>();
        n.path("items").forEach(r -> out.add(refund(r)));
        return out;
    }

    @Override
    public List<GatewayPayment> listPayments(Instant from, Instant to) {
        List<GatewayPayment> out = new ArrayList<>();
        int skip = 0;
        while (true) {
            JsonNode n = call("GET", "/payments?from=" + from.getEpochSecond() + "&to=" + (to.getEpochSecond() - 1)
                    + "&count=100&skip=" + skip, null, MAX_ATTEMPTS);
            JsonNode items = n.path("items");
            items.forEach(p -> out.add(new GatewayPayment(p.path("id").asText(), p.path("order_id").asText(null),
                    p.path("amount").asLong(), p.path("status").asText(), p.path("method").asText(null),
                    Instant.ofEpochSecond(p.path("created_at").asLong()))));
            if (items.size() < 100) {
                return out;
            }
            skip += 100;
        }
    }

    @Override
    public boolean verifyPaymentSignature(String gatewayOrderId, String gatewayPaymentId, String signature) {
        String expected = Hashing.hmacSha256Hex(cfg.keySecret(), gatewayOrderId + "|" + gatewayPaymentId);
        return Hashing.constantTimeEquals(expected, signature);
    }

    @Override
    public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
        if (cfg.webhookSecret() == null || cfg.webhookSecret().isBlank()) {
            log.error("RAZORPAY_WEBHOOK_SECRET is not set; rejecting webhook");
            return false;
        }
        String expected = Hashing.hmacSha256Hex(cfg.webhookSecret(), rawBody);
        return Hashing.constantTimeEquals(expected, signature);
    }

    private static GatewayRefund refund(JsonNode r) {
        return new GatewayRefund(r.path("id").asText(), r.path("payment_id").asText(), r.path("amount").asLong(),
                r.path("status").asText(), r.path("receipt").asText(null));
    }

    private JsonNode call(String method, String path, Object body, int maxAttempts) {
        String auth = Base64.getEncoder()
                .encodeToString((cfg.keyId() + ":" + cfg.keySecret()).getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(cfg.baseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/json");
        if ("POST".equals(method)) {
            b.POST(HttpRequest.BodyPublishers.ofString(json.write(body)));
        } else {
            b.GET();
        }
        HttpRequest req = b.build();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() >= 200 && res.statusCode() < 300) {
                    return json.read(res.body());
                }
                if (res.statusCode() < 500 && res.statusCode() != 429) {
                    JsonNode err = safeRead(res.body());
                    throw new GatewayException(res.statusCode(),
                            err.path("error").path("code").asText("BAD_REQUEST"),
                            err.path("error").path("description").asText(res.body()));
                }
                last = new GatewayException(res.statusCode(), "GATEWAY_ERROR", "Razorpay returned " + res.statusCode());
            } catch (GatewayException e) {
                if (e.status() < 500 && e.status() != 429) {
                    throw e;
                }
                last = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GatewayException(0, "INTERRUPTED", "Interrupted");
            } catch (Exception e) {
                last = new GatewayException(0, "NETWORK_ERROR", e.getMessage());
            }
            if (attempt < maxAttempts) {
                sleep(attempt);
            }
        }
        throw last;
    }

    private JsonNode safeRead(String body) {
        try {
            return json.read(body);
        } catch (Exception e) {
            return json.read("{}");
        }
    }

    private static void sleep(int attempt) {
        try {
            Thread.sleep(300L * attempt * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class GatewayException extends RuntimeException {
        private final int status;
        private final String code;

        public GatewayException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public int status() {
            return status;
        }

        public String code() {
            return code;
        }
    }
}
