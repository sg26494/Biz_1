package com.store.order.domain;

import com.store.common.domain.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class Order extends Timestamped {

    public static final String CANCEL_USER = "USER";
    public static final String CANCEL_ADMIN = "ADMIN";
    public static final String CANCEL_PAYMENT_TIMEOUT = "PAYMENT_TIMEOUT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String orderNumber;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long warehouseId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    /** Snapshot of the address at purchase time. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> shippingAddress;

    @Column(nullable = false)
    private long subtotalPaise;

    @Column(nullable = false)
    private long discountPaise;

    @Column(nullable = false)
    private long shippingPaise;

    @Column(nullable = false)
    private long totalPaise;

    @Column(nullable = false)
    private long taxablePaise;

    @Column(nullable = false)
    private long cgstPaise;

    @Column(nullable = false)
    private long sgstPaise;

    @Column(nullable = false)
    private long igstPaise;

    @Column(name = "is_intra_state", nullable = false)
    private boolean intraState;

    private String couponCode;

    private Instant paymentExpiresAt;

    private Instant paidAt;

    private Instant packedAt;

    private Instant shippedAt;

    private Instant deliveredAt;

    private Instant cancelledAt;

    private String cancelReason;

    private String cancelCode;

    @Version
    private long version;
}
