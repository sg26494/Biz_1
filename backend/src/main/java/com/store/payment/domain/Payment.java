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

/** One row per Razorpay order. Razorpay allows several attempts on one order until one is captured. */
@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment extends Timestamped {

    public enum Status { CREATED, AUTHORIZED, CAPTURED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String gateway = "RAZORPAY";

    @Column(nullable = false)
    private String gatewayOrderId;

    private String gatewayPaymentId;

    @Column(nullable = false)
    private long amountPaise;

    @Column(nullable = false)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.CREATED;

    private String method;

    private String errorCode;

    private String errorDescription;

    private Instant clientVerifiedAt;

    private Instant capturedAt;
}
