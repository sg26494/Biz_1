package com.store.order;

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.store.common.error.ApiException;
import com.store.order.domain.Order;
import com.store.order.domain.OrderStateMachine;
import org.junit.jupiter.api.Test;

class OrderStateMachineTest {

    @Test
    void happyPath() {
        assertThat(OrderStateMachine.canTransition(PENDING_PAYMENT, PAID)).isTrue();
        assertThat(OrderStateMachine.canTransition(PAID, PACKED)).isTrue();
        assertThat(OrderStateMachine.canTransition(PACKED, SHIPPED)).isTrue();
        assertThat(OrderStateMachine.canTransition(SHIPPED, DELIVERED)).isTrue();
        assertThat(OrderStateMachine.canTransition(DELIVERED, COMPLETED)).isTrue();
        assertThat(OrderStateMachine.canTransition(DELIVERED, RETURN_IN_PROGRESS)).isTrue();
        assertThat(OrderStateMachine.canTransition(RETURN_IN_PROGRESS, COMPLETED)).isTrue();
        assertThat(OrderStateMachine.canTransition(SHIPPED, RTO)).isTrue();
    }

    @Test
    void paymentFailureAndRetry() {
        assertThat(OrderStateMachine.canTransition(PENDING_PAYMENT, PAYMENT_FAILED)).isTrue();
        assertThat(OrderStateMachine.canTransition(PAYMENT_FAILED, PENDING_PAYMENT)).isTrue();
        assertThat(OrderStateMachine.canTransition(PAYMENT_FAILED, PAID)).isTrue();
    }

    @Test
    void illegalMovesAreRejected() {
        assertThat(OrderStateMachine.canTransition(SHIPPED, CANCELLED)).isFalse();
        assertThat(OrderStateMachine.canTransition(PENDING_PAYMENT, SHIPPED)).isFalse();
        assertThat(OrderStateMachine.canTransition(CANCELLED, PAID)).isFalse();
        assertThatThrownBy(() -> OrderStateMachine.check(DELIVERED, PAID)).isInstanceOf(ApiException.class);
    }

    @Test
    void userCanOnlyCancelBeforePacking() {
        assertThat(OrderStateMachine.isCancellableByUser(PAID)).isTrue();
        assertThat(OrderStateMachine.isCancellableByUser(PACKED)).isFalse();
        assertThat(OrderStateMachine.isCancellableByAdmin(PACKED)).isTrue();
        assertThat(OrderStateMachine.isCancellableByAdmin(SHIPPED)).isFalse();
    }

    @Test
    void lateCaptureOnlyAfterPaymentTimeout() {
        Order timedOut = new Order();
        timedOut.setStatus(CANCELLED);
        timedOut.setCancelCode(Order.CANCEL_PAYMENT_TIMEOUT);
        OrderStateMachine.checkLateCapture(timedOut);

        Order userCancelled = new Order();
        userCancelled.setStatus(CANCELLED);
        userCancelled.setCancelCode(Order.CANCEL_USER);
        assertThatThrownBy(() -> OrderStateMachine.checkLateCapture(userCancelled)).isInstanceOf(ApiException.class);
    }
}
