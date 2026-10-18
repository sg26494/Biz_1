package com.store.pricing;

import com.store.common.util.Money;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Pure money maths, no I/O, so it is easy to unit test.
 *
 * Order total = sum(unit price x qty) - coupon discount + shipping fee. Prices are GST-inclusive.
 * The coupon discount is spread across lines in proportion to line value (largest-remainder rounding,
 * so shares add up exactly). Each line's tax is then worked backwards from its discounted inclusive value.
 * Same-state delivery splits tax into CGST + SGST; otherwise IGST.
 *
 * Shipping is treated as part of a composite supply and taxed at the highest GST rate in the order.
 * Confirm this treatment with your CA.
 */
public final class PricingCalculator {

    private PricingCalculator() {}

    public record LineInput(long unitPricePaise, int qty, int gstRateBp) {
        public long lineTotal() {
            return unitPricePaise * qty;
        }
    }

    public record TaxSplit(long taxablePaise, long cgstPaise, long sgstPaise, long igstPaise) {
        public long taxPaise() {
            return cgstPaise + sgstPaise + igstPaise;
        }
    }

    public record LineResult(long lineTotalPaise, long discountPaise, TaxSplit tax) {}

    public record Result(List<LineResult> lines, long subtotalPaise, long discountPaise, long shippingPaise,
                         TaxSplit shippingTax, long totalPaise, long taxablePaise, long cgstPaise, long sgstPaise,
                         long igstPaise) {
        public long taxPaise() {
            return cgstPaise + sgstPaise + igstPaise;
        }
    }

    public static Result calculate(List<LineInput> lines, long discountPaise, long shippingPaise, boolean intraState) {
        long subtotal = lines.stream().mapToLong(LineInput::lineTotal).sum();
        long discount = Math.max(0, Math.min(discountPaise, subtotal));
        long[] shares = allocate(lines.stream().mapToLong(LineInput::lineTotal).toArray(), discount);

        List<LineResult> out = new ArrayList<>(lines.size());
        long taxable = 0;
        long cgst = 0;
        long sgst = 0;
        long igst = 0;
        int maxRate = 0;
        for (int i = 0; i < lines.size(); i++) {
            LineInput in = lines.get(i);
            TaxSplit t = split(in.lineTotal() - shares[i], in.gstRateBp(), intraState);
            out.add(new LineResult(in.lineTotal(), shares[i], t));
            taxable += t.taxablePaise();
            cgst += t.cgstPaise();
            sgst += t.sgstPaise();
            igst += t.igstPaise();
            maxRate = Math.max(maxRate, in.gstRateBp());
        }
        TaxSplit shipTax = split(shippingPaise, maxRate, intraState);
        taxable += shipTax.taxablePaise();
        cgst += shipTax.cgstPaise();
        sgst += shipTax.sgstPaise();
        igst += shipTax.igstPaise();
        long total = subtotal - discount + shippingPaise;
        return new Result(out, subtotal, discount, shippingPaise, shipTax, total, taxable, cgst, sgst, igst);
    }

    /** Tax inside a GST-inclusive amount. CGST gets the floor half, SGST the rest, so they add up exactly. */
    public static TaxSplit split(long inclusivePaise, int gstRateBp, boolean intraState) {
        if (inclusivePaise <= 0) {
            return new TaxSplit(0, 0, 0, 0);
        }
        long taxable = Money.taxableFromInclusive(inclusivePaise, gstRateBp);
        long tax = inclusivePaise - taxable;
        if (intraState) {
            long cgst = tax / 2;
            return new TaxSplit(taxable, cgst, tax - cgst, 0);
        }
        return new TaxSplit(taxable, 0, 0, tax);
    }

    /** Proportional allocation with largest-remainder rounding; shares always sum to {@code amount}. */
    public static long[] allocate(long[] weights, long amount) {
        long[] shares = new long[weights.length];
        long totalWeight = 0;
        for (long w : weights) {
            totalWeight += w;
        }
        if (amount <= 0 || totalWeight <= 0) {
            return shares;
        }
        long allocated = 0;
        long[] remainders = new long[weights.length];
        for (int i = 0; i < weights.length; i++) {
            // weights and amounts are paise of a retail order, far below overflow for long multiplication
            long numerator = weights[i] * amount;
            shares[i] = numerator / totalWeight;
            remainders[i] = numerator % totalWeight;
            allocated += shares[i];
        }
        long left = amount - allocated;
        final long[] rem = remainders;
        List<Integer> order = IntStream.range(0, weights.length).boxed()
                .sorted(Comparator.comparingLong((Integer i) -> rem[i]).reversed().thenComparing(i -> i))
                .toList();
        for (int k = 0; k < left; k++) {
            shares[order.get(k % order.size())]++;
        }
        return shares;
    }
}
