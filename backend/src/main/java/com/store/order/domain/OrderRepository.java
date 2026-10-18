package com.store.order.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Row lock: webhook, expiry job, user cancel and admin actions on one order serialise here. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") long id);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
            select o.id from Order o
            where o.status in (com.store.order.domain.OrderStatus.PENDING_PAYMENT,
                               com.store.order.domain.OrderStatus.PAYMENT_FAILED)
              and o.paymentExpiresAt < :now
            order by o.paymentExpiresAt
            """)
    List<Long> findExpiredUnpaid(@Param("now") Instant now, Pageable pageable);
}
