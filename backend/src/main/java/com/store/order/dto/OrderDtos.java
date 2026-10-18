package com.store.order.dto;

import com.store.order.domain.Order;
import com.store.order.domain.OrderItem;
import com.store.order.service.OrderLifecycle.HistoryRow;
import com.store.payment.dto.PaymentDtos.RefundRow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class OrderDtos {

    private OrderDtos() {}

    /** expectedTotalPaise: what the shopper saw; if the server total differs we answer 409 PRICE_CHANGED. */
    public record CheckoutRequest(@NotNull Long addressId, Long expectedTotalPaise) {}

    public record CancelRequest(@NotBlank @Size(max = 500) String reason) {}

    public record OrderSummary(long id, String orderNumber, String status, long totalPaise, int itemCount,
                               String firstItemTitle, String firstItemImage, Instant createdAt) {}

    public record ItemView(long id, long productId, long variantId, String sku, String productTitle,
                           String variantTitle, Map<String, Object> attributes, String imageUrl, String hsnCode,
                           int gstRateBp, boolean returnable, long mrpPaise, long unitPricePaise, int qty,
                           long lineTotalPaise, long discountPaise, long taxablePaise, long cgstPaise, long sgstPaise,
                           long igstPaise) {
        public static ItemView of(OrderItem i) {
            return new ItemView(i.getId(), i.getProductId(), i.getVariantId(), i.getSku(), i.getProductTitle(),
                    i.getVariantTitle(), i.getAttributes(), i.getImageUrl(), i.getHsnCode(), i.getGstRateBp(),
                    i.isReturnable(), i.getMrpPaise(), i.getUnitPricePaise(), i.getQty(), i.getLineTotalPaise(),
                    i.getDiscountPaise(), i.getTaxablePaise(), i.getCgstPaise(), i.getSgstPaise(), i.getIgstPaise());
        }
    }

    public record PaymentInfo(String status, String method, String gatewayPaymentId, Instant capturedAt) {}

    public record OrderDetail(long id, String orderNumber, String status, String email, String phone,
                              Map<String, Object> shippingAddress, List<ItemView> items, long subtotalPaise,
                              long discountPaise, long shippingPaise, long totalPaise, long taxablePaise,
                              long cgstPaise, long sgstPaise, long igstPaise, String couponCode,
                              Instant paymentExpiresAt, Instant paidAt, Instant shippedAt, Instant deliveredAt,
                              Instant cancelledAt, String cancelReason, Instant createdAt, PaymentInfo payment,
                              List<HistoryRow> timeline, List<RefundRow> refunds, boolean canCancel,
                              boolean canRetryPayment, Long customerId, String customerName) {

        public static OrderDetail of(Order o, List<OrderItem> items, PaymentInfo payment, List<HistoryRow> timeline,
                                     List<RefundRow> refunds, boolean canCancel, boolean canRetry, String customerName) {
            return new OrderDetail(o.getId(), o.getOrderNumber(), o.getStatus().name(), o.getEmail(), o.getPhone(),
                    o.getShippingAddress(), items.stream().map(ItemView::of).toList(), o.getSubtotalPaise(),
                    o.getDiscountPaise(), o.getShippingPaise(), o.getTotalPaise(), o.getTaxablePaise(),
                    o.getCgstPaise(), o.getSgstPaise(), o.getIgstPaise(), o.getCouponCode(),
                    o.getPaymentExpiresAt(), o.getPaidAt(), o.getShippedAt(), o.getDeliveredAt(), o.getCancelledAt(),
                    o.getCancelReason(), o.getCreatedAt(), payment, timeline, refunds, canCancel, canRetry,
                    o.getUserId(), customerName);
        }
    }

    public record AdminOrderRow(long id, String orderNumber, String status, long totalPaise, String customerName,
                                String email, String phone, String city, String state, int itemCount,
                                String paymentStatus, java.time.OffsetDateTime createdAt,
                                java.time.OffsetDateTime paidAt) {}
}
