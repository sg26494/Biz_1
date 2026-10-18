package com.store.promo.dto;

import com.store.promo.domain.Coupon;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;

public final class CouponDtos {

    private CouponDtos() {}

    public record CouponRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
            @Size(max = 300) String description,
            @NotNull Coupon.Type type,
            @NotNull @Positive Long discountValue,
            @PositiveOrZero Long minOrderPaise,
            @Positive Long maxDiscountPaise,
            Instant startsAt,
            Instant endsAt,
            @Positive Integer usageLimitTotal,
            @Positive Integer usageLimitPerUser,
            Boolean active) {}

    public record CouponView(long id, String code, String description, String type, long discountValue,
                             long minOrderPaise, Long maxDiscountPaise, OffsetDateTime startsAt, OffsetDateTime endsAt,
                             Integer usageLimitTotal, Integer usageLimitPerUser, boolean active, long timesUsed,
                             long totalDiscountPaise) {}

    public record ApplyCouponRequest(@NotBlank @Size(max = 40) String code) {}
}
