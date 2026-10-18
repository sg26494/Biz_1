package com.store.admin.controller;

import com.store.auth.security.CurrentUser;
import com.store.common.idempotency.IdempotencyService;
import com.store.common.web.PageResponse;
import com.store.order.dto.OrderDtos.AdminOrderRow;
import com.store.order.dto.OrderDtos.CancelRequest;
import com.store.order.dto.OrderDtos.OrderDetail;
import com.store.order.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Pack / ship / retry-shipping endpoints are added with the Shiprocket module (phase 6). */
@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orders;
    private final IdempotencyService idempotency;

    public AdminOrderController(OrderService orders, IdempotencyService idempotency) {
        this.orders = orders;
        this.idempotency = idempotency;
    }

    @GetMapping
    public PageResponse<AdminOrderRow> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "25") int size) {
        return orders.adminList(status, q, from, to, Math.max(0, page), PageResponse.clampSize(size, 100));
    }

    @GetMapping("/{id}")
    public OrderDetail get(@PathVariable long id) {
        return orders.adminGet(id);
    }

    /** Confirm + reason in the UI; Idempotency-Key makes a double click harmless. */
    @PostMapping("/{id}/cancel")
    public JsonNode cancel(@PathVariable long id, @Valid @RequestBody CancelRequest req,
                           @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        long admin = CurrentUser.id();
        return idempotency.execute("admin-order-cancel:" + id, admin, key, req,
                () -> orders.cancelByAdmin(id, req.reason(), admin));
    }
}
