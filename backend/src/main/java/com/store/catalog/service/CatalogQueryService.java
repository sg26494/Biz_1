package com.store.catalog.service;

import com.store.catalog.domain.Category;
import com.store.catalog.domain.CategoryRepository;
import com.store.catalog.domain.Product;
import com.store.catalog.domain.ProductImageRepository;
import com.store.catalog.domain.ProductRepository;
import com.store.catalog.domain.ProductVariant;
import com.store.catalog.domain.ProductVariantRepository;
import com.store.catalog.dto.CatalogDtos.CategoryDetail;
import com.store.catalog.dto.CatalogDtos.CategoryResponse;
import com.store.catalog.dto.CatalogDtos.FacetValue;
import com.store.catalog.dto.CatalogDtos.ImageView;
import com.store.catalog.dto.CatalogDtos.ProductCard;
import com.store.catalog.dto.CatalogDtos.ProductDetail;
import com.store.catalog.dto.CatalogDtos.ProductListQuery;
import com.store.catalog.dto.CatalogDtos.VariantView;
import com.store.common.error.ApiException;
import com.store.common.web.PageResponse;
import com.store.inventory.service.InventoryService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Storefront reads. Postgres does the work: jsonb attribute filters, tsvector search, availability
 * from inventory. The catalog is small (hundreds of products), so no search engine is needed.
 */
@Service
@Transactional(readOnly = true)
public class CatalogQueryService {

    private static final Pattern ATTR_KEY = Pattern.compile("^[a-z][a-z0-9_]{0,29}$");

    private record Facet(String key, String value, long count) {}

    private record PriceRange(Long minPrice, Long maxPrice) {}

    private final JdbcClient jdbc;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final InventoryService inventory;

    public CatalogQueryService(JdbcClient jdbc, CategoryRepository categories, ProductRepository products,
                               ProductVariantRepository variants, ProductImageRepository images,
                               InventoryService inventory) {
        this.jdbc = jdbc;
        this.categories = categories;
        this.products = products;
        this.variants = variants;
        this.images = images;
        this.inventory = inventory;
    }

    public List<CategoryResponse> listCategories() {
        return categories.findByActiveTrueOrderBySortOrderAscNameAsc().stream().map(CategoryResponse::of).toList();
    }

    public CategoryDetail categoryDetail(String slug) {
        Category c = categories.findBySlug(slug).filter(Category::isActive)
                .orElseThrow(() -> ApiException.notFound("Category"));
        List<Facet> rows = jdbc.sql("""
                SELECT kv.key, kv.value, COUNT(DISTINCT p.id) AS count
                FROM products p
                JOIN product_variants pv ON pv.product_id = p.id AND pv.is_active
                CROSS JOIN LATERAL jsonb_each_text(pv.attributes) kv
                WHERE p.status = 'ACTIVE'
                  AND p.category_id IN (SELECT id FROM categories WHERE id = :cid OR parent_id = :cid)
                GROUP BY kv.key, kv.value
                ORDER BY kv.key, kv.value
                """).param("cid", c.getId()).query(Facet.class).list();
        Map<String, List<FacetValue>> facets = new LinkedHashMap<>();
        for (Facet f : rows) {
            facets.computeIfAbsent(f.key(), k -> new ArrayList<>()).add(new FacetValue(f.value(), f.count()));
        }
        PriceRange range = jdbc.sql("""
                SELECT MIN(pv.price_paise) AS min_price, MAX(pv.price_paise) AS max_price
                FROM products p JOIN product_variants pv ON pv.product_id = p.id AND pv.is_active
                WHERE p.status = 'ACTIVE'
                  AND p.category_id IN (SELECT id FROM categories WHERE id = :cid OR parent_id = :cid)
                """).param("cid", c.getId()).query(PriceRange.class).single();
        return new CategoryDetail(CategoryResponse.of(c), facets, range.minPrice(), range.maxPrice());
    }

    public PageResponse<ProductCard> listProducts(ProductListQuery q) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder variantWhere = new StringBuilder("pv.is_active");
        if (q.minPrice() != null) {
            variantWhere.append(" AND pv.price_paise >= :minPrice");
            params.put("minPrice", q.minPrice());
        }
        if (q.maxPrice() != null) {
            variantWhere.append(" AND pv.price_paise <= :maxPrice");
            params.put("maxPrice", q.maxPrice());
        }
        int i = 0;
        if (q.attributes() != null) {
            for (Map.Entry<String, List<String>> e : q.attributes().entrySet()) {
                if (!ATTR_KEY.matcher(e.getKey()).matches() || e.getValue() == null || e.getValue().isEmpty()) {
                    continue;
                }
                // OR within one attribute (size=Single,Double), AND across attributes.
                variantWhere.append(" AND pv.attributes ->> :ak").append(i).append(" IN (:av").append(i).append(")");
                params.put("ak" + i, e.getKey());
                params.put("av" + i, e.getValue());
                i++;
            }
        }

