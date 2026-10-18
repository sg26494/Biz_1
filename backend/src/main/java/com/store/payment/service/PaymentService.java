package com.store.payment.service;

import com.store.common.error.ApiException;
import com.store.common.outbox.EventTypes;
import com.store.common.outbox.OutboxService;
import com.store.inventory.service.InventoryService;
import com.store.order.domain.Order;
import com.store.order.domain.OrderItem;
import com.store.order.domain.OrderItemRepository;
import com.store.order.domain.OrderRepository;
import com.store.order.domain.OrderStatus;
import com.store.order.service.OrderLifecycle;
import com.store.order.service.OrderLifecycle.ActorType;
import com.store.payment.domain.Payment;
import com.store.payment.domain.PaymentRepository;
import com.store.payment.domain.Refund;
import com.store.payment.dto.PaymentDtos.CheckoutPayment;
import com.store.payment.dto.PaymentDtos.VerifyRequest;
import com.store.payment.dto.PaymentDtos.VerifyResponse;
import com.store.payment.gateway.PaymentGateway;
import com.store.promo.service.CouponService;
import com.store.user.domain.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Razorpay payments. The client callback (/payments/verify) only checks the signature and shows
 * "processing"; the webhook is the source of truth and is the only thing that marks an order PAID.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final PaymentGateway gateway;
    private final OrderLifecycle lifecycle;
    private final InventoryService inventory;
    private final CouponService coupons;
    private final RefundService refunds;
    private final OutboxService outbox;
    private final UserRepository users;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, OrderRepository orders, OrderItemRepository orderItems,
                          PaymentGateway gateway, OrderLifecycle lifecycle, InventoryService inventory,
                          CouponService coupons, RefundService refunds, OutboxService outbox, UserRepository users,
                          Clock clock) {
        this.payments = payments;
        this.orders = orders;
        this.orderItems = orderItems;
        this.gateway = gateway;
        this.lifecycle = lifecycle;
        this.inventory = inventory;
        this.coupons = coupons;
        this.refunds = refunds;
        this.outbox = outbox;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Returns the Razorpay order to open Checkout with, creating it if needed. Razorpay accepts several
     * attempts on one order, so retries reuse the existing one. The order row is locked so two tabs
     * cannot create two gateway orders.
     */
    @Transactional
    public CheckoutPayment ensureGatewayOrder(long orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            throw ApiException.conflict("ORDER_NOT_PAYABLE", "This order cannot be paid (status " + order.getStatus() + ")");
        }
        if (order.getPaymentExpiresAt() != null && order.getPaymentExpiresAt().isBefore(clock.instant())) {
            throw ApiException.conflict("PAYMENT_WINDOW_EXPIRED", "This order expired, please place it again");
        }
        Payment p = payments.findByOrderIdOrderByIdDesc(orderId).stream()
                .filter(x -> x.getStatus() != Payment.Status.CAPTURED && x.getAmountPaise() == order.getTotalPaise())
                .findFirst()
                .orElseGet(() -> {
                    PaymentGateway.GatewayOrder go = gateway.createOrder(order.getTotalPaise(), order.getOrderNumber(),
                            Map.of("order_number", order.getOrderNumber(), "order_id", String.valueOf(order.getId())));
                    Payment np = new Payment();
                    np.setOrderId(orderId);
                    np.setGatewayOrderId(go.id());
                    np.setAmountPaise(order.getTotalPaise());
                    return payments.saveAndFlush(np);
                });
        var user = users.findById(order.getUserId()).orElseThrow();
        return new CheckoutPayment(order.getId(), order.getOrderNumber(), order.getStatus().name(),
                p.getGatewayOrderId(), gateway.keyId(), p.getAmountPaise(), p.getCurrency(), user.getName(),
                order.getEmail(), order.getPhone(), order.getPaymentExpiresAt());
    }

    /** Client success callback: verify the signature, record it, report "processing". Never marks PAID. */
    @Transactional
    public VerifyResponse verifyClient(long userId, VerifyRequest req) {
        Payment p = payments.findByGatewayOrderId(req.razorpayOrderId())
                .orElseThrow(() -> ApiException.notFound("Payment"));
        Order order = orders.findById(p.getOrderId()).orElseThrow();
        if (order.getUserId() != userId) {
            throw ApiException.notFound("Payment");
        }
        if (!gateway.verifyPaymentSignature(req.razorpayOrderId(), req.razorpayPaymentId(), req.razorpaySignature())) {
            throw ApiException.badRequest("SIGNATURE_INVALID", "Payment could not be verified");
        }
        if (p.getClientVerifiedAt() == null) {
            p.setClientVerifiedAt(clock.instant());
        }
        return new VerifyResponse(order.getId(), order.getOrderNumber(), order.getStatus().name(),
                order.getStatus() == OrderStatus.PAID ? "PAID" : "PROCESSING");
    }

    // ---------- webhook-driven ----------

    /** payment.captured / order.paid. Idempotent. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onCaptured(String gatewayOrderId, String gatewayPaymentId, long amountPaise, String method) {
        Optional<Payment> maybe = payments.findByGatewayOrderId(gatewayOrderId);
        if (maybe.isEmpty()) {
            log.warn("Captured payment {} for unknown gateway order {}", gatewayPaymentId, gatewayOrderId);
            return;
        }
        Payment p = maybe.get();
        Order order = orders.findByIdForUpdate(p.getOrderId()).orElseThrow();
        if (p.getStatus() == Payment.Status.CAPTURED) {
            return; // duplicate event (payment.captured and order.paid both arrive)
        }
        if (amountPaise != p.getAmountPaise()) {
            log.error("Amount mismatch on {}: expected {} got {}", gatewayOrderId, p.getAmountPaise(), amountPaise);
        }
        p.setStatus(Payment.Status.CAPTURED);
        p.setGatewayPaymentId(gatewayPaymentId);
        p.setMethod(method);
        p.setCapturedAt(clock.instant());
        p.setAmountPaise(amountPaise);
        p.setErrorCode(null);
        p.setErrorDescription(null);
        payments.flush();

        switch (order.getStatus()) {
            case PENDING_PAYMENT, PAYMENT_FAILED -> {
                int committed = inventory.commit(order.getId());
                if (committed == 0 && !inventory.tryDirectSale(order.getId(), order.getWarehouseId(), lines(order))) {
                    // Should not happen (expiry cancels the order in the same transaction as the release).
                    refundWholeOrder(order, p, "Stock unavailable at payment time", Refund.Source.LATE_CAPTURE);
                    lifecycle.transition(order, OrderStatus.CANCELLED, "Stock unavailable at payment", ActorType.WEBHOOK, null);
                    order.setCancelCode(Order.CANCEL_PAYMENT_TIMEOUT);
                    return;
                }
                lifecycle.transition(order, OrderStatus.PAID, "Payment captured", ActorType.WEBHOOK, null);
                paid(order);
            }
            case CANCELLED -> {
                if (Order.CANCEL_PAYMENT_TIMEOUT.equals(order.getCancelCode())
                        && inventory.tryDirectSale(order.getId(), order.getWarehouseId(), lines(order))) {
                    coupons.reactivate(order.getId());
                    lifecycle.lateCaptureToPaid(order, "Payment captured after timeout; stock still available");
                    paid(order);
                } else {
                    refundWholeOrder(order, p, "Payment received after order was cancelled", Refund.Source.LATE_CAPTURE);
                    adminAlert("Late payment auto-refunded", order, gatewayPaymentId);
                }
            }
            default -> {
                // Order already paid through another gateway order: this is a duplicate payment.
                refundWholeOrder(order, p, "Duplicate payment", Refund.Source.LATE_CAPTURE);
                adminAlert("Duplicate payment auto-refunded", order, gatewayPaymentId);
            }
        }
    }

    /** payment.failed. The shopper may still succeed on another attempt within the same Razorpay order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onFailed(String gatewayOrderId, String gatewayPaymentId, String errorCode, String errorDescription) {
        Optional<Payment> maybe = payments.findByGatewayOrderId(gatewayOrderId);
        if (maybe.isEmpty()) {
            return;
        }
        Payment p = maybe.get();
        Order order = orders.findByIdForUpdate(p.getOrderId()).orElseThrow();
        if (p.getStatus() == Payment.Status.CAPTURED) {
            return;
        }
        p.setStatus(Payment.Status.FAILED);
        p.setErrorCode(errorCode);
        p.setErrorDescription(errorDescription);
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            lifecycle.transition(order, OrderStatus.PAYMENT_FAILED,
                    "Payment attempt failed: " + (errorDescription == null ? errorCode : errorDescription),
                    ActorType.WEBHOOK, null);
        }
    }

    private void paid(Order order) {
        outbox.enqueue("order", order.getId(), EventTypes.ORDER_PAID,
                Map.of("orderId", order.getId(), "orderNumber", order.getOrderNumber()));
    }

    private void refundWholeOrder(Order order, Payment p, String reason, Refund.Source source) {
        refunds.request(order, p, p.getAmountPaise(), reason, source, null, null);
    }

    private void adminAlert(String title, Order order, String paymentId) {
        outbox.enqueue("order", order.getId(), EventTypes.ADMIN_ALERT, Map.of(
                "title", title,
                "detail", "Order " + order.getOrderNumber() + ", payment " + paymentId));
    }

    private List<InventoryService.Line> lines(Order order) {
        List<OrderItem> items = orderItems.findByOrderIdOrderByIdAsc(order.getId());
        return items.stream().map(i -> new InventoryService.Line(i.getVariantId(), i.getQty())).toList();
    }
}
