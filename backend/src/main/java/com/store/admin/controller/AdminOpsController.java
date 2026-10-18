package com.store.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.store.auth.security.CurrentUser;
import com.store.common.audit.AuditService;
import com.store.common.audit.AuditService.AuditRow;
import com.store.common.settings.SettingsService;
import com.store.common.settings.SettingsService.Setting;
import com.store.common.web.PageResponse;
import com.store.inventory.service.WarehouseService;
import com.store.inventory.service.WarehouseService.Warehouse;
import com.store.inventory.service.WarehouseService.WarehouseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard, settings, pickup address and the audit log. Reports/GST export arrive in phase 8. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOpsController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public record SettingUpdate(@NotNull JsonNode value) {}

    public record LowStockItem(long variantId, String sku, String productTitle, String variantTitle, int available) {}

    public record Dashboard(long ordersToday, long revenueTodayPaise, long revenueLast30DaysPaise, long pendingToPack,
                            long awaitingPayment, long openMismatches, long failedRefunds, long failedWebhooks,
                            List<LowStockItem> lowStock) {}

    private final SettingsService settings;
    private final WarehouseService warehouses;
    private final AuditService audit;
    private final JdbcClient jdbc;

    public AdminOpsController(SettingsService settings, WarehouseService warehouses, AuditService audit,
                              JdbcClient jdbc) {
        this.settings = settings;
        this.warehouses = warehouses;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        var todayStart = LocalDate.now(IST).atStartOfDay(IST).toOffsetDateTime();
        var monthStart = LocalDate.now(IST).minusDays(30).atStartOfDay(IST).toOffsetDateTime();
        Map<String, Object> m = jdbc.sql("""
                SELECT
                  (SELECT COUNT(*) FROM orders WHERE paid_at >= :today) AS orders_today,
                  (SELECT COALESCE(SUM(total_paise), 0) FROM orders WHERE paid_at >= :today AND status <> 'CANCELLED')
                    AS revenue_today,
                  (SELECT COALESCE(SUM(total_paise), 0) FROM orders WHERE paid_at >= :month AND status <> 'CANCELLED')
                    AS revenue_30d,
                  (SELECT COUNT(*) FROM orders WHERE status = 'PAID') AS pending_to_pack,
                  (SELECT COUNT(*) FROM orders WHERE status IN ('PENDING_PAYMENT', 'PAYMENT_FAILED')) AS awaiting_payment,
                  (SELECT COUNT(*) FROM payment_mismatches WHERE resolved_at IS NULL) AS open_mismatches,
                  (SELECT COUNT(*) FROM refunds WHERE status = 'FAILED') AS failed_refunds,
                  (SELECT COUNT(*) FROM webhook_events WHERE status = 'FAILED') AS failed_webhooks
                """).param("today", todayStart).param("month", monthStart).query().singleRow();
        int threshold = settings.getInt(SettingsService.LOW_STOCK_DEFAULT, 3);
        List<LowStockItem> low = jdbc.sql("""
                SELECT pv.id AS variant_id, pv.sku, p.title AS product_title, pv.title AS variant_title,
                       (i.on_hand - i.reserved) AS available
                FROM inventory i JOIN product_variants pv ON pv.id = i.variant_id JOIN products p ON p.id = pv.product_id
                WHERE p.status = 'ACTIVE' AND pv.is_active
                  AND (i.on_hand - i.reserved) <= COALESCE(i.low_stock_threshold, :t)
                ORDER BY available, p.title LIMIT 20
                """).param("t", threshold).query(LowStockItem.class).list();
        return new Dashboard(num(m, "orders_today"), num(m, "revenue_today"), num(m, "revenue_30d"),
                num(m, "pending_to_pack"), num(m, "awaiting_payment"), num(m, "open_mismatches"),
                num(m, "failed_refunds"), num(m, "failed_webhooks"), low);
    }

    private static long num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    @GetMapping("/settings")
    public Collection<Setting> settings() {
        return settings.all().values();
    }

    @PutMapping("/settings/{key}")
    public Setting updateSetting(@PathVariable String key, @Valid @RequestBody SettingUpdate req) {
        settings.update(key, req.value(), CurrentUser.id());
        return settings.all().get(key);
    }

    @GetMapping("/settings/warehouse")
    public Warehouse warehouse() {
        return warehouses.defaultWarehouse();
    }

    @PutMapping("/settings/warehouse")
    public Warehouse updateWarehouse(@Valid @RequestBody WarehouseRequest req) {
        return warehouses.update(req, CurrentUser.id());
    }

    @GetMapping("/audit-logs")
    public List<AuditRow> auditLogs(@RequestParam(required = false) String entityType,
                                    @RequestParam(required = false) String entityId,
                                    @RequestParam(required = false) Long actorUserId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return audit.search(entityType, entityId, actorUserId, Math.max(0, page), PageResponse.clampSize(size, 200));
    }
}
