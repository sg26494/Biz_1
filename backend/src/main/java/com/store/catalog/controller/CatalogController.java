package com.store.catalog.controller;

import com.store.catalog.dto.CatalogDtos.CategoryDetail;
import com.store.catalog.dto.CatalogDtos.CategoryResponse;
import com.store.catalog.dto.CatalogDtos.ProductCard;
import com.store.catalog.dto.CatalogDtos.ProductDetail;
import com.store.catalog.dto.CatalogDtos.ProductListQuery;
import com.store.catalog.service.CatalogQueryService;
import com.store.common.util.IndianStates;
import com.store.common.web.PageResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public catalog. Attribute filters use "f." params, comma-separated for OR:
 * GET /products?category=jaipuri-razai&f.size=Double,Single&f.colour=Indigo&minPrice=100000&inStock=true&sort=price_asc
 */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogQueryService catalog;

    public CatalogController(CatalogQueryService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalog.listCategories();
    }

    @GetMapping("/categories/{slug}")
    public CategoryDetail category(@PathVariable String slug) {
        return catalog.categoryDetail(slug);
    }

    @GetMapping("/products")
    public PageResponse<ProductCard> products(@RequestParam MultiValueMap<String, String> params) {
        return catalog.listProducts(toQuery(params, params.getFirst("q")));
    }

    @GetMapping("/search")
    public PageResponse<ProductCard> search(@RequestParam MultiValueMap<String, String> params) {
        MultiValueMap<String, String> p = params;
        if (p.getFirst("sort") == null) {
            p = new org.springframework.util.LinkedMultiValueMap<>(params);
            p.set("sort", "relevance");
        }
        return catalog.listProducts(toQuery(p, params.getFirst("q")));
    }

    @GetMapping("/products/{slug}")
    public ProductDetail product(@PathVariable String slug) {
        return catalog.productDetail(slug);
    }

    @GetMapping("/meta/sitemap")
    public List<CatalogQueryService.SitemapEntry> sitemap() {
        return catalog.sitemap();
    }

    @GetMapping("/meta/states")
    public List<String> states() {
        return IndianStates.ALL;
    }

    private static ProductListQuery toQuery(MultiValueMap<String, String> params, String q) {
        Map<String, List<String>> attrs = new LinkedHashMap<>();
        params.forEach((k, values) -> {
            if (k.startsWith("f.") && k.length() > 2) {
                List<String> vals = values.stream()
                        .flatMap(v -> Arrays.stream(v.split(",")))
                        .map(String::trim).filter(s -> !s.isEmpty()).limit(20).toList();
                if (!vals.isEmpty()) {
                    attrs.put(k.substring(2), vals);
                }
            }
        });
        int page = parseInt(params.getFirst("page"), 0);
        int size = PageResponse.clampSize(parseInt(params.getFirst("size"), 24), 60);
        return new ProductListQuery(
                params.getFirst("category"),
                q,
                parseLong(params.getFirst("minPrice")),
                parseLong(params.getFirst("maxPrice")),
                attrs,
                "true".equalsIgnoreCase(params.getFirst("inStock")),
                params.getFirst("sort"),
                Math.max(page, 0),
                size);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Long parseLong(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
