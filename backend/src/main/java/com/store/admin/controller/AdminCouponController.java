package com.store.admin.controller;

import com.store.auth.security.CurrentUser;
import com.store.promo.dto.CouponDtos.CouponRequest;
import com.store.promo.dto.CouponDtos.CouponView;
import com.store.promo.service.CouponService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/coupons")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouponController {

    private final CouponService coupons;

    public AdminCouponController(CouponService coupons) {
        this.coupons = coupons;
    }

    @GetMapping
    public List<CouponView> list() {
        return coupons.list();
    }

    @GetMapping("/{id}")
    public CouponView get(@PathVariable long id) {
        return coupons.get(id);
    }

    @PostMapping
    public CouponView create(@Valid @RequestBody CouponRequest req) {
        return coupons.create(req, CurrentUser.id());
    }

    @PutMapping("/{id}")
    public CouponView update(@PathVariable long id, @Valid @RequestBody CouponRequest req) {
        return coupons.update(id, req, CurrentUser.id());
    }

    /** Coupons are disabled rather than deleted (redemptions reference them). */
    @PostMapping("/{id}/active")
    public CouponView setActive(@PathVariable long id, @RequestParam boolean value) {
        return coupons.setActive(id, value, CurrentUser.id());
    }
}
