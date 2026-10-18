package com.store.cart.service;

import com.store.common.error.ApiException;
import com.store.common.settings.SettingsService;
import com.store.common.web.RateLimiter;
import com.store.pricing.PricingService;
import com.store.pricing.PricingService.CartLine;
import com.store.pricing.PricingService.Quote;
import com.store.pricing.PricingService.VariantInfo;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guest cart keyed by a session cookie, user cart keyed by user id. On login the guest cart is merged
 * into the user cart (quantities summed, capped at available stock and the per-item limit).
 */
@Service
public class CartService {

    /** Exactly one of userId / sessionId is set. */
    public record Owner(Long userId, String sessionId) {
        public boolean isEmpty() {
            return userId == null && (sessionId == null || sessionId.isBlank());
        }
    }

    private record CartRow(long id, String couponCode) {}

    private record ItemRow(long id, long variantId, int qty, Long seenPricePaise) {}

    public record CartView(Long cartId, List<CartItemView> items, Quote pricing) {}

    public record CartItemView(long itemId, PricingService.QuoteLine line) {}

    private final JdbcClient jdbc;
    private final PricingService pricing;
    private final SettingsService settings;
    private final RateLimiter rateLimiter;

    public CartService(JdbcClient jdbc, PricingService pricing, SettingsService settings, RateLimiter rateLimiter) {
        this.jdbc = jdbc;
        this.pricing = pricing;
        this.settings = settings;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public CartView view(Owner owner, String destinationState, String warehouseState) {
        Optional<CartRow> cart = find(owner);
        if (cart.isEmpty()) {
            return new CartView(null, List.of(),
                    pricing.quote(List.of(), null, owner.userId(), destinationState, warehouseState));
        }
        List<ItemRow> items = items(cart.get().id());
        Quote quote = pricing.quote(items.stream().map(i -> new CartLine(i.variantId(), i.qty(), i.seenPricePaise())).toList(),
                cart.get().couponCode(), owner.userId(), destinationState, warehouseState);
        // Remember what the shopper has now seen so a price-change warning shows once.
        for (PricingService.QuoteLine l : quote.lines()) {
            jdbc.sql("UPDATE cart_items SET seen_price_paise = :p WHERE cart_id = :c AND variant_id = :v")
                    .param("p", l.unitPricePaise()).param("c", cart.get().id()).param("v", l.variantId()).update();
        }
        List<CartItemView> views = quote.lines().stream()
                .map(l -> new CartItemView(items.stream().filter(i -> i.variantId() == l.variantId())
                        .findFirst().map(ItemRow::id).orElse(0L), l))
                .toList();
        return new CartView(cart.get().id(), views, quote);
    }

    /** Raw lines for checkout. */
    @Transactional(readOnly = true)
    public Optional<CheckoutCart> forCheckout(long userId) {
        return find(new Owner(userId, null)).map(c -> new CheckoutCart(c.id(), c.couponCode(),
                items(c.id()).stream().map(i -> new CartLine(i.variantId(), i.qty(), null)).toList()));
    }

    public record CheckoutCart(long cartId, String couponCode, List<CartLine> lines) {}

    @Transactional
    public void addItem(Owner owner, long variantId, int qty) {
        requireOwner(owner);
        int maxQty = settings.getInt(SettingsService.MAX_QTY_PER_ITEM, 10);
        VariantInfo v = pricing.variantInfo(List.of(variantId)).get(variantId);
        if (v == null || !v.purchasable()) {
            throw ApiException.notFound("Product");
        }
        long cartId = findOrCreate(owner);
        int existing = jdbc.sql("SELECT COALESCE(MAX(qty), 0) FROM cart_items WHERE cart_id = :c AND variant_id = :v")
                .param("c", cartId).param("v", variantId).query(Integer.class).single();
        int wanted = existing + qty;
        checkQty(v, wanted, maxQty);
        jdbc.sql("""
                INSERT INTO cart_items (cart_id, variant_id, qty, seen_price_paise) VALUES (:c, :v, :q, :p)
                ON CONFLICT (cart_id, variant_id) DO UPDATE SET qty = :q, updated_at = now()
                """).param("c", cartId).param("v", variantId).param("q", wanted).param("p", v.pricePaise()).update();
        touch(cartId);
    }

    @Transactional
    public void updateItem(Owner owner, long itemId, int qty) {
        long cartId = find(owner).map(CartRow::id).orElseThrow(() -> ApiException.notFound("Cart item"));
        ItemRow item = jdbc.sql("SELECT id, variant_id, qty, seen_price_paise FROM cart_items WHERE id = :id AND cart_id = :c")
                .param("id", itemId).param("c", cartId).query(ItemRow.class).optional()
                .orElseThrow(() -> ApiException.notFound("Cart item"));
        if (qty <= 0) {
            removeItem(owner, itemId);
            return;
        }
        VariantInfo v = pricing.variantInfo(List.of(item.variantId())).get(item.variantId());
        if (v == null || !v.purchasable()) {
            throw ApiException.notFound("Product");
        }
        checkQty(v, qty, settings.getInt(SettingsService.MAX_QTY_PER_ITEM, 10));
        jdbc.sql("UPDATE cart_items SET qty = :q, updated_at = now() WHERE id = :id")
                .param("q", qty).param("id", itemId).update();
        touch(cartId);
    }

    @Transactional
    public void removeItem(Owner owner, long itemId) {
        find(owner).ifPresent(c -> {
            jdbc.sql("DELETE FROM cart_items WHERE id = :id AND cart_id = :c").param("id", itemId).param("c", c.id())
                    .update();
            touch(c.id());
        });
    }

    /** Stores the code after checking it against the current subtotal; per-user limits are checked again at checkout. */
    @Transactional
    public CartView applyCoupon(Owner owner, String code, String warehouseState) {
        requireOwner(owner);
        rateLimiter.check("coupon:" + (owner.userId() != null ? "u" + owner.userId() : "s" + owner.sessionId()), 10,
                Duration.ofMinutes(10));
        long cartId = find(owner).map(CartRow::id)
                .orElseThrow(() -> ApiException.badRequest("CART_EMPTY", "Your cart is empty"));
        jdbc.sql("UPDATE carts SET coupon_code = :code, updated_at = now() WHERE id = :id")
                .param("code", code.trim().toUpperCase()).param("id", cartId).update();
        CartView view = view(owner, null, warehouseState);
        if (view.pricing().couponError() != null) {
            jdbc.sql("UPDATE carts SET coupon_code = NULL WHERE id = :id").param("id", cartId).update();
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COUPON_INVALID", view.pricing().couponError());
        }
        return view;
    }

    @Transactional
    public void removeCoupon(Owner owner) {
        find(owner).ifPresent(c -> jdbc.sql("UPDATE carts SET coupon_code = NULL, updated_at = now() WHERE id = :id")
                .param("id", c.id()).update());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void mergeGuestCart(String sessionId, long userId) {
        Optional<CartRow> guest = find(new Owner(null, sessionId));
        if (guest.isEmpty()) {
            return;
        }
        long userCart = findOrCreate(new Owner(userId, null));
        int maxQty = settings.getInt(SettingsService.MAX_QTY_PER_ITEM, 10);
        List<ItemRow> guestItems = items(guest.get().id());
        Map<Long, VariantInfo> info = pricing.variantInfo(guestItems.stream().map(ItemRow::variantId).toList());
        for (ItemRow gi : guestItems) {
            VariantInfo v = info.get(gi.variantId());
            if (v == null || !v.purchasable() || v.available() <= 0) {
                continue;
            }
            // Sum quantities, capped at available stock and the per-item limit.
            jdbc.sql("""
                    INSERT INTO cart_items (cart_id, variant_id, qty, seen_price_paise)
                    VALUES (:c, :v, LEAST(:q, :cap), :p)
                    ON CONFLICT (cart_id, variant_id)
                    DO UPDATE SET qty = LEAST(cart_items.qty + :q, :cap), updated_at = now()
                    """).param("c", userCart).param("v", gi.variantId()).param("q", gi.qty())
                    .param("cap", Math.max(1, Math.min(maxQty, v.available()))).param("p", gi.seenPricePaise())
                    .update();
        }
        if (guest.get().couponCode() != null) {
            jdbc.sql("UPDATE carts SET coupon_code = COALESCE(coupon_code, :code) WHERE id = :id")
                    .param("code", guest.get().couponCode()).param("id", userCart).update();
        }
        jdbc.sql("DELETE FROM carts WHERE id = :id").param("id", guest.get().id()).update();
        touch(userCart);
    }

    /** After a successful checkout the cart is emptied (the order holds the items now). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void clear(long cartId) {
        jdbc.sql("DELETE FROM cart_items WHERE cart_id = :c").param("c", cartId).update();
        jdbc.sql("UPDATE carts SET coupon_code = NULL, updated_at = now() WHERE id = :c").param("c", cartId).update();
    }

    /** An unpaid order was cancelled or expired: give the shopper their items back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restore(long userId, List<CartLine> lines) {
        long cartId = findOrCreate(new Owner(userId, null));
        for (CartLine l : lines) {
            jdbc.sql("""
                    INSERT INTO cart_items (cart_id, variant_id, qty) VALUES (:c, :v, :q)
                    ON CONFLICT (cart_id, variant_id) DO NOTHING
                    """).param("c", cartId).param("v", l.variantId()).param("q", l.qty()).update();
        }
    }

    /** Housekeeping: guest carts untouched for 30 days. */
    @Transactional
    public int deleteStaleGuestCarts() {
        return jdbc.sql("DELETE FROM carts WHERE user_id IS NULL AND updated_at < now() - interval '30 days'").update();
    }

    // ---------- internals ----------

    private static void checkQty(VariantInfo v, int wanted, int maxQty) {
        if (wanted > maxQty) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QTY_LIMIT",
                    "You can buy up to " + maxQty + " of this item", Map.of("max", maxQty));
        }
        if (wanted > v.available()) {
            throw new ApiException(HttpStatus.CONFLICT, "OUT_OF_STOCK",
                    v.available() <= 0 ? "This item is out of stock" : "Only " + v.available() + " left in stock",
                    Map.of("available", Math.max(v.available(), 0)));
        }
    }

