package com.store.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.store.auth.security.CurrentUser;
import com.store.common.audit.AuditService;
import com.store.common.error.ApiException;
import com.store.common.idempotency.IdempotencyService;
import com.store.common.web.PageResponse;
import com.store.order.domain.Order;
import com.store.order.domain.OrderRepository;
import com.store.payment.domain.Payment;
import com.store.payment.domain.Refund;
import com.store.payment.dto.PaymentDtos.ManualRefundRequest;
import com.store.payment.dto.PaymentDtos.MismatchRow;
import com.store.payment.dto.PaymentDtos.PaymentRow;
import com.store.payment.dto.PaymentDtos.RefundRow;
import com.store.payment.service.RefundService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final JdbcClient jdbc;
    private final RefundService refunds;
    private final OrderRepository orders;
    private final IdempotencyService idempotency;
    private final AuditService audit;

    public AdminPaymentController(JdbcClient jdbc, RefundService refunds, OrderRepository orders,
                                  IdempotencyService idempotency, AuditService audit) {
        this.jdbc = jdbc;
        this.refunds = refunds;
        this.orders = orders;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @GetMapping("/payments")
    @Transactional(readOnly = true)
    public List<PaymentRow> payments(@RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        int s = PageResponse.clampSize(size, 200);
        return jdbc.sql("""
                SELECT p.id, p.order_id, o.order_number, p.gateway_order_id, p.gateway_payment_id, p.amount_paise,
                       p.status, p.method, p.error_code, p.captured_at, p.created_at
                FROM payments p JOIN orders o ON o.id = p.order_id
                WHERE (:st = '' OR p.status = :st)
                ORDER BY p.id DESC LIMIT :limit OFFSET :offset
                """).param("st", status == null ? "" : status).param("limit", s).param("offset", Math.max(0, page) * s)
                .query(PaymentRow.class).list();
    }

    @GetMapping("/refunds")
    @Transactional(readOnly = true)
    public List<RefundRow> refunds(@RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        int s = PageResponse.clampSize(size, 200);
        return jdbc.sql("""
                SELECT r.id, r.order_id, o.order_number, r.amount_paise, r.status, r.gateway_refund_id, r.receipt,
                       r.reason, r.source, r.error, u.email AS initiated_by_email, r.created_at, r.processed_at
                FROM refunds r JOIN orders o ON o.id = r.order_id LEFT JOIN users u ON u.id = r.initiated_by
                WHERE (:st = '' OR r.status = :st)
                ORDER BY r.id DESC LIMIT :limit OFFSET :offset
                """).param("st", status == null ? "" : status).param("limit", s).param("offset", Math.max(0, page) * s)
                .query(RefundRow.class).list();
    }

    /** Manual (partial) refund. Confirm + reason in the UI; Idempotency-Key required. */
    @PostMapping("/refunds")
    public JsonNode refund(@Valid @RequestBody ManualRefundRequest req,
                           @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        long admin = CurrentUser.id();
        return idempotency.execute("admin-refund", admin, key, req, () -> {
            Order o = orders.findByIdForUpdate(req.orderId()).orElseThrow(() -> ApiException.notFound("Order"));
            Payment p = refunds.capturedPayment(o.getId())
                    .orElseThrow(() -> ApiException.conflict("NO_PAYMENT", "This order has no captured payment"));
            Refund r = refunds.request(o, p, req.amountPaise(), req.reason(), Refund.Source.MANUAL, null, admin);
            audit.record(admin, "REFUND_CREATED", "order", o.getId(), null,
                    Map.of("refundId", r.getId(), "amountPaise", r.getAmountPaise()), req.reason());
            return Map.of("refundId", r.getId(), "receipt", r.getReceipt(), "status", r.getStatus().name());
        });
    }

    @GetMapping("/payments/mismatches")
    @Transactional(readOnly = true)
    public List<MismatchRow> mismatches(@RequestParam(defaultValue = "false") boolean includeResolved) {
        return jdbc.sql("""
                SELECT m.id, m.run_date::text AS run_date, m.gateway_payment_id, m.gateway_order_id, m.order_id,
                       o.order_number, m.kind, m.detail, m.resolved_at, m.created_at
                FROM payment_mismatches m LEFT JOIN orders o ON o.id = m.order_id
                WHERE (:all OR m.resolved_at IS NULL)
                ORDER BY m.id DESC LIMIT 200
                """).param("all", includeResolved).query(MismatchRow.class).list();
    }

    @PostMapping("/payments/mismatches/{id}/resolve")
    @Transactional
    public Map<String, Object> resolve(@PathVariable long id, @RequestParam String note) {
        long admin = CurrentUser.id();
        int n = jdbc.sql("UPDATE payment_mismatches SET resolved_at = now(), resolved_by = :a WHERE id = :id AND resolved_at IS NULL")
                .param("a", admin).param("id", id).update();
        if (n == 0) {
            throw ApiException.notFound("Open mismatch");
        }
        audit.record(admin, "PAYMENT_MISMATCH_RESOLVED", "payment_mismatch", id, null, null, note);
        return Map.of("resolved", true);
    }
}