        StringBuilder productWhere = new StringBuilder("p.status = 'ACTIVE'");
        if (q.category() != null && !q.category().isBlank()) {
            productWhere.append("""
                     AND p.category_id IN (SELECT c2.id FROM categories c2 JOIN categories c1
                         ON c2.id = c1.id OR c2.parent_id = c1.id WHERE c1.slug = :category)""");
            params.put("category", q.category());
        }
        boolean searching = q.q() != null && !q.q().isBlank();
        if (searching) {
            productWhere.append(" AND (p.search_vector @@ websearch_to_tsquery('simple', :q) OR p.title ILIKE :qLike)");
            params.put("q", q.q().trim());
            params.put("qLike", "%" + q.q().trim().replace("%", "").replace("_", "") + "%");
        }
        if (q.inStockOnly()) {
            productWhere.append(" AND v.available > 0");
        }

        String from = """
                FROM products p
                JOIN categories c ON c.id = p.category_id
                JOIN (
                    SELECT pv.product_id,
                           MIN(pv.price_paise) AS min_price,
                           MAX(pv.mrp_paise) AS max_mrp,
                           COALESCE(SUM(GREATEST(i.on_hand - i.reserved, 0)), 0) AS available
                    FROM product_variants pv
                    LEFT JOIN inventory i ON i.variant_id = pv.id
                    WHERE %s
                    GROUP BY pv.product_id
                ) v ON v.product_id = p.id
                """.formatted(variantWhere);
        String where = " WHERE " + productWhere + " ";

        String order = switch (q.sort() == null ? "" : q.sort()) {
            case "price_asc" -> "v.min_price ASC, p.id DESC";
            case "price_desc" -> "v.min_price DESC, p.id DESC";
            case "relevance" -> searching
                    ? "ts_rank(p.search_vector, websearch_to_tsquery('simple', :q)) DESC, p.id DESC"
                    : "p.created_at DESC";
            default -> "(v.available > 0) DESC, p.created_at DESC";
        };

        long total = jdbc.sql("SELECT COUNT(*) " + from + where).params(params).query(Long.class).single();
        params.put("limit", q.size());
        params.put("offset", q.page() * q.size());
        String select = """
                SELECT p.id, p.slug, p.title, c.slug AS category_slug, v.min_price AS price_paise,
                       v.max_mrp AS mrp_paise, (v.available > 0) AS in_stock,
                       img.url AS image_url, img.alt AS image_alt
                """;
        String imageJoin = """
                LEFT JOIN LATERAL (
                    SELECT pi.url, pi.alt FROM product_images pi WHERE pi.product_id = p.id
                    ORDER BY pi.sort_order, pi.id LIMIT 1
                ) img ON TRUE
                """;
        List<ProductCard> items = jdbc.sql(select + from + imageJoin + where
                        + " ORDER BY " + order + " LIMIT :limit OFFSET :offset")
                .params(params)
                .query(ProductCard.class)
                .list();
        return PageResponse.of(items, q.page(), q.size(), total);
    }

    public ProductDetail productDetail(String slug) {
        Product p = products.findBySlug(slug).filter(x -> x.getStatus() == Product.Status.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Product"));
        Category c = categories.findById(p.getCategoryId()).orElseThrow();
        List<ProductVariant> vs = variants.findByProductIdOrderBySortOrderAscIdAsc(p.getId()).stream()
                .filter(ProductVariant::isActive).toList();
        Map<Long, Integer> available = inventory.availableByVariant(vs.stream().map(ProductVariant::getId).toList());
        List<VariantView> variantViews = vs.stream().map(v -> {
            int avail = available.getOrDefault(v.getId(), 0);
            return new VariantView(v.getId(), v.getSku(), v.getTitle(), v.getAttributes(), v.getMrpPaise(),
                    v.getPricePaise(), Math.min(avail, 10), avail > 0);
        }).toList();
        List<ImageView> imageViews = images.findByProductIdOrderBySortOrderAscIdAsc(p.getId()).stream()
                .map(ImageView::of).toList();
        return new ProductDetail(p.getId(), p.getSlug(), p.getTitle(), p.getDescription(), CategoryResponse.of(c),
                p.getAttributes(), p.isReturnable(), p.getSeoTitle(), p.getSeoDescription(), variantViews, imageViews,
                p.getUpdatedAt());
    }

    /** For the sitemap. */
    public record SitemapEntry(String slug, java.time.OffsetDateTime updatedAt) {}

    public List<SitemapEntry> sitemap() {
        return jdbc.sql("SELECT slug, updated_at FROM products WHERE status = 'ACTIVE' ORDER BY id")
                .query(SitemapEntry.class).list();
    }
}