    private static void requireOwner(Owner owner) {
        if (owner.isEmpty()) {
            throw ApiException.badRequest("NO_CART_SESSION", "Cart session missing");
        }
    }

    private Optional<CartRow> find(Owner owner) {
        if (owner.userId() != null) {
            return jdbc.sql("SELECT id, coupon_code FROM carts WHERE user_id = :u").param("u", owner.userId())
                    .query(CartRow.class).optional();
        }
        if (owner.sessionId() != null && !owner.sessionId().isBlank()) {
            return jdbc.sql("SELECT id, coupon_code FROM carts WHERE session_id = :s").param("s", owner.sessionId())
                    .query(CartRow.class).optional();
        }
        return Optional.empty();
    }

    private long findOrCreate(Owner owner) {
        Optional<CartRow> existing = find(owner);
        if (existing.isPresent()) {
            return existing.get().id();
        }
        String conflictColumn = owner.userId() != null ? "user_id" : "session_id";
        return jdbc.sql("""
                INSERT INTO carts (user_id, session_id) VALUES (:u, :s)
                ON CONFLICT (%s) DO UPDATE SET updated_at = now()
                RETURNING id
                """.formatted(conflictColumn))
                .param("u", owner.userId())
                .param("s", owner.userId() != null ? null : owner.sessionId())
                .query(Long.class).single();
    }

    private List<ItemRow> items(long cartId) {
        return jdbc.sql("SELECT id, variant_id, qty, seen_price_paise FROM cart_items WHERE cart_id = :c ORDER BY id")
                .param("c", cartId).query(ItemRow.class).list();
    }

    private void touch(long cartId) {
        jdbc.sql("UPDATE carts SET updated_at = now() WHERE id = :c").param("c", cartId).update();
    }
}
