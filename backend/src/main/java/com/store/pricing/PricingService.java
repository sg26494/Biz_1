package com.store.pricing;

import com.store.common.settings.SettingsService;
import com.store.common.util.IndianStates;
import com.store.common.util.Json;
import com.store.pricing.PricingCalculator.LineInput;
import com.store.pricing.PricingCalculator.LineResult;
import com.store.promo.service.CouponService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prices are always recomputed from the DB; the cart only stores variant + qty.
 * Produces the pricing preview the cart and checkout show: items, discount, shipping, tax, total, warnings.
 */
@Service
public class PricingService {

    public record CartLine(long variantId, int qty, Long seenPricePaise) {}

    public enum WarningType { OUT_OF_STOCK, LOW_STOCK, QTY_REDUCED, PRICE_CHANGED, UNAVAILABLE, COUPON_INVALID }

    public record Warning(WarningType type, Long variantId, String message) {}

    public record VariantInfo(long variantId, long productId, String sku, String variantTitle, String attributes,
                              long mrpPaise, long pricePaise, int weightGrams, boolean purchasable,
                              String productTitle, String productSlug, String hsnCode, int gstRateBp,
                              boolean returnable, String imageUrl, int available) {}

    public record QuoteLine(long variantId, long productId, String sku, String productTitle, String productSlug,
                            String variantTitle, Map<String, Object> attributes, String imageUrl, String hsnCode,
                            int gstRateBp, boolean returnable, int weightGrams, long mrpPaise, long unitPricePaise,
                            int qty, int available, long lineTotalPaise, long discountPaise, long taxablePaise,
                            long cgstPaise, long sgstPaise, long igstPaise) {}

    public record Quote(List<QuoteLine> lines, long subtotalPaise, long discountPaise, long shippingPaise,
                        long taxPaise, long totalPaise, long taxablePaise, long cgstPaise, long sgstPaise,
                        long igstPaise, boolean intraState, String couponCode, Long couponId, String couponError,
                        Long freeShippingRemainingPaise, int totalWeightGrams, List<Warning> warnings) {}

    private final JdbcClient jdbc;
    private final CouponService coupons;
    private final SettingsService settings;
    private final Json json;

