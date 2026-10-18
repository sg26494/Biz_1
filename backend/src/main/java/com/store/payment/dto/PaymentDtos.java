package com.store.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;

public final class PaymentDtos {

    private PaymentDtos() {}

    /** Everything the frontend needs to open Razorpay Standard Checkout. */
    public record CheckoutPayment(long orderId, String orderNumber, String status, String razorpayOrderId,
                                  String razorpayKeyId, long amountPaise, String currency, String customerName,
                                  String customerEmail, String customerPhone, Instant payBefore) {}

    public record VerifyRequest(@NotBlank String razorpayOrderId, @NotBlank String razorpayPaymentId,
                                @NotBlank String razorpaySignature) {}

    public record VerifyResponse(long orderId, String orderNumber, String orderStatus, String state) {}

    public record ManualRefundRequest(@NotNull Long orderId, @NotNull @Positive Long amountPaise,
                                      @NotBlank @Size(max = 500) String reason) {}

    public record PaymentRow(long id, long orderId, String orderNumber, String gatewayOrderId, String gatewayPaymentId,
                             long amountPaise, String status, String method, String errorCode,
                             OffsetDateTime capturedAt, OffsetDateTime createdAt) {}

    public record RefundRow(long id, long orderId, String orderNumber, long amountPaise, String status,
                            String gatewayRefundId, String receipt, String reason, String source, String error,
                            String initiatedByEmail, OffsetDateTime createdAt, OffsetDateTime processedAt) {}

    public record MismatchRow(long id, String runDate, String gatewayPaymentId, String gatewayOrderId, Long orderId,
                              String orderNumber, String kind, String detail, OffsetDateTime resolvedAt,
                              OffsetDateTime createdAt) {}
}
