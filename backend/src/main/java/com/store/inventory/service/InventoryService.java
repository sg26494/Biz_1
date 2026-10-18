package com.store.inventory.service;

import com.store.common.audit.AuditService;
import com.store.common.error.ApiException;
import com.store.common.util.Db;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock = inventory(on_hand, reserved) per variant and warehouse; available = on_hand - reserved.
 * All changes are single conditional UPDATEs (no read-then-write races) and every change appends
 * an inventory_movements row.
 */
@Service
public class InventoryService {

    public record Line(long variantId, int qty) {}

    public enum AdjustReason { PURCHASE, DAMAGE, CORRECTION }

    private record StockAfter(int onHand, int reserved) {}

    private record Reservation(long id, long variantId, long warehouseId, int qty) {}

    private record Avail(long variantId, int available) {}

    private final JdbcClient jdbc;
    private final AuditService audit;

    public InventoryService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public long defaultWarehouseId() {
        return jdbc.sql("SELECT id FROM warehouses WHERE is_default").query(Long.class).single();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void ensureRow(long variantId) {
        jdbc.sql("""
                INSERT INTO inventory (variant_id, warehouse_id) SELECT :v, id FROM warehouses WHERE is_default
                ON CONFLICT DO NOTHING
                """).param("v", variantId).update();
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> availableByVariant(List<Long> variantIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (variantIds.isEmpty()) {
            return out;
        }
        jdbc.sql("""
                SELECT variant_id, COALESCE(SUM(on_hand - reserved), 0)::int AS available
                FROM inventory WHERE variant_id IN (:ids) GROUP BY variant_id
                """).param("ids", variantIds).query(Avail.class).list()
                .forEach(a -> out.put(a.variantId(), a.available()));
        return out;
    }

    /**
     * Reserve at checkout start. Lines are locked in variant-id order to avoid deadlocks.
     * Any line short of stock throws OUT_OF_STOCK and the caller's transaction rolls back everything.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(long orderId, long warehouseId, List<Line> lines, Instant expiresAt) {
        for (Line line : lines.stream().sorted(Comparator.comparingLong(Line::variantId)).toList()) {
            Optional<StockAfter> after = jdbc.sql("""
                    UPDATE inventory SET reserved = reserved + :qty, updated_at = now()
                    WHERE variant_id = :v AND warehouse_id = :w AND (on_hand - reserved) >= :qty
                    RETURNING on_hand, reserved
                    """).param("qty", line.qty()).param("v", line.variantId()).param("w", warehouseId)
                    .query(StockAfter.class).optional();
            if (after.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "OUT_OF_STOCK", "Some items are no longer in stock",
                        Map.of("variantId", line.variantId()));
            }
            jdbc.sql("""
                    INSERT INTO inventory_reservations (order_id, variant_id, warehouse_id, qty, status, expires_at)
                    VALUES (:o, :v, :w, :qty, 'ACTIVE', :exp)
                    """).param("o", orderId).param("v", line.variantId()).param("w", warehouseId)
                    .param("qty", line.qty()).param("exp", Db.ts(expiresAt)).update();
            movement(line.variantId(), warehouseId, "RESERVE", 0, line.qty(), after.get(), "ORDER", orderId, null, null);
        }
    }

    /** Payment captured: on_hand -= qty, reserved -= qty, reservation COMMITTED, ledger SALE. Returns lines committed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int commit(long orderId) {
        List<Reservation> active = activeReservations(orderId);
        for (Reservation r : active) {
            StockAfter after = jdbc.sql("""
                    UPDATE inventory SET on_hand = on_hand - :qty, reserved = reserved - :qty, updated_at = now()
                    WHERE variant_id = :v AND warehouse_id = :w
                    RETURNING on_hand, reserved
                    """).param("qty", r.qty()).param("v", r.variantId()).param("w", r.warehouseId())
                    .query(StockAfter.class).single();
            setReservationStatus(r.id(), "COMMITTED");
            movement(r.variantId(), r.warehouseId(), "SALE", -r.qty(), -r.qty(), after, "ORDER", orderId, null, null);
        }
        return active.size();
    }

    /** Payment failed for good / expired / cancelled before payment: reserved -= qty. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(long orderId, boolean expired) {
        for (Reservation r : activeReservations(orderId)) {
            StockAfter after = jdbc.sql("""
                    UPDATE inventory SET reserved = reserved - :qty, updated_at = now()
                    WHERE variant_id = :v AND warehouse_id = :w
                    RETURNING on_hand, reserved
                    """).param("qty", r.qty()).param("v", r.variantId()).param("w", r.warehouseId())
                    .query(StockAfter.class).single();
            setReservationStatus(r.id(), expired ? "EXPIRED" : "RELEASED");
            movement(r.variantId(), r.warehouseId(), "RELEASE", 0, -r.qty(), after, "ORDER", orderId, null,
                    expired ? "reservation expired" : null);
        }
    }

    /**
     * Payment captured after the reservation already expired: take the stock directly if it is still there.
     * Returns false (and changes nothing) if any line is short; the caller then auto-refunds.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryDirectSale(long orderId, long warehouseId, List<Line> lines) {
        for (Line line : lines) {
            Integer free = jdbc.sql("""
                    SELECT on_hand - reserved FROM inventory WHERE variant_id = :v AND warehouse_id = :w FOR UPDATE
                    """).param("v", line.variantId()).param("w", warehouseId).query(Integer.class).optional().orElse(0);
            if (free < line.qty()) {
                return false;
            }
        }
        for (Line line : lines.stream().sorted(Comparator.comparingLong(Line::variantId)).toList()) {
            StockAfter after = jdbc.sql("""
                    UPDATE inventory SET on_hand = on_hand - :qty, updated_at = now()
                    WHERE variant_id = :v AND warehouse_id = :w
                    RETURNING on_hand, reserved
                    """).param("qty", line.qty()).param("v", line.variantId()).param("w", warehouseId)
                    .query(StockAfter.class).single();
            movement(line.variantId(), warehouseId, "SALE", -line.qty(), 0, after, "ORDER", orderId, null,
                    "late payment capture");
        }
        return true;
    }

    /** A paid order cancelled before shipping: put the sold units back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restockCancelled(long orderId, long warehouseId, List<Line> lines, Long actorUserId) {
        for (Line line : lines) {
            StockAfter after = addOnHand(line.variantId(), warehouseId, line.qty());
            movement(line.variantId(), warehouseId, "CANCEL_RESTOCK", line.qty(), 0, after, "ORDER", orderId,
                    actorUserId, null);
        }
    }

    /** After a returned item passes QC (used by the returns module). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restockReturn(long returnId, long variantId, long warehouseId, int qty, Long actorUserId) {
        StockAfter after = addOnHand(variantId, warehouseId, qty);
        movement(variantId, warehouseId, "RETURN", qty, 0, after, "RETURN", returnId, actorUserId, null);
    }

    /** Admin stock adjustment; a reason code is mandatory. */
    @Transactional
    public StockView adjust(long variantId, AdjustReason reason, int delta, String note, long actorUserId) {
        if (delta == 0) {
            throw ApiException.badRequest("INVALID_ADJUSTMENT", "Change must not be zero");
        }
        if (reason == AdjustReason.PURCHASE && delta < 0) {
            throw ApiException.badRequest("INVALID_ADJUSTMENT", "A purchase must add stock");
        }
        if (reason == AdjustReason.DAMAGE && delta > 0) {
            throw ApiException.badRequest("INVALID_ADJUSTMENT", "Damage must remove stock");
        }
        long warehouseId = defaultWarehouseId();
        ensureRow(variantId);
        StockView before = stock(variantId, warehouseId);
        Optional<StockAfter> after = jdbc.sql("""
                UPDATE inventory SET on_hand = on_hand + :d, updated_at = now()
                WHERE variant_id = :v AND warehouse_id = :w AND on_hand + :d >= reserved
                RETURNING on_hand, reserved
                """).param("d", delta).param("v", variantId).param("w", warehouseId)
                .query(StockAfter.class).optional();
        if (after.isEmpty()) {
            throw ApiException.conflict("INSUFFICIENT_STOCK",
                    "Cannot remove more than the unreserved stock (" + before.available() + ")");
        }
        movement(variantId, warehouseId, reason.name(), delta, 0, after.get(), "ADJUSTMENT", null, actorUserId, note);
        StockView updated = stock(variantId, warehouseId);
        audit.record(actorUserId, "STOCK_ADJUSTED", "variant", variantId, before, updated,
                reason.name() + (note == null ? "" : ": " + note));
        return updated;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void setLowStockThreshold(long variantId, Integer threshold) {
        ensureRow(variantId);
        jdbc.sql("UPDATE inventory SET low_stock_threshold = :t WHERE variant_id = :v")
                .param("t", threshold).param("v", variantId).update();
    }

    public record StockView(long variantId, int onHand, int reserved, int available) {}

    @Transactional(readOnly = true)
    public StockView stock(long variantId, long warehouseId) {
        return jdbc.sql("""
                SELECT variant_id, on_hand, reserved, on_hand - reserved AS available
                FROM inventory WHERE variant_id = :v AND warehouse_id = :w
                """).param("v", variantId).param("w", warehouseId).query(StockView.class).optional()
                .orElse(new StockView(variantId, 0, 0, 0));
    }

    // ---------- admin reads ----------

    public record InventoryRow(long variantId, String sku, String variantTitle, long productId, String productTitle,
                               String productStatus, int onHand, int reserved, int available, int lowStockThreshold,
                               boolean lowStock) {}

    @Transactional(readOnly = true)
    public List<InventoryRow> list(String q, boolean lowOnly, int defaultThreshold) {
        String search = q == null ? "" : q.trim();
        return jdbc.sql("""
                SELECT pv.id AS variant_id, pv.sku, pv.title AS variant_title, p.id AS product_id,
                       p.title AS product_title, p.status AS product_status,
                       COALESCE(i.on_hand, 0) AS on_hand, COALESCE(i.reserved, 0) AS reserved,
                       COALESCE(i.on_hand - i.reserved, 0) AS available,
                       COALESCE(i.low_stock_threshold, :def) AS low_stock_threshold,
                       COALESCE(i.on_hand - i.reserved, 0) <= COALESCE(i.low_stock_threshold, :def) AS low_stock
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id
                LEFT JOIN inventory i ON i.variant_id = pv.id
                WHERE pv.is_active AND p.status <> 'ARCHIVED'
                  AND (:q = '' OR pv.sku ILIKE '%' || :q || '%' OR p.title ILIKE '%' || :q || '%')
                  AND (NOT :lowOnly OR COALESCE(i.on_hand - i.reserved, 0) <= COALESCE(i.low_stock_threshold, :def))
                ORDER BY p.title, pv.sort_order, pv.id
                """).param("def", defaultThreshold).param("q", search).param("lowOnly", lowOnly)
                .query(InventoryRow.class).list();
    }

    public record MovementRow(long id, long variantId, String sku, String movementType, int deltaOnHand,
                              int deltaReserved, int onHandAfter, int reservedAfter, String refType, String refId,
                              String note, String actorEmail, OffsetDateTime createdAt) {}

    @Transactional(readOnly = true)
    public List<MovementRow> movements(Long variantId, int page, int size) {
        return jdbc.sql("""
                SELECT m.id, m.variant_id, pv.sku, m.movement_type, m.delta_on_hand, m.delta_reserved,
                       m.on_hand_after, m.reserved_after, m.ref_type, m.ref_id, m.note, u.email AS actor_email,
                       m.created_at
                FROM inventory_movements m
                JOIN product_variants pv ON pv.id = m.variant_id
                LEFT JOIN users u ON u.id = m.actor_user_id
                WHERE (CAST(:v AS bigint) IS NULL OR m.variant_id = :v)
                ORDER BY m.id DESC LIMIT :limit OFFSET :offset
                """).param("v", variantId).param("limit", size).param("offset", page * size)
                .query(MovementRow.class).list();
    }

    // ---------- internals ----------

    private List<Reservation> activeReservations(long orderId) {
        return jdbc.sql("""
                SELECT id, variant_id, warehouse_id, qty FROM inventory_reservations
                WHERE order_id = :o AND status = 'ACTIVE' ORDER BY variant_id FOR UPDATE
                """).param("o", orderId).query(Reservation.class).list();
    }

    private void setReservationStatus(long id, String status) {
        jdbc.sql("UPDATE inventory_reservations SET status = :s, updated_at = now() WHERE id = :id")
                .param("s", status).param("id", id).update();
    }

    private StockAfter addOnHand(long variantId, long warehouseId, int qty) {
        jdbc.sql("""
                INSERT INTO inventory (variant_id, warehouse_id) VALUES (:v, :w) ON CONFLICT DO NOTHING
                """).param("v", variantId).param("w", warehouseId).update();
        return jdbc.sql("""
                UPDATE inventory SET on_hand = on_hand + :qty, updated_at = now()
                WHERE variant_id = :v AND warehouse_id = :w
                RETURNING on_hand, reserved
                """).param("qty", qty).param("v", variantId).param("w", warehouseId)
                .query(StockAfter.class).single();
    }

    private void movement(long variantId, long warehouseId, String type, int dOnHand, int dReserved, StockAfter after,
                          String refType, Object refId, Long actor, String note) {
        jdbc.sql("""
                INSERT INTO inventory_movements (variant_id, warehouse_id, movement_type, delta_on_hand, delta_reserved,
                                                 on_hand_after, reserved_after, ref_type, ref_id, actor_user_id, note)
                VALUES (:v, :w, :t, :doh, :dr, :oh, :r, :rt, :rid, :actor, :note)
                """)
                .param("v", variantId).param("w", warehouseId).param("t", type)
                .param("doh", dOnHand).param("dr", dReserved)
                .param("oh", after.onHand()).param("r", after.reserved())
                .param("rt", refType).param("rid", refId == null ? null : String.valueOf(refId))
                .param("actor", actor).param("note", note)
                .update();
    }
}
