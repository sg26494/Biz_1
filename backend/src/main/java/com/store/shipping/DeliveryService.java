package com.store.shipping;

import com.store.common.error.ApiException;
import com.store.common.settings.SettingsService;
import com.store.inventory.service.WarehouseService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pincode serviceability with a short in-memory cache (no pincode table at launch).
 * Cache key buckets weight to the next 500 g so a few calls cover most carts.
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public record DeliveryCheck(String pincode, boolean serviceable, Integer etaDays, LocalDate estimatedDelivery,
                                String message) {}

    private record Cached(ShippingProvider.Serviceability value, Instant at) {}

    private final ShippingProvider provider;
    private final WarehouseService warehouses;
    private final SettingsService settings;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public DeliveryService(ShippingProvider provider, WarehouseService warehouses, SettingsService settings,
                           Clock clock) {
        this.provider = provider;
        this.warehouses = warehouses;
        this.settings = settings;
        this.clock = clock;
    }

    public DeliveryCheck check(String pincode, int weightGrams) {
        if (pincode == null || !pincode.matches("^[1-9]\\d{5}$")) {
            throw ApiException.badRequest("PINCODE_INVALID", "Enter a valid 6-digit pincode");
        }
        int bucket = ((Math.max(weightGrams, 1) + 499) / 500) * 500;
        String pickup = warehouses.defaultWarehouse().pincode();
        String key = pincode + ":" + bucket;
        Cached c = cache.get(key);
        ShippingProvider.Serviceability s;
        if (c != null && c.at().plus(CACHE_TTL).isAfter(clock.instant())) {
            s = c.value();
        } else {
            try {
                s = provider.checkServiceability(pickup, pincode, bucket);
                cache.put(key, new Cached(s, clock.instant()));
            } catch (Exception e) {
                // Don't block sales if the courier API is down: assume serviceable, flag in logs.
                log.warn("Serviceability check failed for {}: {}", pincode, e.getMessage());
                s = new ShippingProvider.Serviceability(true, null, null, null);
            }
        }
        if (!s.serviceable()) {
            return new DeliveryCheck(pincode, false, null, null, "Sorry, we don't deliver to this pincode yet");
        }
        int eta = s.etaDays() != null ? s.etaDays() : settings.getInt(SettingsService.SHIPPING_DEFAULT_ETA_DAYS, 5);
        // +1 day for packing.
        LocalDate date = LocalDate.now(clock.withZone(IST)).plusDays(eta + 1L);
        return new DeliveryCheck(pincode, true, eta + 1, date, "Delivery by " + date);
    }

    public void requireServiceable(String pincode, int weightGrams) {
        if (!check(pincode, weightGrams).serviceable()) {
            throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "PINCODE_NOT_SERVICEABLE",
                    "Sorry, we don't deliver to this pincode yet");
        }
    }
}
