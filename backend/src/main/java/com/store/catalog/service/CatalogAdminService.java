package com.store.catalog.service;

import com.store.catalog.domain.Category;
import com.store.catalog.domain.CategoryRepository;
import com.store.catalog.domain.Product;
import com.store.catalog.domain.ProductImage;
import com.store.catalog.domain.ProductImageRepository;
import com.store.catalog.domain.ProductRepository;
import com.store.catalog.domain.ProductVariant;
import com.store.catalog.domain.ProductVariantRepository;
import com.store.catalog.dto.CatalogDtos.CategoryRequest;
import com.store.catalog.dto.CatalogDtos.CategoryResponse;
import com.store.catalog.dto.CatalogDtos.ImageAttachRequest;
import com.store.catalog.dto.CatalogDtos.ImageView;
import com.store.catalog.dto.CatalogDtos.PresignRequest;
import com.store.catalog.dto.CatalogDtos.PresignResponse;
import com.store.catalog.dto.CatalogDtos.ProductAdminRow;
import com.store.catalog.dto.CatalogDtos.ProductAdminView;
import com.store.catalog.dto.CatalogDtos.ProductRequest;
import com.store.catalog.dto.CatalogDtos.VariantAdminView;
import com.store.catalog.dto.CatalogDtos.VariantRequest;
import com.store.common.audit.AuditService;
import com.store.common.error.ApiException;
import com.store.common.outbox.EventTypes;
import com.store.common.outbox.OutboxService;
import com.store.common.settings.SettingsService;
import com.store.common.web.PageResponse;
import com.store.inventory.service.InventoryService;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin catalog writes. Every write is audited and triggers storefront revalidation via the outbox. */
@Service
public class CatalogAdminService {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final InventoryService inventory;
    private final StorageService storage;
    private final AuditService audit;
    private final OutboxService outbox;
    private final SettingsService settings;
    private final JdbcClient jdbc;

    public CatalogAdminService(CategoryRepository categories, ProductRepository products,
                               ProductVariantRepository variants, ProductImageRepository images,
                               InventoryService inventory, StorageService storage, AuditService audit,
                               OutboxService outbox, SettingsService settings, JdbcClient jdbc) {
        this.categories = categories;
        this.products = products;
        this.variants = variants;
        this.images = images;
        this.inventory = inventory;
        this.storage = storage;
        this.audit = audit;
        this.outbox = outbox;
        this.settings = settings;
        this.jdbc = jdbc;
    }

