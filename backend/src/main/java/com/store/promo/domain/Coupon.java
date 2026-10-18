package com.store.promo.domain;

import com.store.common.domain.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "coupons")
public class Coupon extends Timestamped {

    public enum Type { PERCENT, FLAT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private Type type;

    /** PERCENT: basis points (1000 = 10%). FLAT: paise. */
    @Column(nullable = false)
    private long discountValue;

    @Column(nullable = false)
    private long minOrderPaise;

    private Long maxDiscountPaise;

    private Instant startsAt;

    private Instant endsAt;

    private Integer usageLimitTotal;

    private Integer usageLimitPerUser;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
