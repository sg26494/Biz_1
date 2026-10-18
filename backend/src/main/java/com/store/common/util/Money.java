package com.store.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** All money is stored and computed in paise (long). These helpers keep rounding in one place. */
public final class Money {

    private static final long BP_DENOMINATOR = 10_000L;

    private Money() {}

    /** amount x basisPoints / 10000, rounded half-up. 500 bp = 5%. */
    public static long percentOf(long amountPaise, long basisPoints) {
        return BigDecimal.valueOf(amountPaise)
                .multiply(BigDecimal.valueOf(basisPoints))
                .divide(BigDecimal.valueOf(BP_DENOMINATOR), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * Taxable value inside a GST-inclusive amount: inclusive x 10000 / (10000 + rate), half-up.
     * Tax = inclusive - taxable, so the two always add back to the inclusive amount.
     */
    public static long taxableFromInclusive(long inclusivePaise, int gstRateBp) {
        return BigDecimal.valueOf(inclusivePaise)
                .multiply(BigDecimal.valueOf(BP_DENOMINATOR))
                .divide(BigDecimal.valueOf(BP_DENOMINATOR + gstRateBp), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static String format(long paise) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
        return nf.format(BigDecimal.valueOf(paise, 2));
    }

    public static String rupees(long paise) {
        return BigDecimal.valueOf(paise, 2).toPlainString();
    }
}
