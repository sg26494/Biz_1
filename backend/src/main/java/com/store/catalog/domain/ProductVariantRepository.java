package com.store.catalog.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    List<ProductVariant> findByIdIn(Collection<Long> ids);

    boolean existsBySku(String sku);

    long countByProductIdAndActiveTrue(Long productId);
}
