package com.store.notification;

import com.store.common.config.AppProperties;
import com.store.common.outbox.EventTypes;
import com.store.common.outbox.OutboxHandler;
import com.store.common.util.Json;
import com.store.order.dto.OrderDtos.OrderDetail;
import com.store.order.service.OrderService;
import com.store.payment.domain.Refund;
import com.store.payment.domain.RefundRepository;
import com.store.payment.service.RefundService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Side effects run by the worker from outbox_events. Every one is safe to repeat. */
@Configuration
public class OutboxHandlers {

    private final NotificationService notifications;
    private final OrderService orders;

    public OutboxHandlers(NotificationService notifications, OrderService orders) {
        this.notifications = notifications;
        this.orders = orders;
    }

    @Bean
    OutboxHandler userSignedUpHandler() {
        return OutboxHandler.of(EventTypes.USER_SIGNED_UP, (id, p) -> notifications.send("welcome",
                p.path("email").asText(), "Welcome!", Map.of("name", p.path("name").asText()),
                p.path("userId").asLong(), id));
    }

    @Bean
    OutboxHandler orderPaidHandler() {
        return OutboxHandler.of(EventTypes.ORDER_PAID, (id, p) -> {
            OrderDetail o = orders.adminGet(p.path("orderId").asLong());
            Map<String, Object> model = Map.of("order", o);
            notifications.send("order-confirmed", o.email(), "Order confirmed: " + o.orderNumber(), model,
                    o.customerId(), id);
            notifications.sendToAdmins("admin-new-order", "New order " + o.orderNumber(), model, id);
            // Phase 8: generate the GST invoice PDF here.
        });
    }

    @Bean
    OutboxHandler orderCancelledHandler() {
        return OutboxHandler.of(EventTypes.ORDER_CANCELLED, (id, p) -> {
            OrderDetail o = orders.adminGet(p.path("orderId").asLong());
            notifications.send("order-cancelled", o.email(), "Order cancelled: " + o.orderNumber(),
                    Map.of("order", o, "refunded", p.path("refunded").asBoolean()), o.customerId(), id);
        });
    }

    @Bean
    OutboxHandler paymentFailedHandler() {
        return OutboxHandler.of(EventTypes.PAYMENT_FAILED, (id, p) -> {
            OrderDetail o = orders.adminGet(p.path("orderId").asLong());
            notifications.send("payment-failed", o.email(), "Payment not completed: " + o.orderNumber(),
                    Map.of("order", o), o.customerId(), id);
        });
    }

    @Bean
    OutboxHandler refundRequestedHandler(RefundService refunds) {
        return OutboxHandler.of(EventTypes.REFUND_REQUESTED, (id, p) -> refunds.execute(p.path("refundId").asLong()));
    }

    @Bean
    OutboxHandler refundProcessedHandler(RefundRepository refunds) {
        return OutboxHandler.of(EventTypes.REFUND_PROCESSED, (id, p) -> {
            Refund r = refunds.findById(p.path("refundId").asLong()).orElseThrow();
            OrderDetail o = orders.adminGet(r.getOrderId());
            Map<String, Object> model = new HashMap<>();
            model.put("order", o);
            model.put("amountPaise", r.getAmountPaise());
            model.put("refundId", r.getGatewayRefundId());
            notifications.send("refund-processed", o.email(), "Refund processed: " + o.orderNumber(), model,
                    o.customerId(), id);
        });
    }

    @Bean
    OutboxHandler refundFailedHandler() {
        return OutboxHandler.of(EventTypes.REFUND_FAILED, (id, p) -> notifications.sendToAdmins("admin-alert",
                "Refund failed", Map.of("title", "Refund failed",
                        "detail", "Refund #" + p.path("refundId").asLong() + ": " + p.path("error").asText()), id));
    }

    @Bean
    OutboxHandler adminAlertHandler() {
        return OutboxHandler.of(EventTypes.ADMIN_ALERT, (id, p) -> notifications.sendToAdmins("admin-alert",
                p.path("title").asText("Alert"),
                Map.of("title", p.path("title").asText(), "detail", p.path("detail").asText()), id));
    }

    /** Tells Next.js to rebuild ISR pages whose data changed. */
    @Bean
    OutboxHandler catalogChangedHandler(HttpClient http, AppProperties props, Json json) {
        return OutboxHandler.of(EventTypes.CATALOG_CHANGED, (id, p) -> {
            String url = props.frontend().revalidateUrl();
            if (url == null || url.isBlank()) {
                return;
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("x-revalidate-secret", props.frontend().revalidateSecret())
                    .POST(HttpRequest.BodyPublishers.ofString(json.write(Map.of("paths", p.path("paths")))))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                throw new IllegalStateException("Revalidate failed: " + res.statusCode());
            }
        });
    }
}
