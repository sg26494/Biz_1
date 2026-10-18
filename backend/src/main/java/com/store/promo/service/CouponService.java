package com.store.promo.service;

import com.store.common.audit.AuditService;
import com.store.common.error.ApiException;
import com.store.common.util.Money;
import com.store.promo.domain.Coupon;
import com.store.promo.domain.CouponRepository;
import com.store.promo.dto.CouponDtos.CouponRequest;
import com.store.promo.dto.CouponDtos.CouponView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coupon rules: percent or flat, min order value, max discount, validity window, total and per-user limits.
 * A redemption row is written when the order is created and reversed if the order is cancelled before payment.
 */
@Service
public class CouponService {

    public record Evaluation(Long couponId, String code, long discountPaise, String error) {
        public boolean valid() {
            return error == null;
        }
    }

    private final CouponRepository coupons;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Clock clock;

    public CouponService(CouponRepository coupons, JdbcClient jdbc, AuditService audit, Clock clock) {
        this.coupons = coupons;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /** Read-only check used by the cart preview. userId may be null for guests (per-user limit checked later). */
    @Transactional(readOnly = true)
    public Evaluation evaluate(String code, Long userId, long subtotalPaise) {
        return coupons.findByCode(code.trim())
                .map(c -> evaluate(c, userId, subtotalPaise))
                .orElse(new Evaluation(null, code, 0, "This coupon code is not valid"));
    }

    private Evaluation evaluate(Coupon c, Long userId, long subtotal) {
        Instant now = clock.instant();
        if (!c.isActive() || (c.getStartsAt() != null && now.isBefore(c.getStartsAt()))) {
            return invalid(c, "This coupon code is not valid");
        }
        if (c.getEndsAt() != null && now.isAfter(c.getEndsAt())) {
            return invalid(c, "This coupon has expired");
        }
        if (subtotal < c.getMinOrderPaise()) {
            return invalid(c, "Add items worth " + Money.format(c.getMinOrderPaise() - subtotal) + " more to use this coupon");
        }
        if (c.getUsageLimitTotal() != null && usage(c.getId(), null) >= c.getUsageLimitTotal()) {
            return invalid(c, "This coupon has been fully redeemed");
        }
        if (userId != null && c.getUsageLimitPerUser() != null && usage(c.getId(), userId) >= c.getUsageLimitPerUser()) {
            return invalid(c, "You have already used this coupon");
        }
        return new Evaluation(c.getId(), c.getCode(), discountFor(c, subtotal), null);
    }

    static long discountFor(Coupon c, long subtotal) {
        long d = c.getType() == Coupon.Type.PERCENT
                ? Money.percentOf(subtotal, c.getDiscountValue())
                : c.getDiscountValue();
        if (c.getMaxDiscountPaise() != null) {
            d = Math.min(d, c.getMaxDiscountPaise());
        }
        return Math.max(0, Math.min(d, subtotal));
    }

    /** At checkout: lock the coupon, re-check everything, and fail loudly if it no longer applies. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Evaluation lockAndValidate(String code, long userId, long subtotalPaise) {
        Coupon c = coupons.findByCodeForUpdate(code.trim())
                .orElseThrow(() -> couponError("This coupon code is not valid"));
        Evaluation e = evaluate(c, userId, subtotalPaise);
        if (!e.valid()) {
            throw couponError(e.error());
        }
        return e;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void redeem(long couponId, long userId, long orderId, long discountPaise) {
        jdbc.sql("""
                INSERT INTO coupon_redemptions (coupon_id, user_id, order_id, discount_paise)
                VALUES (:c, :u, :o, :d)
                """).param("c", couponId).param("u", userId).param("o", orderId).param("d", discountPaise).update();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reverse(long orderId) {
        jdbc.sql("""
                UPDATE coupon_redemptions SET status = 'REVERSED', reversed_at = now()
                WHERE order_id = :o AND status = 'ACTIVE'
                """).param("o", orderId).update();
    }

    /** Late payment capture on an order that had been cancelled: re-activate its redemption. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reactivate(long orderId) {
        jdbc.sql("""
                UPDATE coupon_redemptions SET status = 'ACTIVE', reversed_at = NULL
                WHERE order_id = :o AND status = 'REVERSED'
                """).param("o", orderId).update();
    }

    private long usage(long couponId, Long userId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM coupon_redemptions
                WHERE coupon_id = :c AND status = 'ACTIVE' AND (CAST(:u AS bigint) IS NULL OR user_id = :u)
                """).param("c", couponId).param("u", userId).query(Long.class).single();
    }

    private static Evaluation invalid(Coupon c, String msg) {
        return new Evaluation(c.getId(), c.getCode(), 0, msg);
    }

    private static ApiException couponError(String msg) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COUPON_INVALID", msg);
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public List<CouponView> list() {
        return jdbc.sql("""
                SELECT c.id, c.code, c.description, c.discount_type AS type, c.discount_value, c.min_order_paise,
                       c.max_discount_paise, c.starts_at, c.ends_at, c.usage_limit_total, c.usage_limit_per_user,
                       c.is_active AS active,
                       COUNT(r.id) FILTER (WHERE r.status = 'ACTIVE') AS times_used,
                       COALESCE(SUM(r.discount_paise) FILTER (WHERE r.status = 'ACTIVE'), 0) AS total_discount_paise
                FROM coupons c LEFT JOIN coupon_redemptions r ON r.coupon_id = c.id
                GROUP BY c.id ORDER BY c.created_at DESC
                """).query(CouponView.class).list();
    }

    @Transactional
    public CouponView create(CouponRequest req, long actor) {
        if (coupons.findByCode(req.code()).isPresent()) {
            throw ApiException.conflict("COUPON_EXISTS", "A coupon with this code already exists");
        }
        Coupon c = new Coupon();
        apply(c, req);
        coupons.saveAndFlush(c);
        audit.record(actor, "COUPON_CREATED", "coupon", c.getId(), null, req, null);
        return get(c.getId());
    }

    @Transactional
    public CouponView update(long id, CouponRequest req, long actor) {
        Coupon c = coupons.findById(id).orElseThrow(() -> ApiException.notFound("Coupon"));
        Optional<Coupon> clash = coupons.findByCode(req.code());
        if (clash.isPresent() && !clash.get().getId().equals(id)) {
            throw ApiException.conflict("COUPON_EXISTS", "A coupon with this code already exists");
        }
        CouponView before = get(id);
        apply(c, req);
        coupons.flush();
        audit.record(actor, "COUPON_UPDATED", "coupon", id, before, req, null);
        return get(id);
    }

    @Transactional
    public CouponView setActive(long id, boolean active, long actor) {
        Coupon c = coupons.findById(id).orElseThrow(() -> ApiException.notFound("Coupon"));
        boolean before = c.isActive();
        c.setActive(active);
        coupons.flush();
        audit.record(actor, active ? "COUPON_ENABLED" : "COUPON_DISABLED", "coupon", id,
                java.util.Map.of("active", before), java.util.Map.of("active", active), null);
        return get(id);
    }

    @Transactional(readOnly = true)
    public CouponView get(long id) {
        return list().stream().filter(v -> v.id() == id).findFirst().orElseThrow(() -> ApiException.notFound("Coupon"));
    }

    private void apply(Coupon c, CouponRequest req) {
        if (req.type() == Coupon.Type.PERCENT && req.discountValue() > 10_000) {
            throw ApiException.badRequest("INVALID_COUPON", "Percent discount is in basis points (max 10000 = 100%)");
        }
        if (req.startsAt() != null && req.endsAt() != null && !req.endsAt().isAfter(req.startsAt())) {
            throw ApiException.badRequest("INVALID_COUPON", "End must be after start");
        }
        c.setCode(req.code().trim().toUpperCase());
        c.setDescription(req.description());
        c.setType(req.type());
        c.setDiscountValue(req.discountValue());
        c.setMinOrderPaise(req.minOrderPaise() == null ? 0 : req.minOrderPaise());
        c.setMaxDiscountPaise(req.maxDiscountPaise());
        c.setStartsAt(req.startsAt());
        c.setEndsAt(req.endsAt());
        c.setUsageLimitTotal(req.usageLimitTotal());
        c.setUsageLimitPerUser(req.usageLimitPerUser());
        c.setActive(req.active() == null || req.active());
    }
}
