package com.store.content;

import com.store.auth.security.CurrentUser;
import com.store.common.audit.AuditService;
import com.store.common.error.ApiException;
import com.store.common.outbox.EventTypes;
import com.store.common.outbox.OutboxService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Static pages (About, policies: Razorpay KYC needs these live) and homepage banners.
 * Page HTML is written by admins only; the frontend still sanitises it before rendering.
 */
@RestController
@RequestMapping("/api/v1")
public class ContentController {

    public record Page(long id, String slug, String title, String bodyHtml, boolean isPublished,
                       OffsetDateTime updatedAt) {}

    public record PageRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 200_000) String bodyHtml,
            boolean isPublished) {}

    public record Banner(long id, String title, String subtitle, String imageUrl, String linkUrl, String placement,
                         int sortOrder, boolean isActive, OffsetDateTime startsAt, OffsetDateTime endsAt) {}

    public record BannerRequest(@NotBlank @Size(max = 200) String title, @Size(max = 300) String subtitle,
                                @NotBlank String imageUrl, String linkUrl, String placement, Integer sortOrder,
                                Boolean isActive, OffsetDateTime startsAt, OffsetDateTime endsAt) {}

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final OutboxService outbox;

    public ContentController(JdbcClient jdbc, AuditService audit, OutboxService outbox) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.outbox = outbox;
    }

    // ---------- public ----------

    @GetMapping("/pages/{slug}")
    public Page page(@PathVariable String slug) {
        return jdbc.sql("""
                SELECT id, slug, title, body_html, is_published, updated_at FROM cms_pages
                WHERE slug = :s AND is_published
                """).param("s", slug).query(Page.class).optional().orElseThrow(() -> ApiException.notFound("Page"));
    }

    @GetMapping("/banners")
    public List<Banner> banners() {
        return jdbc.sql("""
                SELECT id, title, subtitle, image_url, link_url, placement, sort_order, is_active, starts_at, ends_at
                FROM banners
                WHERE is_active AND (starts_at IS NULL OR starts_at <= now()) AND (ends_at IS NULL OR ends_at > now())
                ORDER BY placement, sort_order, id
                """).query(Banner.class).list();
    }

    // ---------- admin ----------

    @GetMapping("/admin/pages")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Page> adminPages() {
        return jdbc.sql("SELECT id, slug, title, body_html, is_published, updated_at FROM cms_pages ORDER BY slug")
                .query(Page.class).list();
    }

    @PostMapping("/admin/pages")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Page createPage(@Valid @RequestBody PageRequest req) {
        long id = jdbc.sql("""
                INSERT INTO cms_pages (slug, title, body_html, is_published) VALUES (:s, :t, :b, :p) RETURNING id
                """).param("s", req.slug()).param("t", req.title()).param("b", req.bodyHtml() == null ? "" : req.bodyHtml())
                .param("p", req.isPublished()).query(Long.class).single();
        audit.record(CurrentUser.id(), "PAGE_CREATED", "cms_page", id, null, Map.of("slug", req.slug()), null);
        revalidate("/pages/" + req.slug());
        return pageById(id);
    }

    @PutMapping("/admin/pages/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Page updatePage(@PathVariable long id, @Valid @RequestBody PageRequest req) {
        Page before = pageById(id);
        jdbc.sql("""
                UPDATE cms_pages SET slug = :s, title = :t, body_html = :b, is_published = :p, updated_at = now()
                WHERE id = :id
                """).param("s", req.slug()).param("t", req.title()).param("b", req.bodyHtml() == null ? "" : req.bodyHtml())
                .param("p", req.isPublished()).param("id", id).update();
        audit.record(CurrentUser.id(), "PAGE_UPDATED", "cms_page", id,
                Map.of("slug", before.slug(), "title", before.title(), "published", before.isPublished()),
                Map.of("slug", req.slug(), "title", req.title(), "published", req.isPublished()), null);
        revalidate("/pages/" + before.slug());
        revalidate("/pages/" + req.slug());
        return pageById(id);
    }

    @GetMapping("/admin/banners")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Banner> adminBanners() {
        return jdbc.sql("""
                SELECT id, title, subtitle, image_url, link_url, placement, sort_order, is_active, starts_at, ends_at
                FROM banners ORDER BY placement, sort_order, id
                """).query(Banner.class).list();
    }

    @PostMapping("/admin/banners")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Banner createBanner(@Valid @RequestBody BannerRequest req) {
        long id = jdbc.sql("""
                INSERT INTO banners (title, subtitle, image_url, link_url, placement, sort_order, is_active, starts_at, ends_at)
                VALUES (:t, :st, :img, :link, :pl, :so, :act, :sa, :ea) RETURNING id
                """).params(bannerParams(req)).query(Long.class).single();
        audit.record(CurrentUser.id(), "BANNER_CREATED", "banner", id, null, req, null);
        revalidate("/");
        return bannerById(id);
    }

    @PutMapping("/admin/banners/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Banner updateBanner(@PathVariable long id, @Valid @RequestBody BannerRequest req) {
        Banner before = bannerById(id);
        Map<String, Object> p = new java.util.HashMap<>(bannerParams(req));
        p.put("id", id);
        jdbc.sql("""
                UPDATE banners SET title = :t, subtitle = :st, image_url = :img, link_url = :link, placement = :pl,
                       sort_order = :so, is_active = :act, starts_at = :sa, ends_at = :ea, updated_at = now()
                WHERE id = :id
                """).params(p).update();
        audit.record(CurrentUser.id(), "BANNER_UPDATED", "banner", id, before, req, null);
        revalidate("/");
        return bannerById(id);
    }

    @DeleteMapping("/admin/banners/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> deleteBanner(@PathVariable long id) {
        Banner before = bannerById(id);
        jdbc.sql("DELETE FROM banners WHERE id = :id").param("id", id).update();
        audit.record(CurrentUser.id(), "BANNER_DELETED", "banner", id, before, null, null);
        revalidate("/");
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> bannerParams(BannerRequest req) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("t", req.title());
        m.put("st", req.subtitle());
        m.put("img", req.imageUrl());
        m.put("link", req.linkUrl());
        m.put("pl", req.placement() == null ? "HOME_HERO" : req.placement());
        m.put("so", req.sortOrder() == null ? 0 : req.sortOrder());
        m.put("act", req.isActive() == null || req.isActive());
        m.put("sa", req.startsAt());
        m.put("ea", req.endsAt());
        return m;
    }

    private Page pageById(long id) {
        return jdbc.sql("SELECT id, slug, title, body_html, is_published, updated_at FROM cms_pages WHERE id = :id")
                .param("id", id).query(Page.class).optional().orElseThrow(() -> ApiException.notFound("Page"));
    }

    private Banner bannerById(long id) {
        return jdbc.sql("""
                SELECT id, title, subtitle, image_url, link_url, placement, sort_order, is_active, starts_at, ends_at
                FROM banners WHERE id = :id
                """).param("id", id).query(Banner.class).optional().orElseThrow(() -> ApiException.notFound("Banner"));
    }

    private void revalidate(String path) {
        outbox.enqueue("content", path, EventTypes.CATALOG_CHANGED, Map.of("paths", List.of(path)));
    }
}
