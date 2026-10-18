package com.store.common.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.store.common.audit.AuditService;
import com.store.common.error.ApiException;
import com.store.common.util.Json;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules (free-shipping threshold, return window, reservation TTL...) live in app_settings
 * so admins can change them without a deploy. Cached for 30 seconds.
 */
@Service
public class SettingsService {

    public static final String SHIPPING_FLAT_FEE = "shipping.flat_fee_paise";
    public static final String SHIPPING_FREE_THRESHOLD = "shipping.free_threshold_paise";
    public static final String SHIPPING_DEFAULT_ETA_DAYS = "shipping.default_eta_days";
    public static final String RETURN_WINDOW_DAYS = "returns.window_days";
    public static final String RESERVATION_TTL_MINUTES = "checkout.reservation_ttl_minutes";
    public static final String MAX_QTY_PER_ITEM = "cart.max_qty_per_item";
    public static final String LOW_STOCK_DEFAULT = "inventory.low_stock_default";

    private static final Duration TTL = Duration.ofSeconds(30);

    public record Setting(String key, JsonNode value, String description) {}

    private record SettingRow(String key, String value, String description) {}

    private final JdbcClient jdbc;
    private final Json json;
    private final AuditService audit;
    private final Clock clock;

    private volatile Map<String, Setting> cache = Map.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    public SettingsService(JdbcClient jdbc, Json json, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.clock = clock;
    }

    public Map<String, Setting> all() {
        if (Duration.between(loadedAt, clock.instant()).compareTo(TTL) > 0) {
            reload();
        }
        return cache;
    }

    private synchronized void reload() {
        List<SettingRow> rows = jdbc.sql("SELECT key, value::text AS value, description FROM app_settings ORDER BY key")
                .query(SettingRow.class).list();
        Map<String, Setting> map = new LinkedHashMap<>();
        for (SettingRow r : rows) {
            map.put(r.key(), new Setting(r.key(), json.read(r.value()), r.description()));
        }
        cache = Map.copyOf(map);
        loadedAt = clock.instant();
    }

    public long getLong(String key, long fallback) {
        Setting s = all().get(key);
        return s == null || s.value().isNull() ? fallback : s.value().asLong(fallback);
    }

    /** Returns null when the setting is explicitly null (e.g. "never free shipping"). */
    public Long getNullableLong(String key) {
        Setting s = all().get(key);
        return s == null || s.value().isNull() ? null : s.value().asLong();
    }

    public int getInt(String key, int fallback) {
        return (int) getLong(key, fallback);
    }

    public boolean getBoolean(String key, boolean fallback) {
        Setting s = all().get(key);
        return s == null || s.value().isNull() ? fallback : s.value().asBoolean(fallback);
    }

    public String getString(String key, String fallback) {
        Setting s = all().get(key);
        return s == null || s.value().isNull() ? fallback : s.value().asText(fallback);
    }

    @Transactional
    public void update(String key, JsonNode value, long actorUserId) {
        Setting before = all().get(key);
        if (before == null) {
            throw ApiException.notFound("Setting " + key);
        }
        if (before.value().isNumber() && !(value.isNumber() || value.isNull())) {
            throw ApiException.badRequest("INVALID_SETTING", key + " must be a number");
        }
        if (before.value().isBoolean() && !value.isBoolean()) {
            throw ApiException.badRequest("INVALID_SETTING", key + " must be true or false");
        }
        jdbc.sql("UPDATE app_settings SET value = CAST(:v AS jsonb), updated_at = now(), updated_by = :by WHERE key = :k")
                .param("v", json.write(value))
                .param("by", actorUserId)
                .param("k", key)
                .update();
        audit.record(actorUserId, "SETTING_UPDATED", "app_setting", key, before.value(), value, null);
        loadedAt = Instant.EPOCH;
    }
}
