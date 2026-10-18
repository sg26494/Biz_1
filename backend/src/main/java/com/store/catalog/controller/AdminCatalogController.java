package com.store.catalog.controller;

import com.store.auth.security.CurrentUser;
import com.store.catalog.dto.CatalogDtos.CategoryRequest;
import com.store.catalog.dto.CatalogDtos.CategoryResponse;
import com.store.catalog.dto.CatalogDtos.ImageAttachRequest;
import com.store.catalog.dto.CatalogDtos.ImageOrderRequest;
import com.store.catalog.dto.CatalogDtos.ImageView;
import com.store.catalog.dto.CatalogDtos.PresignRequest;
import com.store.catalog.dto.CatalogDtos.PresignResponse;
import com.store.catalog.dto.CatalogDtos.ProductAdminRow;
import com.store.catalog.dto.CatalogDtos.ProductAdminView;
import com.store.catalog.dto.CatalogDtos.ProductRequest;
import com.store.catalog.dto.CatalogDtos.ProductStatusRequest;
import com.store.catalog.dto.CatalogDtos.VariantAdminView;
import com.store.catalog.dto.CatalogDtos.VariantRequest;
import com.store.catalog.service.CatalogAdminService;
import com.store.common.web.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {

    private final CatalogAdminService catalog;

    public AdminCatalogController(CatalogAdminService catalog) {
        this.catalog = catalog;
    }

    // categories
    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalog.listCategories();
    }

    @PostMapping("/categories")
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest req) {
        return catalog.createCategory(req, CurrentUser.id());
    }

    @PutMapping("/categories/{id}")
    public CategoryResponse updateCategory(@PathVariable long id, @Valid @RequestBody CategoryRequest req) {
        return catalog.updateCategory(id, req, CurrentUser.id());
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable long id) {
        catalog.deleteCategory(id, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    // products
    @GetMapping("/products")
    public PageResponse<ProductAdminRow> products(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Long categoryId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "25") int size) {
        return catalog.listProducts(q, status, categoryId, Math.max(page, 0), PageResponse.clampSize(size, 100));
    }

    @GetMapping("/products/{id}")
    public ProductAdminView product(@PathVariable long id) {
        return catalog.getProduct(id);
    }

    @PostMapping("/products")
    public ProductAdminView create(@Valid @RequestBody ProductRequest req) {
        return catalog.createProduct(req, CurrentUser.id());
    }

    @PutMapping("/products/{id}")
    public ProductAdminView update(@PathVariable long id, @Valid @RequestBody ProductRequest req) {
        return catalog.updateProduct(id, req, CurrentUser.id());
    }

    /** Products are never hard-deleted (orders reference them); archive instead. */
    @PostMapping("/products/{id}/status")
    public ProductAdminView status(@PathVariable long id, @Valid @RequestBody ProductStatusRequest req) {
        return catalog.setStatus(id, req.status(), CurrentUser.id());
    }

    // variants
    @PostMapping("/variants")
    public VariantAdminView createVariant(@Valid @RequestBody VariantRequest req) {
        return catalog.createVariant(req, CurrentUser.id());
    }

    @PutMapping("/variants/{id}")
    public VariantAdminView updateVariant(@PathVariable long id, @Valid @RequestBody VariantRequest req) {
        return catalog.updateVariant(id, req, CurrentUser.id());
    }

    // images
    @PostMapping("/uploads/presign")
    public PresignResponse presign(@Valid @RequestBody PresignRequest req) {
        return catalog.presignUpload(req);
    }

    @PostMapping("/products/{id}/images")
    public ImageView attachImage(@PathVariable long id, @Valid @RequestBody ImageAttachRequest req) {
        return catalog.attachImage(id, req, CurrentUser.id());
    }

    @PutMapping("/products/{id}/images/order")
    public List<ImageView> reorder(@PathVariable long id, @Valid @RequestBody ImageOrderRequest req) {
        return catalog.reorderImages(id, req.imageIds(), CurrentUser.id());
    }

    @DeleteMapping("/products/{id}/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable long id, @PathVariable long imageId) {
        catalog.deleteImage(id, imageId, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }
}
