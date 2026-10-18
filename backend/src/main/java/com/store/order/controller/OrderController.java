package com.store.order.controller;

import com.store.auth.security.CurrentUser;
import com.store.common.web.PageResponse;
import com.store.order.dto.OrderDtos.CancelRequest;
import com.store.order.dto.OrderDtos.CheckoutRequest;
import com.store.order.dto.OrderDtos.OrderDetail;
import com.store.order.dto.OrderDtos.OrderSummary;
import com.store.order.service.CheckoutService;
import com.store.order.service.OrderService;
import com.store.payment.dto.PaymentDtos.CheckoutPayment;
import com.store.payment.dto.PaymentDtos.VerifyRequest;
import com.store.payment.dto.PaymentDtos.VerifyResponse;
import com.store.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final CheckoutService checkout;
    private final OrderService orders;
    private final PaymentService payments;

    public OrderController(CheckoutService checkout, OrderService orders, PaymentService payments) {
        this.checkout = checkout;
        this.orders = orders;
        this.payments = payments;
    }

    @PostMapping("/checkout")
    public CheckoutPayment checkout(@RequestHeader(name = "Idempotency-Key", required = false) String key,
                                    @Valid @RequestBody CheckoutRequest req) {
        return checkout.checkout(CurrentUser.id(), key, req);
    }

    @PostMapping("/payments/verify")
    public VerifyResponse verify(@Valid @RequestBody VerifyRequest req) {
        return payments.verifyClient(CurrentUser.id(), req);
    }

    @PostMapping("/orders/{id}/retry-payment")
    public CheckoutPayment retry(@PathVariable long id) {
        long userId = CurrentUser.id();
        orders.moveToPendingForRetry(userId, id);
        orders.requireOwned(userId, id);
        return payments.ensureGatewayOrder(id);
    }

    @GetMapping("/orders")
    public PageResponse<OrderSummary> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return orders.listMine(CurrentUser.id(), Math.max(0, page), PageResponse.clampSize(size, 50));
    }

    /** The payment "processing" screen polls this until status is PAID or the order is cancelled. */
    @GetMapping("/orders/{id}")
    public OrderDetail get(@PathVariable long id) {
        return orders.getMine(CurrentUser.id(), id);
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderDetail cancel(@PathVariable long id, @Valid @RequestBody CancelRequest req) {
        return orders.cancelByUser(CurrentUser.id(), id, req.reason());
    }

    /** Invoice PDFs arrive in phase 8 (invoice module). */
    @GetMapping("/orders/{id}/invoice")
    public ResponseEntity<Void> invoice(@PathVariable long id) {
        orders.requireOwned(CurrentUser.id(), id);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
