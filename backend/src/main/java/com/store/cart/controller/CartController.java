package com.store.cart.controller;

import com.store.auth.security.CurrentUser;
import com.store.cart.service.CartService;
import com.store.cart.service.CartService.CartView;
import com.store.cart.service.CartService.Owner;
import com.store.common.config.AppProperties;
import com.store.common.util.Hashing;
import com.store.inventory.service.WarehouseService;
import com.store.promo.dto.CouponDtos.ApplyCouponRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    public static final String SESSION_COOKIE = "sid";

    public record AddItemRequest(@NotNull Long variantId, @NotNull @Min(1) @Max(100) Integer qty) {}

    public record UpdateItemRequest(@NotNull @Min(0) @Max(100) Integer qty) {}

    private final CartService carts;
    private final WarehouseService warehouses;
    private final AppProperties props;

    public CartController(CartService carts, WarehouseService warehouses, AppProperties props) {
        this.carts = carts;
        this.warehouses = warehouses;
        this.props = props;
    }

    @GetMapping
    public CartView get(@CookieValue(name = SESSION_COOKIE, required = false) String sid) {
        return carts.view(owner(sid), null, warehouses.defaultWarehouse().state());
    }

    @PostMapping("/items")
    public CartView add(@Valid @RequestBody AddItemRequest req,
                        @CookieValue(name = SESSION_COOKIE, required = false) String sid, HttpServletResponse res) {
        Owner owner = ownerCreatingSession(sid, res);
        carts.addItem(owner, req.variantId(), req.qty());
        return carts.view(owner, null, warehouses.defaultWarehouse().state());
    }

    @PatchMapping("/items/{itemId}")
    public CartView update(@PathVariable long itemId, @Valid @RequestBody UpdateItemRequest req,
                           @CookieValue(name = SESSION_COOKIE, required = false) String sid) {
        Owner owner = owner(sid);
        carts.updateItem(owner, itemId, req.qty());
        return carts.view(owner, null, warehouses.defaultWarehouse().state());
    }

    @DeleteMapping("/items/{itemId}")
    public CartView remove(@PathVariable long itemId,
                           @CookieValue(name = SESSION_COOKIE, required = false) String sid) {
        Owner owner = owner(sid);
        carts.removeItem(owner, itemId);
        return carts.view(owner, null, warehouses.defaultWarehouse().state());
    }

    @PostMapping("/coupon")
    public CartView applyCoupon(@Valid @RequestBody ApplyCouponRequest req,
                                @CookieValue(name = SESSION_COOKIE, required = false) String sid) {
        return carts.applyCoupon(owner(sid), req.code(), warehouses.defaultWarehouse().state());
    }

    @DeleteMapping("/coupon")
    public CartView removeCoupon(@CookieValue(name = SESSION_COOKIE, required = false) String sid) {
        Owner owner = owner(sid);
        carts.removeCoupon(owner);
        return carts.view(owner, null, warehouses.defaultWarehouse().state());
    }

    private static Owner owner(String sid) {
        Long userId = CurrentUser.idOrNull();
        return userId != null ? new Owner(userId, null) : new Owner(null, validSid(sid));
    }

    private Owner ownerCreatingSession(String sid, HttpServletResponse res) {
        Owner owner = owner(sid);
        if (owner.isEmpty()) {
            String fresh = Hashing.randomUrlToken(24);
            res.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(fresh, Duration.ofDays(30), props.security().cookieSecure()));
            return new Owner(null, fresh);
        }
        return owner;
    }

    private static String validSid(String sid) {
        return sid != null && sid.matches("^[A-Za-z0-9_-]{16,64}$") ? sid : null;
    }

    private static String sessionCookie(String value, Duration maxAge, boolean secure) {
        return ResponseCookie.from(SESSION_COOKIE, value).httpOnly(true).secure(secure).sameSite("Lax").path("/")
                .maxAge(maxAge).build().toString();
    }

    public static String clearSessionCookie(boolean secure) {
        return sessionCookie("", Duration.ZERO, secure);
    }
}
