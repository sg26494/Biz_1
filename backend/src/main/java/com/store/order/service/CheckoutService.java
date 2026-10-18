package com.store.order.service;

import com.store.cart.service.CartService;
import com.store.cart.service.CartService.CheckoutCart;
import com.store.common.error.ApiException;
import com.store.common.idempotency.IdempotencyService;
import com.store.common.settings.SettingsService;
import com.store.common.web.RateLimiter;
import com.store.inventory.service.InventoryService;
import com.store.inventory.service.WarehouseService;
import com.store.inventory.service.WarehouseService.Warehouse;
import com.store.order.domain.Order;
import com.store.order.domain.OrderItem;
import com.store.order.domain.OrderItemRepository;
import com.store.order.domain.OrderRepository;
import com.store.order.domain.OrderStatus;
import com.store.order.dto.OrderDtos.CheckoutRequest;
import com.store.payment.dto.PaymentDtos.CheckoutPayment;
import com.store.payment.service.PaymentService;
import com.store.pricing.PricingService;
import com.store.pricing.PricingService.Quote;
import com.store.pricing.PricingService.QuoteLine;
import com.store.pricing.PricingService.WarningType;
import com.store.promo.service.CouponService;
import com.store.shipping.DeliveryService;
import com.store.user.domain.User;
import com.store.user.domain.UserAddress;
import com.store.user.service.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * POST /checkout (with Idempotency-Key):
 *  1. Re-validate cart, prices, coupon and pincode serviceability.
 *  2. One DB transaction: orders (PENDING_PAYMENT) + order_items snapshots + stock reservation + coupon redemption.
 *  3. Create the Razorpay order and save it in payments (CREATED).
 *  4. Return razorpay_order_id, key id and amount for Standard Checkout.
 * A repeated request with the same key returns the same order.
 */
@Service
public class CheckoutService {

    private static final Set<WarningType> BLOCKING = EnumSet.of(WarningType.UNAVAILABLE, WarningType.OUT_OF_STOCK,
            WarningType.QTY_REDUCED, WarningType.COUPON_INVALID);

