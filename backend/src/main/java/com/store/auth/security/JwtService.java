package com.store.auth.security;

import com.store.common.config.AppProperties;
import com.store.common.error.ApiException;
import com.store.user.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Access tokens (15 min, claims sub + role) and signup tokens (15 min, single use, purpose=signup).
 * Rotation plan: deploy with a new JWT_SECRET; access tokens die within 15 minutes and refresh
 * tokens are opaque DB rows, so users are not logged out.
 */
@Service
public class JwtService {

    private static final String ISSUER = "store-api";
    private static final String TYP = "typ";

    public record SignupClaims(String jti, String email, String name, String provider, String providerUserId,
                               Instant expiresAt) {}

    private final SecretKey key;
    private final AppProperties.Security cfg;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        this.cfg = props.security();
        byte[] bytes = cfg.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.clock = clock;
    }

    public long accessTokenTtlSeconds() {
        return cfg.accessTokenTtl().toSeconds();
    }

    public String issueAccessToken(long userId, Role role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .claim(TYP, "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(cfg.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    public AuthUser parseAccessToken(String token) {
        Claims claims = parse(token);
        if (!"access".equals(claims.get(TYP, String.class))) {
            throw new JwtException("Not an access token");
        }
        return new AuthUser(Long.parseLong(claims.getSubject()), Role.valueOf(claims.get("role", String.class)));
    }

    public String issueSignupToken(String email, String name, String provider, String providerUserId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claim(TYP, "signup")
                .claim("name", name)
                .claim("provider", provider)
                .claim("pid", providerUserId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(cfg.signupTokenTtl())))
                .signWith(key)
                .compact();
    }

    public SignupClaims parseSignupToken(String token) {
        try {
            Claims c = parse(token);
            if (!"signup".equals(c.get(TYP, String.class))) {
                throw new JwtException("Not a signup token");
            }
            return new SignupClaims(c.getId(), c.getSubject(), c.get("name", String.class),
                    c.get("provider", String.class), c.get("pid", String.class), c.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("SIGNUP_TOKEN_INVALID", "Signup session expired, please log in again");
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
