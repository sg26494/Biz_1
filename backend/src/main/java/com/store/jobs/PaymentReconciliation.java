package com.store.jobs;

import com.store.common.outbox.EventTypes;
import com.store.common.outbox.OutboxService;
import com.store.payment.gateway.PaymentGateway;
import com.store.payment.gateway.PaymentGateway.GatewayPayment;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily: list yesterday's Razorpay payments and compare with our payments/orders.
 *  - CAPTURED_NOT_RECORDED: captured at the gateway, but our payment row is not CAPTURED (missed webhook?)
 *  - CAPTURED_ORDER_NOT_PAID: captured, but the order never reached PAID (and no refund issued)
 *  - UNKNOWN_GATEWAY_ORDER: a payment for a Razorpay order we never created
 *  - RECORDED_NOT_AT_GATEWAY: we think it is captured, gateway disagrees
 * Results land in payment_mismatches (admin dashboard) and an admin email.
 */
@Component
public class PaymentReconciliation {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliation.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private record Ours(long paymentId, long orderId, String gatewayOrderId, String gatewayPaymentId, String status,
                        String orderStatus, long refunded) {}

    private final PaymentGateway gateway;
    private final JdbcClient jdbc;
    private final OutboxService outbox;
    private final Clock clock;

    public PaymentReconciliation(PaymentGateway gateway, JdbcClient jdbc, OutboxService outbox, Clock clock) {
        this.gateway = gateway;
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.clock = clock;
    }

    public void runForYesterday() {
        run(LocalDate.now(clock.withZone(IST)).minusDays(1));
    }

    @Transactional
    public int run(LocalDate day) {
        var from = day.atStartOfDay(IST).toInstant();
        var to = day.plusDays(1).atStartOfDay(IST).toInstant();
        List<GatewayPayment> remote = gateway.listPayments(from, to);
        int found = 0;
        Set<String> capturedRemote = new HashSet<>();
        for (GatewayPayment gp : remote) {
            if (!"captured".equals(gp.status()) && !"refunded".equals(gp.status())) {
                continue;
            }
            capturedRemote.add(gp.id());
            Ours ours = jdbc.sql("""
                    SELECT p.id AS payment_id, p.order_id, p.gateway_order_id, p.gateway_payment_id, p.status,
                           o.status AS order_status,
                           COALESCE((SELECT SUM(r.amount_paise) FROM refunds r
                                      WHERE r.payment_id = p.id AND r.status <> 'FAILED'), 0) AS refunded
                    FROM payments p JOIN orders o ON o.id = p.order_id WHERE p.gateway_order_id = :g
                    """).param("g", gp.orderId()).query(Ours.class).optional().orElse(null);
            if (ours == null) {
                found += flag(day, gp.id(), gp.orderId(), null, "UNKNOWN_GATEWAY_ORDER",
                        "Captured " + gp.amountPaise() + " paise for a gateway order not in our DB");
            } else if (!"CAPTURED".equals(ours.status())) {
                found += flag(day, gp.id(), gp.orderId(), ours.orderId(), "CAPTURED_NOT_RECORDED",
                        "Gateway captured, our payment is " + ours.status());
            } else if (ours.refunded() == 0 && (ours.orderStatus().equals("PENDING_PAYMENT")
                    || ours.orderStatus().equals("PAYMENT_FAILED") || ours.orderStatus().equals("CANCELLED"))) {
                found += flag(day, gp.id(), gp.orderId(), ours.orderId(), "CAPTURED_ORDER_NOT_PAID",
                        "Order is " + ours.orderStatus() + " with no refund");
            }
        }
        List<Ours> ourCaptured = jdbc.sql("""
                SELECT p.id AS payment_id, p.order_id, p.gateway_order_id, p.gateway_payment_id, p.status,
                       o.status AS order_status, 0 AS refunded
                FROM payments p JOIN orders o ON o.id = p.order_id
                WHERE p.status = 'CAPTURED' AND p.captured_at >= :f AND p.captured_at < :t
                """).param("f", from.atOffset(java.time.ZoneOffset.UTC)).param("t", to.atOffset(java.time.ZoneOffset.UTC))
                .query(Ours.class).list();
        for (Ours o : ourCaptured) {
            if (o.gatewayPaymentId() != null && !capturedRemote.contains(o.gatewayPaymentId())) {
                found += flag(day, o.gatewayPaymentId(), o.gatewayOrderId(), o.orderId(), "RECORDED_NOT_AT_GATEWAY",
                        "Our payment is CAPTURED but the gateway list for the day does not show it");
            }
        }
        if (found > 0) {
            outbox.enqueue("payments", day.toString(), EventTypes.ADMIN_ALERT, Map.of(
                    "title", "Payment mismatches for " + day + ": " + found,
                    "detail", "Open the admin dashboard → Payments → Mismatches."));
        }
        log.info("Reconciliation {}: {} gateway payments, {} mismatches", day, remote.size(), found);
        return found;
    }

    private int flag(LocalDate day, String paymentId, String orderId, Long ourOrderId, String kind, String detail) {
        return jdbc.sql("""
                INSERT INTO payment_mismatches (run_date, gateway_payment_id, gateway_order_id, order_id, kind, detail)
                VALUES (:d, :p, :g, :o, :k, :detail) ON CONFLICT DO NOTHING
                """).param("d", day).param("p", paymentId).param("g", orderId).param("o", ourOrderId)
                .param("k", kind).param("detail", detail).update();
    }
}
