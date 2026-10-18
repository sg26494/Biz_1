package com.store.catalog.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Category> findAllByOrderBySortOrderAscNameAsc();

    List<Category> findByActiveTrueOrderBySortOrderAscNameAsc();
}