    private final CartService carts;
    private final PricingService pricing;
    private final CouponService coupons;
    private final InventoryService inventory;
    private final WarehouseService warehouses;
    private final DeliveryService delivery;
    private final UserService users;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderNumberGenerator numbers;
    private final OrderLifecycle lifecycle;
    private final PaymentService payments;
    private final SettingsService settings;
    private final RateLimiter rateLimiter;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CheckoutService(CartService carts, PricingService pricing, CouponService coupons, InventoryService inventory,
                           WarehouseService warehouses, DeliveryService delivery, UserService users,
                           OrderRepository orders, OrderItemRepository orderItems, OrderNumberGenerator numbers,
                           OrderLifecycle lifecycle, PaymentService payments, SettingsService settings,
                           RateLimiter rateLimiter, PlatformTransactionManager txManager, Clock clock) {
        this.carts = carts;
        this.pricing = pricing;
        this.coupons = coupons;
        this.inventory = inventory;
        this.warehouses = warehouses;
        this.delivery = delivery;
        this.users = users;
        this.orders = orders;
        this.orderItems = orderItems;
        this.numbers = numbers;
        this.lifecycle = lifecycle;
        this.payments = payments;
        this.settings = settings;
        this.rateLimiter = rateLimiter;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    public CheckoutPayment checkout(long userId, String idempotencyKey, CheckoutRequest req) {
        IdempotencyService.requireKey(idempotencyKey);
        rateLimiter.check("checkout:" + userId, 10, Duration.ofMinutes(5));

        Optional<Order> existing = orders.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), userId);
        }

        User user = users.get(userId);
        if (user.getPhone() == null) {
            throw ApiException.badRequest("PHONE_REQUIRED", "Add your mobile number to your profile first");
        }
        UserAddress address = users.getAddress(userId, req.addressId());
        CheckoutCart cart = carts.forCheckout(userId)
                .filter(c -> !c.lines().isEmpty())
                .orElseThrow(() -> ApiException.badRequest("CART_EMPTY", "Your cart is empty"));
        Warehouse wh = warehouses.defaultWarehouse();

        // Outside the transaction: courier API call.
        Quote preview = pricing.quote(cart.lines(), cart.couponCode(), userId, address.getState(), wh.state());
        delivery.requireServiceable(address.getPincode(), Math.max(preview.totalWeightGrams(), 1));

        Order order;
        try {
            order = tx.execute(status -> createOrder(userId, user, idempotencyKey, req, address, cart, wh));
        } catch (DataIntegrityViolationException e) {
            // Same key raced in from a double click: return the order the other request created.
            return orders.findByIdempotencyKey(idempotencyKey).map(o -> replay(o, userId)).orElseThrow(() -> e);
        }
        return payments.ensureGatewayOrder(order.getId());
    }

    private CheckoutPayment replay(Order o, long userId) {
        if (o.getUserId() != userId) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key belongs to another request");
        }
        return payments.ensureGatewayOrder(o.getId());
    }

    private Order createOrder(long userId, User user, String key, CheckoutRequest req, UserAddress address,
                              CheckoutCart cart, Warehouse wh) {
        Quote q = pricing.quote(cart.lines(), cart.couponCode(), userId, address.getState(), wh.state());
        List<PricingService.Warning> blocking = q.warnings().stream().filter(w -> BLOCKING.contains(w.type())).toList();
        if (!blocking.isEmpty() || q.lines().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "CART_CHANGED",
                    "Your cart changed, please review it before paying", Map.of("warnings", blocking));
        }
        if (req.expectedTotalPaise() != null && req.expectedTotalPaise() != q.totalPaise()) {
            throw new ApiException(HttpStatus.CONFLICT, "PRICE_CHANGED", "The total changed, please review it",
                    Map.of("totalPaise", q.totalPaise()));
        }

        CouponService.Evaluation coupon = null;
        if (q.couponId() != null) {
            coupon = coupons.lockAndValidate(q.couponCode(), userId, q.subtotalPaise());
            if (coupon.discountPaise() != q.discountPaise()) {
                throw new ApiException(HttpStatus.CONFLICT, "PRICE_CHANGED", "The discount changed, please review it",
                        Map.of("totalPaise", q.totalPaise()));
            }
        }

        Instant now = clock.instant();
        int ttl = settings.getInt(SettingsService.RESERVATION_TTL_MINUTES, 15);
        Order o = new Order();
        o.setOrderNumber(numbers.next());
        o.setUserId(userId);
        o.setStatus(OrderStatus.PENDING_PAYMENT);
        o.setIdempotencyKey(key);
        o.setWarehouseId(wh.id());
        o.setEmail(user.getEmail());
        o.setPhone(address.getPhone());
        o.setShippingAddress(addressSnapshot(address));
        o.setSubtotalPaise(q.subtotalPaise());
        o.setDiscountPaise(q.discountPaise());
        o.setShippingPaise(q.shippingPaise());
        o.setTotalPaise(q.totalPaise());
        o.setTaxablePaise(q.taxablePaise());
        o.setCgstPaise(q.cgstPaise());
        o.setSgstPaise(q.sgstPaise());
        o.setIgstPaise(q.igstPaise());
        o.setIntraState(q.intraState());
        o.setCouponCode(coupon != null ? coupon.code() : null);
        o.setPaymentExpiresAt(now.plus(Duration.ofMinutes(ttl)));
        orders.saveAndFlush(o);

        for (QuoteLine l : q.lines()) {
            OrderItem i = new OrderItem();
            i.setOrderId(o.getId());
            i.setProductId(l.productId());
            i.setVariantId(l.variantId());
            i.setSku(l.sku());
            i.setProductTitle(l.productTitle());
            i.setVariantTitle(l.variantTitle());
            i.setAttributes(l.attributes());
            i.setImageUrl(l.imageUrl());
            i.setHsnCode(l.hsnCode());
            i.setGstRateBp(l.gstRateBp());
            i.setReturnable(l.returnable());
            i.setWeightGrams(l.weightGrams());
            i.setMrpPaise(l.mrpPaise());
            i.setUnitPricePaise(l.unitPricePaise());
            i.setQty(l.qty());
            i.setLineTotalPaise(l.lineTotalPaise());
            i.setDiscountPaise(l.discountPaise());
            i.setTaxablePaise(l.taxablePaise());
            i.setCgstPaise(l.cgstPaise());
            i.setSgstPaise(l.sgstPaise());
            i.setIgstPaise(l.igstPaise());
            orderItems.save(i);
        }

        inventory.reserve(o.getId(), wh.id(),
                q.lines().stream().map(l -> new InventoryService.Line(l.variantId(), l.qty())).toList(),
                o.getPaymentExpiresAt());
        if (coupon != null) {
            coupons.redeem(coupon.couponId(), userId, o.getId(), q.discountPaise());
        }
        lifecycle.created(o);
        carts.clear(cart.cartId());
        return o;
    }

    private static Map<String, Object> addressSnapshot(UserAddress a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", a.getName());
        m.put("phone", a.getPhone());
        m.put("line1", a.getLine1());
        m.put("line2", a.getLine2());
        m.put("landmark", a.getLandmark());
        m.put("city", a.getCity());
        m.put("state", a.getState());
        m.put("pincode", a.getPincode());
        m.put("country", "India");
        return m;
    }
}
