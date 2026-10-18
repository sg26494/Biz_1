package com.store.payment.gateway;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Everything we need from the payment provider, behind one interface so tests can mock it. */
public interface PaymentGateway {

    record GatewayOrder(String id, long amountPaise, String currency, String receipt) {}

    record GatewayRefund(String id, String paymentId, long amountPaise, String status, String receipt) {}

    record GatewayPayment(String id, String orderId, long amountPaise, String status, String method,
                          Instant createdAt) {}

    String keyId();

    GatewayOrder createOrder(long amountPaise, String receipt, Map<String, String> notes);

    GatewayRefund createRefund(String paymentId, long amountPaise, String receipt, Map<String, String> notes);

    List<GatewayRefund> listRefunds(String paymentId);

    /** Payments created in [from, to). Used by daily reconciliation. */
    List<GatewayPayment> listPayments(Instant from, Instant to);

    /** Checkout callback signature: HMAC-SHA256(order_id + "|" + payment_id, key_secret). */
    boolean verifyPaymentSignature(String gatewayOrderId, String gatewayPaymentId, String signature);

    /** Webhook signature: HMAC-SHA256(raw body, webhook_secret). */
    boolean verifyWebhookSignature(byte[] rawBody, String signature);
}
