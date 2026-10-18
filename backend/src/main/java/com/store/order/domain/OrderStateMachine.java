package com.store.order.domain;

import static com.store.order.domain.OrderStatus.CANCELLED;
import static com.store.order.domain.OrderStatus.COMPLETED;
import static com.store.order.domain.OrderStatus.DELIVERED;
import static com.store.order.domain.OrderStatus.PACKED;
import static com.store.order.domain.OrderStatus.PAID;
import static com.store.order.domain.OrderStatus.PAYMENT_FAILED;
import static com.store.order.domain.OrderStatus.PENDING_PAYMENT;
import static com.store.order.domain.OrderStatus.RETURN_IN_PROGRESS;
import static com.store.order.domain.OrderStatus.RTO;
import static com.store.order.domain.OrderStatus.SHIPPED;

import com.store.common.error.ApiException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * The only place order transitions are defined.
 *
 * PENDING_PAYMENT -> PAID -> PACKED -> SHIPPED -> DELIVERED -> COMPLETED
 * PENDING_PAYMENT -> PAYMENT_FAILED / CANCELLED
 * PAYMENT_FAILED  -> PENDING_PAYMENT (retry) / PAID (a later attempt succeeded) / CANCELLED (timeout)
 * PAID or PACKED  -> CANCELLED (refund triggered)
 * DELIVERED       -> RETURN_IN_PROGRESS -> COMPLETED
 * SHIPPED         -> RTO (return to origin)
 * CANCELLED       -> PAID only for a late capture after a payment timeout, and only if stock is still there
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(PENDING_PAYMENT, EnumSet.of(PAID, PAYMENT_FAILED, CANCELLED));
        ALLOWED.put(PAYMENT_FAILED, EnumSet.of(PENDING_PAYMENT, PAID, CANCELLED));
        ALLOWED.put(PAID, EnumSet.of(PACKED, CANCELLED));
        ALLOWED.put(PACKED, EnumSet.of(SHIPPED, CANCELLED));
        ALLOWED.put(SHIPPED, EnumSet.of(DELIVERED, RTO));
        ALLOWED.put(DELIVERED, EnumSet.of(COMPLETED, RETURN_IN_PROGRESS));
        ALLOWED.put(RETURN_IN_PROGRESS, EnumSet.of(COMPLETED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(RTO, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {}

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void check(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ORDER_TRANSITION",
                    "Order cannot move from " + from + " to " + to);
        }
    }

    /** Late capture: a payment that lands after the order was auto-cancelled for non-payment. */
    public static void checkLateCapture(Order order) {
        if (order.getStatus() != CANCELLED || !Order.CANCEL_PAYMENT_TIMEOUT.equals(order.getCancelCode())) {
            check(order.getStatus(), PAID);
        }
    }

    public static boolean isCancellableByUser(OrderStatus s) {
        return s == PENDING_PAYMENT || s == PAYMENT_FAILED || s == PAID;
    }

    public static boolean isCancellableByAdmin(OrderStatus s) {
        return s == PENDING_PAYMENT || s == PAYMENT_FAILED || s == PAID || s == PACKED;
    }
}
