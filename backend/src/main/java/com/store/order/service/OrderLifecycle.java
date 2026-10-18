package com.store.order.service;

import com.store.order.domain.Order;
import com.store.order.domain.OrderStateMachine;
import com.store.order.domain.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Applies a validated status change, stamps the matching timestamp and writes order_status_history. */
@Component
public class OrderLifecycle {

    public enum ActorType { USER, ADMIN, SYSTEM, WEBHOOK }

    public record HistoryRow(long id, String fromStatus, String toStatus, String reason, String actorType,
                             String actorEmail, OffsetDateTime createdAt) {}

    private final JdbcClient jdbc;
    private final Clock clock;

    public OrderLifecycle(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void transition(Order order, OrderStatus to, String reason, ActorType actorType, Long actorUserId) {
        OrderStateMachine.check(order.getStatus(), to);
        apply(order, to, reason, actorType, actorUserId);
    }

    /** Late capture after a payment-timeout cancellation (the only CANCELLED -> PAID path). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lateCaptureToPaid(Order order, String reason) {
        OrderStateMachine.checkLateCapture(order);
        order.setCancelledAt(null);
        order.setCancelCode(null);
        order.setCancelReason(null);
        apply(order, OrderStatus.PAID, reason, ActorType.WEBHOOK, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void created(Order order) {
        insertHistory(order.getId(), null, order.getStatus(), "Order placed", ActorType.USER, order.getUserId());
    }

    private void apply(Order order, OrderStatus to, String reason, ActorType actorType, Long actorUserId) {
        OrderStatus from = order.getStatus();
        Instant now = clock.instant();
        order.setStatus(to);
        switch (to) {
            case PAID -> order.setPaidAt(now);
            case PACKED -> order.setPackedAt(now);
            case SHIPPED -> order.setShippedAt(now);
            case DELIVERED -> order.setDeliveredAt(now);
            case CANCELLED -> order.setCancelledAt(now);
            default -> { }
        }
        insertHistory(order.getId(), from, to, reason, actorType, actorUserId);
        MDC.put("orderNumber", order.getOrderNumber());
    }

    private void insertHistory(long orderId, OrderStatus from, OrderStatus to, String reason, ActorType actorType,
                               Long actorUserId) {
        jdbc.sql("""
                INSERT INTO order_status_history (order_id, from_status, to_status, reason, actor_type, actor_user_id)
                VALUES (:o, :f, :t, :r, :at, :a)
                """)
                .param("o", orderId).param("f", from == null ? null : from.name()).param("t", to.name())
                .param("r", reason).param("at", actorType.name()).param("a", actorUserId)
                .update();
    }

    public List<HistoryRow> history(long orderId) {
        return jdbc.sql("""
                SELECT h.id, h.from_status, h.to_status, h.reason, h.actor_type, u.email AS actor_email, h.created_at
                FROM order_status_history h LEFT JOIN users u ON u.id = h.actor_user_id
                WHERE h.order_id = :o ORDER BY h.created_at, h.id
                """).param("o", orderId).query(HistoryRow.class).list();
    }
}
