package com.store.order.service;

import com.store.cart.service.CartService;
import com.store.common.audit.AuditService;
import com.store.common.error.ApiException;
import com.store.common.outbox.EventTypes;
import com.store.common.outbox.OutboxService;
import com.store.common.web.PageResponse;
import com.store.inventory.service.InventoryService;
import com.store.order.domain.Order;
import com.store.order.domain.OrderItem;
import com.store.order.domain.OrderItemRepository;
import com.store.order.domain.OrderRepository;
import com.store.order.domain.OrderStateMachine;
import com.store.order.domain.OrderStatus;
import com.store.order.dto.OrderDtos.AdminOrderRow;
import com.store.order.dto.OrderDtos.OrderDetail;
import com.store.order.dto.OrderDtos.OrderSummary;
import com.store.order.dto.OrderDtos.PaymentInfo;
import com.store.order.service.OrderLifecycle.ActorType;
import com.store.payment.domain.Payment;
import com.store.payment.domain.PaymentRepository;
import com.store.payment.dto.PaymentDtos.RefundRow;
import com.store.payment.service.RefundService;
import com.store.pricing.PricingService.CartLine;
import com.store.promo.service.CouponService;
import com.store.user.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentRepository payments;
    private final OrderLifecycle lifecycle;
    private final InventoryService inventory;
    private final CouponService coupons;
    private final CartService carts;
    private final RefundService refunds;
    private final OutboxService outbox;
    private final AuditService audit;
    private final UserRepository users;
    private final JdbcClient jdbc;
    private final Clock clock;

    public OrderService(OrderRepository orders, OrderItemRepository items, PaymentRepository payments,
                        OrderLifecycle lifecycle, InventoryService inventory, CouponService coupons, CartService carts,
                        RefundService refunds, OutboxService outbox, AuditService audit, UserRepository users,
                        JdbcClient jdbc, Clock clock) {
        this.orders = orders;
        this.items = items;
        this.payments = payments;
        this.lifecycle = lifecycle;
        this.inventory = inventory;
        this.coupons = coupons;
        this.carts = carts;
        this.refunds = refunds;
        this.outbox = outbox;
        this.audit = audit;
        this.users = users;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ---------- customer ----------

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> listMine(long userId, int page, int size) {
        Page<Order> p = orders.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        Map<Long, List<OrderItem>> byOrder = new HashMap<>();
        items.findByOrderIdIn(p.getContent().stream().map(Order::getId).toList())
                .forEach(i -> byOrder.computeIfAbsent(i.getOrderId(), k -> new java.util.ArrayList<>()).add(i));
        return PageResponse.from(p, o -> {
            List<OrderItem> its = byOrder.getOrDefault(o.getId(), List.of());
            return new OrderSummary(o.getId(), o.getOrderNumber(), o.getStatus().name(), o.getTotalPaise(),
                    its.stream().mapToInt(OrderItem::getQty).sum(),
                    its.isEmpty() ? null : its.get(0).getProductTitle(),
                    its.isEmpty() ? null : its.get(0).getImageUrl(), o.getCreatedAt());
        });
    }

    /** Users can only read their own orders (checked against order.user_id). */
    @Transactional(readOnly = true)
    public OrderDetail getMine(long userId, long orderId) {
        Order o = orders.findById(orderId).filter(x -> x.getUserId() == userId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        return detail(o);
    }

    @Transactional(readOnly = true)
    public Order requireOwned(long userId, long orderId) {
        return orders.findById(orderId).filter(x -> x.getUserId() == userId)
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    @Transactional
    public OrderDetail cancelByUser(long userId, long orderId, String reason) {
        Order o = orders.findByIdForUpdate(orderId).filter(x -> x.getUserId() == userId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (!OrderStateMachine.isCancellableByUser(o.getStatus())) {
            throw ApiException.conflict("CANNOT_CANCEL",
                    "This order is already being packed. You can request a return after delivery.");
        }
        cancel(o, Order.CANCEL_USER, reason, ActorType.USER, userId);
        return detail(o);
    }

    @Transactional
    public void moveToPendingForRetry(long userId, long orderId) {
        Order o = orders.findByIdForUpdate(orderId).filter(x -> x.getUserId() == userId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (o.getStatus() == OrderStatus.PAYMENT_FAILED) {
            if (o.getPaymentExpiresAt().isBefore(clock.instant())) {
                throw ApiException.conflict("PAYMENT_WINDOW_EXPIRED", "This order expired, please place it again");
            }
            lifecycle.transition(o, OrderStatus.PENDING_PAYMENT, "Payment retried", ActorType.USER, userId);
        }
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderRow> adminList(String status, String q, LocalDate from, LocalDate to, int page,
                                                 int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("st", status == null ? "" : status);
        params.put("q", q == null ? "" : q.trim());
        params.put("from", from == null ? LocalDate.of(2000, 1, 1).atStartOfDay(IST).toOffsetDateTime()
                : from.atStartOfDay(IST).toOffsetDateTime());
        params.put("to", to == null ? LocalDate.of(2999, 1, 1).atStartOfDay(IST).toOffsetDateTime()
                : to.plusDays(1).atStartOfDay(IST).toOffsetDateTime());
        String where = """
                WHERE (:st = '' OR o.status = :st)
                  AND o.created_at >= :from AND o.created_at < :to
                  AND (:q = '' OR o.order_number ILIKE '%' || :q || '%' OR o.phone LIKE '%' || :q || '%'
                       OR o.email ILIKE '%' || :q || '%')
                """;
        long total = jdbc.sql("SELECT COUNT(*) FROM orders o " + where).params(params).query(Long.class).single();
        params.put("limit", size);
        params.put("offset", page * size);
        List<AdminOrderRow> rows = jdbc.sql("""
                SELECT o.id, o.order_number, o.status, o.total_paise, u.name AS customer_name, o.email, o.phone,
                       o.shipping_address ->> 'city' AS city, o.shipping_address ->> 'state' AS state,
                       (SELECT COALESCE(SUM(qty), 0) FROM order_items i WHERE i.order_id = o.id)::int AS item_count,
                       (SELECT p.status FROM payments p WHERE p.order_id = o.id ORDER BY p.id DESC LIMIT 1)
                         AS payment_status,
                       o.created_at, o.paid_at
                FROM orders o JOIN users u ON u.id = o.user_id
                """ + where + " ORDER BY o.created_at DESC LIMIT :limit OFFSET :offset")
                .params(params).query(AdminOrderRow.class).list();
        return PageResponse.of(rows, page, size, total);
    }

    @Transactional(readOnly = true)
    public OrderDetail adminGet(long orderId) {
        return detail(orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order")));
    }

    /** Admin cancel (PAID or PACKED triggers a full refund). Reason is mandatory and audited. */
    @Transactional
    public OrderDetail cancelByAdmin(long orderId, String reason, long adminId) {
        Order o = orders.findByIdForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (!OrderStateMachine.isCancellableByAdmin(o.getStatus())) {
            throw ApiException.conflict("CANNOT_CANCEL", "Orders that have shipped cannot be cancelled; use RTO/returns");
        }
        String before = o.getStatus().name();
        cancel(o, Order.CANCEL_ADMIN, reason, ActorType.ADMIN, adminId);
        audit.record(adminId, "ORDER_CANCELLED", "order", o.getId(), Map.of("status", before),
                Map.of("status", o.getStatus().name()), reason);
        return detail(o);
    }

    // ---------- system ----------

    /** Reservation expired without payment: release stock and cancel. Called by the expiry job. */
    @Transactional
    public boolean expireIfUnpaid(long orderId) {
        Order o = orders.findByIdForUpdate(orderId).orElse(null);
        if (o == null || (o.getStatus() != OrderStatus.PENDING_PAYMENT && o.getStatus() != OrderStatus.PAYMENT_FAILED)
                || o.getPaymentExpiresAt() == null || o.getPaymentExpiresAt().isAfter(clock.instant())) {
            return false;
        }
        boolean hadFailedAttempt = o.getStatus() == OrderStatus.PAYMENT_FAILED;
        inventory.release(o.getId(), true);
        coupons.reverse(o.getId());
        carts.restore(o.getUserId(), lines(o.getId()));
        o.setCancelCode(Order.CANCEL_PAYMENT_TIMEOUT);
        o.setCancelReason("Payment not completed in time");
        lifecycle.transition(o, OrderStatus.CANCELLED, "Payment not completed in time", ActorType.SYSTEM, null);
        if (hadFailedAttempt) {
            outbox.enqueue("order", o.getId(), EventTypes.PAYMENT_FAILED,
                    Map.of("orderId", o.getId(), "orderNumber", o.getOrderNumber()));
        }
        return true;
    }

    // ---------- internals ----------

    private void cancel(Order o, String code, String reason, ActorType actorType, Long actor) {
        OrderStatus from = o.getStatus();
        o.setCancelCode(code);
        o.setCancelReason(reason);
        if (from == OrderStatus.PENDING_PAYMENT || from == OrderStatus.PAYMENT_FAILED) {
            inventory.release(o.getId(), false);
            coupons.reverse(o.getId());
            if (actorType == ActorType.USER) {
                carts.restore(o.getUserId(), lines(o.getId()));
            }
            lifecycle.transition(o, OrderStatus.CANCELLED, reason, actorType, actor);
        } else {
            // PAID / PACKED: stock was already sold; put it back and refund the full amount.
            inventory.restockCancelled(o.getId(), o.getWarehouseId(),
                    items.findByOrderIdOrderByIdAsc(o.getId()).stream()
                            .map(i -> new InventoryService.Line(i.getVariantId(), i.getQty())).toList(),
                    actor);
            lifecycle.transition(o, OrderStatus.CANCELLED, reason, actorType, actor);
            Payment captured = refunds.capturedPayment(o.getId())
                    .orElseThrow(() -> ApiException.conflict("NO_PAYMENT", "No captured payment found to refund"));
            refunds.request(o, captured, captured.getAmountPaise(), "Order cancelled: " + reason,
                    com.store.payment.domain.Refund.Source.ORDER_CANCEL, null, actor);
        }
        outbox.enqueue("order", o.getId(), EventTypes.ORDER_CANCELLED, Map.of("orderId", o.getId(),
                "orderNumber", o.getOrderNumber(), "refunded", from == OrderStatus.PAID || from == OrderStatus.PACKED));
    }

    private List<CartLine> lines(long orderId) {
        return items.findByOrderIdOrderByIdAsc(orderId).stream()
                .map(i -> new CartLine(i.getVariantId(), i.getQty(), null)).toList();
    }

    private OrderDetail detail(Order o) {
        List<OrderItem> its = items.findByOrderIdOrderByIdAsc(o.getId());
        Optional<Payment> p = payments.findByOrderIdOrderByIdDesc(o.getId()).stream().findFirst();
        PaymentInfo pi = p.map(x -> new PaymentInfo(x.getStatus().name(), x.getMethod(), x.getGatewayPaymentId(),
                x.getCapturedAt())).orElse(null);
        List<RefundRow> refundRows = jdbc.sql("""
                SELECT r.id, r.order_id, o.order_number, r.amount_paise, r.status, r.gateway_refund_id, r.receipt,
                       r.reason, r.source, r.error, u.email AS initiated_by_email, r.created_at, r.processed_at
                FROM refunds r JOIN orders o ON o.id = r.order_id LEFT JOIN users u ON u.id = r.initiated_by
                WHERE r.order_id = :o ORDER BY r.id
                """).param("o", o.getId()).query(RefundRow.class).list();
        boolean canRetry = (o.getStatus() == OrderStatus.PENDING_PAYMENT || o.getStatus() == OrderStatus.PAYMENT_FAILED)
                && o.getPaymentExpiresAt() != null && o.getPaymentExpiresAt().isAfter(clock.instant());
        String name = users.findById(o.getUserId()).map(u -> u.getName()).orElse(null);
        return OrderDetail.of(o, its, pi, lifecycle.history(o.getId()), refundRows,
                OrderStateMachine.isCancellableByUser(o.getStatus()), canRetry, name);
    }
}
