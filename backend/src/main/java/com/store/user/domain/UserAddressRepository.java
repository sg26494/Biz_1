package com.store.user.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    @Query("select a from UserAddress a where a.userId = :userId and a.deletedAt is null order by a.defaultAddress desc, a.id desc")
    List<UserAddress> findActiveByUser(@Param("userId") long userId);

    @Query("select a from UserAddress a where a.id = :id and a.userId = :userId and a.deletedAt is null")
    Optional<UserAddress> findActive(@Param("id") long id, @Param("userId") long userId);

    @Modifying
    @Query("update UserAddress a set a.defaultAddress = false where a.userId = :userId and a.defaultAddress = true")
    void clearDefault(@Param("userId") long userId);
}
