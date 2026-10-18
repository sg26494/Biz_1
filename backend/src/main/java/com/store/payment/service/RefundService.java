package com.store.payment.service;

import com.store.common.error.ApiException;
import com.store.common.outbox.EventTypes;
import com.store.common.outbox.OutboxService;
import com.store.order.domain.Order;
import com.store.payment.domain.Payment;
import com.store.payment.domain.PaymentRepository;
import com.store.payment.domain.Refund;
import com.store.payment.domain.RefundRepository;
import com.store.payment.gateway.PaymentGateway;
import com.store.payment.gateway.PaymentGateway.GatewayRefund;
import com.store.payment.gateway.RazorpayGateway.GatewayException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refunds go back to the original payment method.
 * 1) request(): validates (sum of refunds never exceeds the captured amount), saves PENDING, queues the call.
 * 2) execute(): the worker calls Razorpay; before calling it looks for an existing refund with our receipt,
 *    so a crash between "call" and "save" never refunds twice.
 * 3) refund.processed / refund.failed webhooks finalise the row.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final OutboxService outbox;
    private final Clock clock;

    public RefundService(RefundRepository refunds, PaymentRepository payments, PaymentGateway gateway,
                         OutboxService outbox, Clock clock) {
        this.refunds = refunds;
        this.payments = payments;
        this.gateway = gateway;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Refund request(Order order, Payment capturedPayment, long amountPaise, String reason, Refund.Source source,
                          Long sourceRefId, Long actorUserId) {
        if (amountPaise <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Refund amount must be positive");
        }
        Payment p = payments.findByIdForUpdate(capturedPayment.getId()).orElseThrow();
        if (p.getStatus() != Payment.Status.CAPTURED) {
            throw ApiException.conflict("NOT_CAPTURED", "This payment was not captured");
        }
        long already = refunds.sumNonFailed(p.getId());
        if (already + amountPaise > p.getAmountPaise()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REFUND_EXCEEDS_CAPTURED",
                    "Refunds cannot exceed the amount paid",
                    Map.of("capturedPaise", p.getAmountPaise(), "alreadyRefundedPaise", already));
        }
        Refund r = new Refund();
        r.setPaymentId(p.getId());
        r.setOrderId(order.getId());
        r.setAmountPaise(amountPaise);
        r.setReason(reason);
        r.setSource(source);
        r.setSourceRefId(sourceRefId);
        r.setInitiatedBy(actorUserId);
        r.setReceipt("RF-" + order.getOrderNumber() + "-" + (refunds.countByOrderId(order.getId()) + 1));
        refunds.saveAndFlush(r);
        outbox.enqueue("refund", r.getId(), EventTypes.REFUND_REQUESTED, Map.of("refundId", r.getId()));
        return r;
    }

    /** The order's captured payment (latest), if any. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Payment> capturedPayment(long orderId) {
        List<Payment> captured = payments.findCaptured(orderId);
        return captured.isEmpty() ? Optional.empty() : Optional.of(captured.get(captured.size() - 1));
    }

    /** Outbox handler body. Throws on transient failure so the worker retries. */
    @Transactional
    public void execute(long refundId) {
        Refund r = refunds.findById(refundId).orElseThrow();
        if (r.getGatewayRefundId() != null || r.getStatus() != Refund.Status.PENDING) {
            return;
        }
        Payment p = payments.findById(r.getPaymentId()).orElseThrow();
        Optional<GatewayRefund> existing = gateway.listRefunds(p.getGatewayPaymentId()).stream()
                .filter(g -> r.getReceipt().equals(g.receipt())).findFirst();
        GatewayRefund g;
        if (existing.isPresent()) {
            g = existing.get();
        } else {
            try {
                g = gateway.createRefund(p.getGatewayPaymentId(), r.getAmountPaise(), r.getReceipt(),
                        Map.of("order_id", String.valueOf(r.getOrderId()), "reason", truncate(r.getReason(), 200)));
            } catch (GatewayException e) {
                if (e.status() >= 400 && e.status() < 500 && e.status() != 429) {
                    // Permanent rejection (e.g. amount too high, payment too old): surface to admins.
                    r.setStatus(Refund.Status.FAILED);
                    r.setError(e.code() + ": " + e.getMessage());
                    outbox.enqueue("refund", r.getId(), EventTypes.REFUND_FAILED,
                            Map.of("refundId", r.getId(), "error", r.getError()));
                    log.error("Refund {} rejected by gateway: {}", r.getReceipt(), e.getMessage());
                    return;
                }
                throw e;
            }
        }
        r.setGatewayRefundId(g.id());
        if ("processed".equalsIgnoreCase(g.status())) {
            markProcessed(r);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onGatewayProcessed(String gatewayRefundId, String receipt) {
        findRefund(gatewayRefundId, receipt).ifPresentOrElse(r -> {
            if (r.getGatewayRefundId() == null) {
                r.setGatewayRefundId(gatewayRefundId);
            }
            if (r.getStatus() != Refund.Status.PROCESSED) {
                markProcessed(r);
            }
        }, () -> log.warn("refund.processed for unknown refund {} / {}", gatewayRefundId, receipt));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onGatewayFailed(String gatewayRefundId, String receipt, String error) {
        findRefund(gatewayRefundId, receipt).ifPresent(r -> {
            if (r.getStatus() == Refund.Status.PENDING) {
                r.setStatus(Refund.Status.FAILED);
                r.setError(error);
                outbox.enqueue("refund", r.getId(), EventTypes.REFUND_FAILED,
                        Map.of("refundId", r.getId(), "error", String.valueOf(error)));
            }
        });
    }

    private void markProcessed(Refund r) {
        r.setStatus(Refund.Status.PROCESSED);
        r.setProcessedAt(clock.instant());
        outbox.enqueue("refund", r.getId(), EventTypes.REFUND_PROCESSED, Map.of("refundId", r.getId()));
    }

    private Optional<Refund> findRefund(String gatewayRefundId, String receipt) {
        Optional<Refund> r = gatewayRefundId == null ? Optional.empty() : refunds.findByGatewayRefundId(gatewayRefundId);
        if (r.isEmpty() && receipt != null) {
            r = refunds.findByReceipt(receipt);
        }
        return r;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? String.valueOf(s) : s.substring(0, max);
    }
}
