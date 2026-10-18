package com.store.promo.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Query("select c from Coupon c where upper(c.code) = upper(:code)")
    Optional<Coupon> findByCode(@Param("code") String code);

    /** Locks the coupon row so concurrent checkouts cannot exceed usage limits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where upper(c.code) = upper(:code)")
    Optional<Coupon> findByCodeForUpdate(@Param("code") String code);
}