    // ---------- categories ----------

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categories.findAllByOrderBySortOrderAscNameAsc().stream().map(CategoryResponse::of).toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest req, long actor) {
        Category c = new Category();
        applyCategory(c, req);
        c.setSlug(uniqueCategorySlug(req.slug() != null ? req.slug() : slugify(req.name()), null));
        categories.saveAndFlush(c);
        audit.record(actor, "CATEGORY_CREATED", "category", c.getId(), null, CategoryResponse.of(c), null);
        revalidate(List.of("/", "/c/" + c.getSlug()));
        return CategoryResponse.of(c);
    }

    @Transactional
    public CategoryResponse updateCategory(long id, CategoryRequest req, long actor) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        CategoryResponse before = CategoryResponse.of(c);
        applyCategory(c, req);
        if (req.slug() != null && !req.slug().equals(c.getSlug())) {
            c.setSlug(uniqueCategorySlug(req.slug(), id));
        }
        if (req.parentId() != null && req.parentId() == id) {
            throw ApiException.badRequest("INVALID_PARENT", "A category cannot be its own parent");
        }
        categories.flush();
        audit.record(actor, "CATEGORY_UPDATED", "category", id, before, CategoryResponse.of(c), null);
        revalidate(List.of("/", "/c/" + before.slug(), "/c/" + c.getSlug()));
        return CategoryResponse.of(c);
    }

    @Transactional
    public void deleteCategory(long id, long actor) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        if (products.countByCategoryId(id) > 0) {
            throw ApiException.conflict("CATEGORY_IN_USE", "Move or archive the products in this category first");
        }
        audit.record(actor, "CATEGORY_DELETED", "category", id, CategoryResponse.of(c), null, null);
        categories.delete(c);
        revalidate(List.of("/"));
    }

    private void applyCategory(Category c, CategoryRequest req) {
        if (req.parentId() != null && categories.findById(req.parentId()).isEmpty()) {
            throw ApiException.badRequest("INVALID_PARENT", "Parent category not found");
        }
        c.setParentId(req.parentId());
        c.setName(req.name().trim());
        c.setDescription(req.description());
        c.setImageUrl(req.imageUrl());
        if (req.attributeDefs() != null) {
            c.setAttributeDefs(new ArrayList<>(req.attributeDefs()));
        }
        if (req.sortOrder() != null) {
            c.setSortOrder(req.sortOrder());
        }
        if (req.active() != null) {
            c.setActive(req.active());
        }
    }

    // ---------- products ----------

    @Transactional(readOnly = true)
    public PageResponse<ProductAdminRow> listProducts(String q, String status, Long categoryId, int page, int size) {
        String search = q == null ? "" : q.trim();
        String st = status == null ? "" : status;
        Map<String, Object> params = new HashMap<>();
        params.put("q", search);
        params.put("st", st);
        params.put("cid", categoryId == null ? 0L : categoryId);
        String where = """
                WHERE (:q = '' OR p.title ILIKE '%' || :q || '%' OR EXISTS (
                         SELECT 1 FROM product_variants x WHERE x.product_id = p.id AND x.sku ILIKE '%' || :q || '%'))
                  AND (:st = '' OR p.status = :st)
                  AND (:cid = 0 OR p.category_id = :cid)
                """;
        long total = jdbc.sql("SELECT COUNT(*) FROM products p " + where).params(params).query(Long.class).single();
        params.put("limit", size);
        params.put("offset", page * size);
        List<ProductAdminRow> rows = jdbc.sql("""
                SELECT p.id, p.title, p.slug, p.status, c.name AS category_name,
                       (SELECT COUNT(*) FROM product_variants v WHERE v.product_id = p.id) AS variant_count,
                       (SELECT MIN(v.price_paise) FROM product_variants v WHERE v.product_id = p.id AND v.is_active)
                         AS min_price_paise,
                       COALESCE((SELECT SUM(i.on_hand - i.reserved) FROM inventory i
                                  JOIN product_variants v ON v.id = i.variant_id WHERE v.product_id = p.id), 0)
                         AS total_available,
                       (SELECT pi.url FROM product_images pi WHERE pi.product_id = p.id
                         ORDER BY pi.sort_order, pi.id LIMIT 1) AS image_url,
                       p.updated_at
                FROM products p JOIN categories c ON c.id = p.category_id
                """ + where + " ORDER BY p.updated_at DESC LIMIT :limit OFFSET :offset")
                .params(params).query(ProductAdminRow.class).list();
        return PageResponse.of(rows, page, size, total);
    }

    @Transactional(readOnly = true)
    public ProductAdminView getProduct(long id) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Product"));
        long wh = inventory.defaultWarehouseId();
        List<VariantAdminView> vs = variants.findByProductIdOrderBySortOrderAscIdAsc(id).stream()
                .map(v -> {
                    var s = inventory.stock(v.getId(), wh);
                    return VariantAdminView.of(v, s.onHand(), s.reserved());
                }).toList();
        List<ImageView> imgs = images.findByProductIdOrderBySortOrderAscIdAsc(id).stream().map(ImageView::of).toList();
        return ProductAdminView.of(p, vs, imgs);
    }

    @Transactional
    public ProductAdminView createProduct(ProductRequest req, long actor) {
        Product p = new Product();
        applyProduct(p, req);
        p.setSlug(uniqueProductSlug(req.slug() != null ? req.slug() : slugify(req.title()), null));
        p.setStatus(Product.Status.DRAFT);
        products.saveAndFlush(p);
        audit.record(actor, "PRODUCT_CREATED", "product", p.getId(), null, req, null);
        return getProduct(p.getId());
    }

    @Transactional
    public ProductAdminView updateProduct(long id, ProductRequest req, long actor) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Product"));
        ProductAdminView before = getProduct(id);
        String oldSlug = p.getSlug();
        applyProduct(p, req);
        if (req.slug() != null && !req.slug().equals(p.getSlug())) {
            p.setSlug(uniqueProductSlug(req.slug(), id));
        }
        products.flush();
        ProductAdminView after = getProduct(id);
        audit.record(actor, "PRODUCT_UPDATED", "product", id, summary(before), summary(after), null);
        revalidateProduct(p, oldSlug);
        return after;
    }

    @Transactional
    public ProductAdminView setStatus(long id, Product.Status status, long actor) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Product"));
        if (status == Product.Status.ACTIVE && variants.countByProductIdAndActiveTrue(id) == 0) {
            throw ApiException.badRequest("NO_VARIANTS", "Add at least one active variant before publishing");
        }
        Product.Status before = p.getStatus();
        p.setStatus(status);
        products.flush();
        audit.record(actor, "PRODUCT_STATUS_CHANGED", "product", id, Map.of("status", before),
                Map.of("status", status), null);
        revalidateProduct(p, p.getSlug());
        return getProduct(id);
    }

    private void applyProduct(Product p, ProductRequest req) {
        if (categories.findById(req.categoryId()).isEmpty()) {
            throw ApiException.badRequest("INVALID_CATEGORY", "Category not found");
        }
        p.setCategoryId(req.categoryId());
        p.setTitle(req.title().trim());
        p.setDescription(req.description() == null ? "" : req.description());
        p.setHsnCode(req.hsnCode());
        p.setGstRateBp(req.gstRateBp());
        p.setReturnable(req.returnable() == null || req.returnable());
        p.setAttributes(req.attributes() == null ? new HashMap<>() : new HashMap<>(req.attributes()));
        p.setSeoTitle(req.seoTitle());
        p.setSeoDescription(req.seoDescription());
    }

    // ---------- variants ----------

    @Transactional
    public VariantAdminView createVariant(VariantRequest req, long actor) {
        Product p = products.findById(req.productId()).orElseThrow(() -> ApiException.notFound("Product"));
        if (variants.existsBySku(req.sku())) {
            throw ApiException.conflict("SKU_EXISTS", "This SKU is already used");
        }
        ProductVariant v = new ProductVariant();
        v.setProductId(p.getId());
        applyVariant(v, req);
        variants.saveAndFlush(v);
        inventory.ensureRow(v.getId());
        audit.record(actor, "VARIANT_CREATED", "variant", v.getId(), null, req, null);
        revalidateProduct(p, p.getSlug());
        return VariantAdminView.of(v, 0, 0);
    }

    @Transactional
    public VariantAdminView updateVariant(long id, VariantRequest req, long actor) {
        ProductVariant v = variants.findById(id).orElseThrow(() -> ApiException.notFound("Variant"));
        if (!v.getProductId().equals(req.productId())) {
            throw ApiException.badRequest("INVALID_PRODUCT", "A variant cannot move to another product");
        }
        if (!v.getSku().equals(req.sku()) && variants.existsBySku(req.sku())) {
            throw ApiException.conflict("SKU_EXISTS", "This SKU is already used");
        }
        Map<String, Object> before = variantSummary(v);
        applyVariant(v, req);
        variants.flush();
        audit.record(actor, "VARIANT_UPDATED", "variant", id, before, variantSummary(v), null);
        Product p = products.findById(v.getProductId()).orElseThrow();
        revalidateProduct(p, p.getSlug());
        var s = inventory.stock(v.getId(), inventory.defaultWarehouseId());
        return VariantAdminView.of(v, s.onHand(), s.reserved());
    }

    private void applyVariant(ProductVariant v, VariantRequest req) {
        if (req.pricePaise() > req.mrpPaise()) {
            throw ApiException.badRequest("INVALID_PRICE", "Selling price cannot be above MRP");
        }
        v.setSku(req.sku().trim());
        v.setTitle(req.title().trim());
        v.setAttributes(req.attributes() == null ? new HashMap<>() : new HashMap<>(req.attributes()));
        v.setMrpPaise(req.mrpPaise());
        v.setPricePaise(req.pricePaise());
        v.setWeightGrams(req.weightGrams());
        v.setLengthCm(req.lengthCm());
        v.setBreadthCm(req.breadthCm());
        v.setHeightCm(req.heightCm());
        v.setActive(req.active() == null || req.active());
        if (req.sortOrder() != null) {
            v.setSortOrder(req.sortOrder());
        }
    }

    private static Map<String, Object> variantSummary(ProductVariant v) {
        Map<String, Object> m = new HashMap<>();
        m.put("sku", v.getSku());
        m.put("title", v.getTitle());
        m.put("mrpPaise", v.getMrpPaise());
        m.put("pricePaise", v.getPricePaise());
        m.put("weightGrams", v.getWeightGrams());
        m.put("active", v.isActive());
        m.put("attributes", v.getAttributes());
        return m;
    }

    private static Map<String, Object> summary(ProductAdminView v) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", v.title());
        m.put("slug", v.slug());
        m.put("status", v.status());
        m.put("categoryId", v.categoryId());
        m.put("hsnCode", v.hsnCode());
        m.put("gstRateBp", v.gstRateBp());
        m.put("returnable", v.returnable());
        return m;
    }

    // ---------- images ----------

    public PresignResponse presignUpload(PresignRequest req) {
        if (!products.existsById(req.productId())) {
            throw ApiException.notFound("Product");
        }
        String ext = switch (req.contentType()) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String key = "products/" + req.productId() + "/" + UUID.randomUUID() + "." + ext;
        StorageService.Presigned p = storage.presignPut(key, req.contentType(), req.contentLength());
        return new PresignResponse(p.url(), key, storage.publicUrl(key), p.headers(), p.expiresAt());
    }

    @Transactional
    public ImageView attachImage(long productId, ImageAttachRequest req, long actor) {
        Product p = products.findById(productId).orElseThrow(() -> ApiException.notFound("Product"));
        if (!req.storageKey().startsWith("products/" + productId + "/") || req.storageKey().contains("..")) {
            throw ApiException.badRequest("INVALID_KEY", "Upload key does not belong to this product");
        }
        if (req.variantId() != null) {
            ProductVariant v = variants.findById(req.variantId()).orElseThrow(() -> ApiException.notFound("Variant"));
            if (!v.getProductId().equals(productId)) {
                throw ApiException.badRequest("INVALID_VARIANT", "Variant belongs to another product");
            }
        }
        ProductImage img = new ProductImage();
        img.setProductId(productId);
        img.setVariantId(req.variantId());
        img.setStorageKey(req.storageKey());
        img.setUrl(storage.publicUrl(req.storageKey()));
        img.setAlt(req.alt() != null ? req.alt() : p.getTitle());
        img.setWidth(req.width());
        img.setHeight(req.height());
        img.setSortOrder(req.sortOrder() != null ? req.sortOrder()
                : images.findByProductIdOrderBySortOrderAscIdAsc(productId).size());
        images.saveAndFlush(img);
        audit.record(actor, "IMAGE_ADDED", "product", productId, null, Map.of("imageId", img.getId(), "key", img.getStorageKey()), null);
        revalidateProduct(p, p.getSlug());
        return ImageView.of(img);
    }

    @Transactional
    public void deleteImage(long productId, long imageId, long actor) {
        ProductImage img = images.findById(imageId).filter(i -> i.getProductId() == productId)
                .orElseThrow(() -> ApiException.notFound("Image"));
        images.delete(img);
        audit.record(actor, "IMAGE_REMOVED", "product", productId, Map.of("imageId", imageId, "key", img.getStorageKey()),
                null, null);
        Product p = products.findById(productId).orElseThrow();
        revalidateProduct(p, p.getSlug());
    }

    @Transactional
    public List<ImageView> reorderImages(long productId, List<Long> imageIds, long actor) {
        List<ProductImage> current = images.findByProductIdOrderBySortOrderAscIdAsc(productId);
        for (ProductImage img : current) {
            int idx = imageIds.indexOf(img.getId());
            img.setSortOrder(idx >= 0 ? idx : imageIds.size() + current.indexOf(img));
        }
        images.flush();
        audit.record(actor, "IMAGES_REORDERED", "product", productId, null, Map.of("order", imageIds), null);
        Product p = products.findById(productId).orElseThrow();
        revalidateProduct(p, p.getSlug());
        return images.findByProductIdOrderBySortOrderAscIdAsc(productId).stream().map(ImageView::of).toList();
    }

    // ---------- helpers ----------

    private void revalidateProduct(Product p, String oldSlug) {
        List<String> paths = new ArrayList<>(List.of("/", "/products/" + p.getSlug()));
        if (oldSlug != null && !oldSlug.equals(p.getSlug())) {
            paths.add("/products/" + oldSlug);
        }
        categories.findById(p.getCategoryId()).ifPresent(c -> paths.add("/c/" + c.getSlug()));
        revalidate(paths);
    }

    private void revalidate(List<String> paths) {
        outbox.enqueue("catalog", "paths", EventTypes.CATALOG_CHANGED, Map.of("paths", paths));
    }

    static String slugify(String input) {
        String s = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (s.isEmpty()) {
            s = "item";
        }
        return s.length() > 200 ? s.substring(0, 200).replaceAll("-$", "") : s;
    }

    private String uniqueProductSlug(String base, Long selfId) {
        String slug = base;
        for (int i = 2; ; i++) {
            var existing = products.findBySlug(slug);
            if (existing.isEmpty() || existing.get().getId().equals(selfId)) {
                return slug;
            }
            slug = base + "-" + i;
        }
    }

    private String uniqueCategorySlug(String base, Long selfId) {
        String slug = base;
        for (int i = 2; ; i++) {
            var existing = categories.findBySlug(slug);
            if (existing.isEmpty() || existing.get().getId().equals(selfId)) {
                return slug;
            }
            slug = base + "-" + i;
        }
    }
}
