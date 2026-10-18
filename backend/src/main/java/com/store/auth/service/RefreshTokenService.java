package com.store.auth.service;

import com.store.auth.domain.RefreshToken;
import com.store.auth.domain.RefreshTokenRepository;
import com.store.common.config.AppProperties;
import com.store.common.error.ApiException;
import com.store.common.util.Hashing;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens (~30 days), stored hashed, rotated on every use.
 * Reusing an already-rotated token means it leaked: revoke every session of that user.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    public record Rotation(long userId, String newToken) {}

    private final RefreshTokenRepository repo;
    private final AppProperties.Security cfg;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repo, AppProperties props, Clock clock) {
        this.repo = repo;
        this.cfg = props.security();
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String issue(long userId, UUID familyId, String userAgent, String ip) {
        String raw = Hashing.randomUrlToken(32);
        RefreshToken t = new RefreshToken();
        t.setUserId(userId);
        t.setTokenHash(Hashing.sha256Hex(raw));
        t.setFamilyId(familyId == null ? UUID.randomUUID() : familyId);
        t.setExpiresAt(clock.instant().plus(cfg.refreshTokenTtl()));
        t.setUserAgent(truncate(userAgent, 300));
        t.setIp(ip);
        repo.save(t);
        return raw;
    }

    /** noRollbackFor: a detected reuse must still commit the mass revocation before we answer 401. */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String raw, String userAgent, String ip) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.unauthorized("REFRESH_INVALID", "Session expired, please log in again");
        }
        Instant now = clock.instant();
        RefreshToken current = repo.findByHashForUpdate(Hashing.sha256Hex(raw))
                .orElseThrow(() -> ApiException.unauthorized("REFRESH_INVALID", "Session expired, please log in again"));
        if (current.getRevokedAt() != null) {
            if (current.getReplacedBy() != null) {
                log.warn("Refresh token reuse detected for user {}; revoking all sessions", current.getUserId());
                repo.revokeAllForUser(current.getUserId(), now);
            }
            throw ApiException.unauthorized("REFRESH_INVALID", "Session expired, please log in again");
        }
        if (current.getExpiresAt().isBefore(now)) {
            throw ApiException.unauthorized("REFRESH_INVALID", "Session expired, please log in again");
        }
        String next = issue(current.getUserId(), current.getFamilyId(), userAgent, ip);
        RefreshToken nextRow = repo.findByHashForUpdate(Hashing.sha256Hex(next)).orElseThrow();
        current.setRevokedAt(now);
        current.setReplacedBy(nextRow.getId());
        return new Rotation(current.getUserId(), next);
    }

    @Transactional
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        repo.findByHashForUpdate(Hashing.sha256Hex(raw)).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(clock.instant());
            }
        });
    }

    @Transactional
    public void revokeAll(long userId) {
        repo.revokeAllForUser(userId, clock.instant());
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
