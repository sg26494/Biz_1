package com.store.user.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    Optional<User> findByPhone(String phone);

    List<User> findByRole(Role role);

    long countByRole(Role role);

    /** Pass "" for no filter (null params confuse Postgres type inference). */
    @Query("""
            select u from User u
            where (:q = '' or lower(u.email) like lower(concat('%', :q, '%'))
                   or lower(u.name) like lower(concat('%', :q, '%'))
                   or u.phone like concat('%', :q, '%'))
            order by u.createdAt desc
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);
}
