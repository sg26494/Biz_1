package com.store.common.web;

import com.store.common.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Small in-memory fixed-window limiter for OTP, login, coupon and checkout endpoints.
 * Good enough for one API instance behind Cloudflare; swap for a DB/Redis limiter if you scale out.
 */
@Component
public class RateLimiter {

    private record Window(long startMillis, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String key, int limit, Duration window) {
        long now = clock.millis();
        long windowMillis = window.toMillis();
        Window updated = windows.compute(key, (k, w) -> {
            if (w == null || now - w.startMillis() >= windowMillis) {
                return new Window(now, 1);
            }
            return new Window(w.startMillis(), w.count() + 1);
        });
        if (updated.count() > limit) {
            long retryAfter = Math.max(1, (updated.startMillis() + windowMillis - now) / 1000);
            throw ApiException.tooManyRequests(retryAfter);
        }
        if (windows.size() > 50_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMillis() > Duration.ofHours(2).toMillis());
        }
    }
}
