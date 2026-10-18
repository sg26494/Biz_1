package com.store.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Snapshot of what was bought: later catalog edits never change past orders. */
@Getter
@Setter
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long variantId;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String productTitle;

    @Column(nullable = false)
    private String variantTitle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    private String imageUrl;

    @Column(nullable = false)
    private String hsnCode;

    @Column(nullable = false)
    private int gstRateBp;

    @Column(name = "is_returnable", nullable = false)
    private boolean returnable;

    @Column(nullable = false)
    private int weightGrams;

    @Column(nullable = false)
    private long mrpPaise;

    @Column(nullable = false)
    private long unitPricePaise;

    @Column(nullable = false)
    private int qty;

    @Column(nullable = false)
    private long lineTotalPaise;

    @Column(nullable = false)
    private long discountPaise;

    @Column(nullable = false)
    private long taxablePaise;

    @Column(nullable = false)
    private long cgstPaise;

    @Column(nullable = false)
    private long sgstPaise;

    @Column(nullable = false)
    private long igstPaise;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