    public PricingService(JdbcClient jdbc, CouponService coupons, SettingsService settings, Json json) {
        this.jdbc = jdbc;
        this.coupons = coupons;
        this.settings = settings;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Map<Long, VariantInfo> variantInfo(List<Long> variantIds) {
        Map<Long, VariantInfo> map = new HashMap<>();
        if (variantIds.isEmpty()) {
            return map;
        }
        jdbc.sql("""
                SELECT pv.id AS variant_id, pv.product_id, pv.sku, pv.title AS variant_title,
                       pv.attributes::text AS attributes, pv.mrp_paise, pv.price_paise, pv.weight_grams,
                       (pv.is_active AND p.status = 'ACTIVE') AS purchasable,
                       p.title AS product_title, p.slug AS product_slug, p.hsn_code, p.gst_rate_bp,
                       p.is_returnable AS returnable,
                       (SELECT pi.url FROM product_images pi
                         WHERE pi.product_id = p.id AND (pi.variant_id IS NULL OR pi.variant_id = pv.id)
                         ORDER BY (pi.variant_id = pv.id) DESC NULLS LAST, pi.sort_order, pi.id LIMIT 1) AS image_url,
                       COALESCE((SELECT SUM(i.on_hand - i.reserved) FROM inventory i WHERE i.variant_id = pv.id), 0)::int
                         AS available
                FROM product_variants pv JOIN products p ON p.id = pv.product_id
                WHERE pv.id IN (:ids)
                """).param("ids", variantIds).query(VariantInfo.class).list()
                .forEach(v -> map.put(v.variantId(), v));
        return map;
    }

    /**
     * @param destinationState shipping state if known (for the CGST/SGST vs IGST split); null in cart preview
     * @param warehouseState   state of the dispatching warehouse
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Quote quote(List<CartLine> cartLines, String couponCode, Long userId, String destinationState,
                       String warehouseState) {
        int maxQty = settings.getInt(SettingsService.MAX_QTY_PER_ITEM, 10);
        Map<Long, VariantInfo> info = variantInfo(cartLines.stream().map(CartLine::variantId).toList());
        List<Warning> warnings = new ArrayList<>();
        List<VariantInfo> priced = new ArrayList<>();
        List<Integer> qtys = new ArrayList<>();

        for (CartLine cl : cartLines) {
            VariantInfo v = info.get(cl.variantId());
            if (v == null || !v.purchasable()) {
                warnings.add(new Warning(WarningType.UNAVAILABLE, cl.variantId(), "An item is no longer available"));
                continue;
            }
            if (v.available() <= 0) {
                warnings.add(new Warning(WarningType.OUT_OF_STOCK, v.variantId(), v.productTitle() + " is out of stock"));
                continue;
            }
            int qty = Math.min(cl.qty(), maxQty);
            if (qty > v.available()) {
                qty = v.available();
                warnings.add(new Warning(WarningType.QTY_REDUCED, v.variantId(),
                        "Only " + v.available() + " left of " + v.productTitle()));
            } else if (v.available() <= 3) {
                warnings.add(new Warning(WarningType.LOW_STOCK, v.variantId(), "Only " + v.available() + " left"));
            }
            if (cl.seenPricePaise() != null && cl.seenPricePaise() != v.pricePaise()) {
                warnings.add(new Warning(WarningType.PRICE_CHANGED, v.variantId(),
                        "The price of " + v.productTitle() + " has changed"));
            }
            priced.add(v);
            qtys.add(qty);
        }

        long subtotal = 0;
        int weight = 0;
        for (int i = 0; i < priced.size(); i++) {
            subtotal += priced.get(i).pricePaise() * qtys.get(i);
            weight += priced.get(i).weightGrams() * qtys.get(i);
        }

        long discount = 0;
        Long couponId = null;
        String couponError = null;
        if (couponCode != null && !couponCode.isBlank() && subtotal > 0) {
            CouponService.Evaluation e = coupons.evaluate(couponCode, userId, subtotal);
            if (e.valid()) {
                discount = e.discountPaise();
                couponId = e.couponId();
            } else {
                couponError = e.error();
                warnings.add(new Warning(WarningType.COUPON_INVALID, null, e.error()));
            }
        }

        long goods = subtotal - discount;
        long shipping = 0;
        Long freeRemaining = null;
        if (subtotal > 0) {
            Long threshold = settings.getNullableLong(SettingsService.SHIPPING_FREE_THRESHOLD);
            long flat = settings.getLong(SettingsService.SHIPPING_FLAT_FEE, 0);
            if (threshold != null && goods >= threshold) {
                shipping = 0;
            } else {
                shipping = flat;
                if (threshold != null) {
                    freeRemaining = threshold - goods;
                }
            }
        }

        boolean intra = destinationState == null || IndianStates.sameState(destinationState, warehouseState);
        List<LineInput> inputs = new ArrayList<>();
        for (int i = 0; i < priced.size(); i++) {
            inputs.add(new LineInput(priced.get(i).pricePaise(), qtys.get(i), priced.get(i).gstRateBp()));
        }
        PricingCalculator.Result r = PricingCalculator.calculate(inputs, discount, shipping, intra);

        List<QuoteLine> lines = new ArrayList<>();
        for (int i = 0; i < priced.size(); i++) {
            VariantInfo v = priced.get(i);
            LineResult lr = r.lines().get(i);
            Map<String, Object> attrs = v.attributes() == null ? Map.of() : json.read(v.attributes(), Map.class);
            lines.add(new QuoteLine(v.variantId(), v.productId(), v.sku(), v.productTitle(), v.productSlug(),
                    v.variantTitle(), attrs, v.imageUrl(), v.hsnCode(), v.gstRateBp(), v.returnable(),
                    v.weightGrams(), v.mrpPaise(), v.pricePaise(), qtys.get(i), v.available(), lr.lineTotalPaise(),
                    lr.discountPaise(), lr.tax().taxablePaise(), lr.tax().cgstPaise(), lr.tax().sgstPaise(),
                    lr.tax().igstPaise()));
        }
        return new Quote(lines, r.subtotalPaise(), r.discountPaise(), r.shippingPaise(), r.taxPaise(), r.totalPaise(),
                r.taxablePaise(), r.cgstPaise(), r.sgstPaise(), r.igstPaise(), intra,
                couponId != null ? couponCode.trim().toUpperCase() : (couponCode == null ? null : couponCode.trim().toUpperCase()),
                couponId, couponError, freeRemaining, weight, warnings);
    }
}
