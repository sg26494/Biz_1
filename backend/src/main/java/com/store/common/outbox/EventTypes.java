package com.store.common.outbox;

public final class EventTypes {

    public static final String USER_SIGNED_UP = "UserSignedUp";
    public static final String ORDER_PAID = "OrderPaid";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String REFUND_REQUESTED = "RefundRequested";
    public static final String REFUND_PROCESSED = "RefundProcessed";
    public static final String REFUND_FAILED = "RefundFailed";
    public static final String CATALOG_CHANGED = "CatalogChanged";
    public static final String ADMIN_ALERT = "AdminAlert";

    private EventTypes() {}
}
