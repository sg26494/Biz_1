package com.store.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_FAILED,
    PAID,
    PACKED,
    SHIPPED,
    DELIVERED,
    RETURN_IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    RTO
}
