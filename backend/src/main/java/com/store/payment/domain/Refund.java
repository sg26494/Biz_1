package com.store.payment.domain;

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
@Table(name = "refunds")
public class Refund extends Timestamped {

    public enum Status { PENDING, PROCESSED, FAILED }

    public enum Source { ORDER_CANCEL, RETURN, LATE_CAPTURE, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    private String gatewayRefundId;

    /** Unique; sent to Razorpay so a retried call can find an existing refund instead of creating another. */
    @Column(nullable = false)
    private String receipt;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    private Long sourceRefId;

    private Long initiatedBy;

    private String error;

    private Instant processedAt;
}
