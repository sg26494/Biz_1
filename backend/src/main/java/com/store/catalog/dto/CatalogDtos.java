package com.store.catalog.dto;

import com.store.catalog.domain.Category;
import com.store.catalog.domain.Product;
import com.store.catalog.domain.ProductImage;
import com.store.catalog.domain.ProductVariant;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CatalogDtos {

    private CatalogDtos() {}

    // ---------- storefront ----------

    public record CategoryResponse(long id, Long parentId, String name, String slug, String description,
                                   String imageUrl, List<Map<String, Object>> attributeDefs, int sortOrder,
                                   boolean active) {
        public static CategoryResponse of(Category c) {
            return new CategoryResponse(c.getId(), c.getParentId(), c.getName(), c.getSlug(), c.getDescription(),
                    c.getImageUrl(), c.getAttributeDefs(), c.getSortOrder(), c.isActive());
        }
    }

    public record FacetValue(String value, long count) {}

    public record CategoryDetail(CategoryResponse category, Map<String, List<FacetValue>> facets, Long minPricePaise,
                                 Long maxPricePaise) {}

    public record ProductCard(long id, String slug, String title, String categorySlug, long pricePaise,
                              long mrpPaise, boolean inStock, String imageUrl, String imageAlt) {}

    public record ProductListQuery(String category, String q, Long minPrice, Long maxPrice,
                                   Map<String, List<String>> attributes, boolean inStockOnly, String sort, int page,
                                   int size) {}

    public record VariantView(long id, String sku, String title, Map<String, Object> attributes, long mrpPaise,
                              long pricePaise, int available, boolean inStock) {}

    public record ImageView(long id, Long variantId, String url, String alt, Integer width, Integer height) {
        public static ImageView of(ProductImage i) {
            return new ImageView(i.getId(), i.getVariantId(), i.getUrl(), i.getAlt(), i.getWidth(), i.getHeight());
        }
    }

    public record ProductDetail(long id, String slug, String title, String description, CategoryResponse category,
                                Map<String, Object> attributes, boolean returnable, String seoTitle,
                                String seoDescription, List<VariantView> variants, List<ImageView> images,
                                Instant updatedAt) {}

    // ---------- admin ----------

    public record CategoryRequest(
            Long parentId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 140) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "lowercase letters, digits and hyphens") String slug,
            String description,
            String imageUrl,
            List<Map<String, Object>> attributeDefs,
            Integer sortOrder,
            Boolean active) {}

    public record ProductRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 220) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "lowercase letters, digits and hyphens") String slug,
            String description,
            @NotBlank @Pattern(regexp = "^\\d{4,8}$", message = "HSN code is 4-8 digits") String hsnCode,
            @NotNull @Min(0) @Max(2800) Integer gstRateBp,
            Boolean returnable,
            Map<String, Object> attributes,
            @Size(max = 200) String seoTitle,
            @Size(max = 400) String seoDescription) {}

    public record ProductStatusRequest(@NotNull Product.Status status) {}

    public record VariantRequest(
            @NotNull Long productId,
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String sku,
            @NotBlank @Size(max = 120) String title,
            Map<String, Object> attributes,
            @NotNull @Positive Long mrpPaise,
            @NotNull @Positive Long pricePaise,
            @NotNull @Positive Integer weightGrams,
            @NotNull @DecimalMin("0.1") BigDecimal lengthCm,
            @NotNull @DecimalMin("0.1") BigDecimal breadthCm,
            @NotNull @DecimalMin("0.1") BigDecimal heightCm,
            Boolean active,
            Integer sortOrder) {}

    public record VariantAdminView(long id, long productId, String sku, String title, Map<String, Object> attributes,
                                   long mrpPaise, long pricePaise, int weightGrams, BigDecimal lengthCm,
                                   BigDecimal breadthCm, BigDecimal heightCm, boolean active, int sortOrder,
                                   int onHand, int reserved) {
        public static VariantAdminView of(ProductVariant v, int onHand, int reserved) {
            return new VariantAdminView(v.getId(), v.getProductId(), v.getSku(), v.getTitle(), v.getAttributes(),
                    v.getMrpPaise(), v.getPricePaise(), v.getWeightGrams(), v.getLengthCm(), v.getBreadthCm(),
                    v.getHeightCm(), v.isActive(), v.getSortOrder(), onHand, reserved);
        }
    }

    public record ProductAdminView(long id, long categoryId, String title, String slug, String description,
                                   String status, String hsnCode, int gstRateBp, boolean returnable,
                                   Map<String, Object> attributes, String seoTitle, String seoDescription,
                                   List<VariantAdminView> variants, List<ImageView> images, Instant createdAt,
                                   Instant updatedAt) {
        public static ProductAdminView of(Product p, List<VariantAdminView> variants, List<ImageView> images) {
            return new ProductAdminView(p.getId(), p.getCategoryId(), p.getTitle(), p.getSlug(), p.getDescription(),
                    p.getStatus().name(), p.getHsnCode(), p.getGstRateBp(), p.isReturnable(), p.getAttributes(),
                    p.getSeoTitle(), p.getSeoDescription(), variants, images, p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    public record ProductAdminRow(long id, String title, String slug, String status, String categoryName,
                                  long variantCount, Long minPricePaise, long totalAvailable, String imageUrl,
                                  java.time.OffsetDateTime updatedAt) {}

    public record PresignRequest(
            @NotNull Long productId,
            @NotBlank @Pattern(regexp = "^image/(jpeg|png|webp)$", message = "Only JPEG, PNG or WebP") String contentType,
            @NotNull @Positive @Max(10_485_760) Long contentLength) {}

    public record PresignResponse(String uploadUrl, String storageKey, String publicUrl, Map<String, String> headers,
                                  Instant expiresAt) {}

    public record ImageAttachRequest(@NotBlank String storageKey, Long variantId, @Size(max = 200) String alt,
                                     Integer width, Integer height, Integer sortOrder) {}

    public record ImageOrderRequest(@NotNull List<Long> imageIds) {}
}
