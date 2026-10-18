package com.store.payment.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByGatewayRefundId(String gatewayRefundId);

    Optional<Refund> findByReceipt(String receipt);

    List<Refund> findByOrderIdOrderByIdAsc(Long orderId);

    @Query("""
            select coalesce(sum(r.amountPaise), 0) from Refund r
            where r.paymentId = :paymentId and r.status <> com.store.payment.domain.Refund.Status.FAILED
            """)
    long sumNonFailed(@Param("paymentId") long paymentId);

    long countByOrderId(Long orderId);
}
