package com.store.content;

import com.store.common.web.RateLimiter;
import com.store.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Homepage "Join our Pink City circle" sign-ups. Admins can export the table for their email tool. */
@RestController
@RequestMapping("/api/v1/newsletter")
public class NewsletterController {

    public record SubscribeRequest(@NotBlank @Email @Size(max = 320) String email) {}

    private final JdbcClient jdbc;
    private final RateLimiter rateLimiter;

    public NewsletterController(JdbcClient jdbc, RateLimiter rateLimiter) {
        this.jdbc = jdbc;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public Map<String, Object> subscribe(@Valid @RequestBody SubscribeRequest req, HttpServletRequest http) {
        rateLimiter.check("newsletter:" + RequestIdFilter.clientIp(http), 10, Duration.ofHours(1));
        jdbc.sql("""
                INSERT INTO newsletter_subscribers (email) VALUES (:e)
                ON CONFLICT ((lower(email))) DO UPDATE SET unsubscribed_at = NULL
                """).param("e", req.email().trim().toLowerCase()).update();
        return Map.of("subscribed", true);
    }
}
