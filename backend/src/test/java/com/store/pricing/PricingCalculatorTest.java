package com.store.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.store.common.util.Money;
import com.store.pricing.PricingCalculator.LineInput;
import com.store.pricing.PricingCalculator.Result;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingCalculatorTest {

    @Test
    void taxIsComputedBackwardsFromInclusivePrice() {
        // ₹1,050 inclusive at 5% -> taxable ₹1,000, tax ₹50
        var t = PricingCalculator.split(105_000, 500, true);
        assertThat(t.taxablePaise()).isEqualTo(100_000);
        assertThat(t.cgstPaise()).isEqualTo(2_500);
        assertThat(t.sgstPaise()).isEqualTo(2_500);
        assertThat(t.igstPaise()).isZero();
    }

    @Test
    void interStateUsesIgst() {
        var t = PricingCalculator.split(105_000, 500, false);
        assertThat(t.igstPaise()).isEqualTo(5_000);
        assertThat(t.cgstPaise() + t.sgstPaise()).isZero();
    }

    @Test
    void oddTaxSplitsWithoutLosingAPaisa() {
        var t = PricingCalculator.split(99_999, 1200, true);
        assertThat(t.taxablePaise() + t.cgstPaise() + t.sgstPaise()).isEqualTo(99_999);
        assertThat(Math.abs(t.cgstPaise() - t.sgstPaise())).isLessThanOrEqualTo(1);
    }

    @Test
    void discountAllocationAlwaysSumsExactly() {
        long[] shares = PricingCalculator.allocate(new long[] {33_333, 33_333, 33_334}, 10_001);
        assertThat(Arrays.stream(shares).sum()).isEqualTo(10_001);
        long[] none = PricingCalculator.allocate(new long[] {100, 200}, 0);
        assertThat(Arrays.stream(none).sum()).isZero();
    }

    @Test
    void orderTotalIsSubtotalMinusDiscountPlusShipping() {
        Result r = PricingCalculator.calculate(List.of(
                new LineInput(249_900, 2, 500),   // razai x2
                new LineInput(129_900, 1, 1200)), // sheet x1
                50_000, 9_900, false);
        assertThat(r.subtotalPaise()).isEqualTo(629_700);
        assertThat(r.totalPaise()).isEqualTo(629_700 - 50_000 + 9_900);
        // every paisa of the total is either taxable value or tax
        assertThat(r.taxablePaise() + r.taxPaise()).isEqualTo(r.totalPaise());
        assertThat(r.lines().stream().mapToLong(l -> l.discountPaise()).sum()).isEqualTo(50_000);
    }

    @Test
    void discountNeverExceedsSubtotal() {
        Result r = PricingCalculator.calculate(List.of(new LineInput(10_000, 1, 500)), 50_000, 0, true);
        assertThat(r.discountPaise()).isEqualTo(10_000);
        assertThat(r.totalPaise()).isZero();
    }

    @Test
    void percentOfRoundsHalfUp() {
        assertThat(Money.percentOf(12_345, 1000)).isEqualTo(1_235); // 10% of ₹123.45
    }
}
