package com.store.auth.service;

import com.store.common.config.AppProperties;
import com.store.common.error.ApiException;
import com.store.common.util.Db;
import com.store.common.util.Hashing;
import com.store.common.web.RateLimiter;
import com.store.notification.NotificationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Email OTP: 6 digits, stored hashed, 10 min expiry, max 5 attempts, 60 s resend cooldown, rate limits. */
@Service
public class OtpService {

    static final Duration EXPIRY = Duration.ofMinutes(10);
    static final Duration COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;

    private record OtpRow(long id, String codeHash, int attempts, OffsetDateTime expiresAt) {}

    private final JdbcClient jdbc;
    private final NotificationService notifications;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final String pepper;

    public OtpService(JdbcClient jdbc, NotificationService notifications, RateLimiter rateLimiter, Clock clock,
                      AppProperties props) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.pepper = props.security().otpPepper();
    }

    @Transactional
    public void request(String rawEmail, String ip) {
        String email = normalise(rawEmail);
        rateLimiter.check("otp-req-email:" + email, 5, Duration.ofHours(1));
        rateLimiter.check("otp-req-ip:" + ip, 20, Duration.ofHours(1));

        Optional<OffsetDateTime> last = jdbc.sql("""
                SELECT created_at FROM otp_requests WHERE lower(email) = :email ORDER BY created_at DESC LIMIT 1
                """).param("email", email).query(OffsetDateTime.class).optional();
        Instant now = clock.instant();
        if (last.isPresent() && last.get().toInstant().plus(COOLDOWN).isAfter(now)) {
            long wait = Duration.between(now, last.get().toInstant().plus(COOLDOWN)).toSeconds() + 1;
            throw ApiException.tooManyRequests(wait);
        }

        String code = Hashing.randomDigits(6);
        jdbc.sql("""
                INSERT INTO otp_requests (email, code_hash, expires_at, ip) VALUES (:email, :hash, :exp, :ip)
                """)
                .param("email", email)
                .param("hash", hash(email, code))
                .param("exp", Db.ts(now.plus(EXPIRY)))
                .param("ip", ip)
                .update();
        try {
            notifications.send("otp", email, "Your login code: " + code,
                    Map.of("code", code, "minutes", EXPIRY.toMinutes()), null, null);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_FAILED",
                    "We couldn't send the code right now, please try again");
        }
    }

    /** Returns the normalised email on success; throws otherwise. */
    @Transactional(noRollbackFor = ApiException.class)
    public String verify(String rawEmail, String code, String ip) {
        String email = normalise(rawEmail);
        rateLimiter.check("otp-verify-ip:" + ip, 30, Duration.ofMinutes(10));
        OtpRow row = jdbc.sql("""
                SELECT id, code_hash, attempts, expires_at FROM otp_requests
                WHERE lower(email) = :email AND consumed_at IS NULL
                ORDER BY created_at DESC LIMIT 1
                FOR UPDATE
                """).param("email", email).query(OtpRow.class).optional()
                .orElseThrow(() -> ApiException.badRequest("OTP_INVALID", "Code is invalid or expired"));

        if (row.expiresAt().toInstant().isBefore(clock.instant())) {
            throw ApiException.badRequest("OTP_EXPIRED", "Code expired, please request a new one");
        }
        if (row.attempts() >= MAX_ATTEMPTS) {
            throw ApiException.badRequest("OTP_LOCKED", "Too many wrong attempts, please request a new code");
        }
        if (code == null || !Hashing.constantTimeEquals(row.codeHash(), hash(email, code.trim()))) {
            jdbc.sql("UPDATE otp_requests SET attempts = attempts + 1 WHERE id = :id").param("id", row.id()).update();
            int left = MAX_ATTEMPTS - row.attempts() - 1;
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID", "Incorrect code",
                    Map.of("attemptsLeft", Math.max(left, 0)));
        }
        jdbc.sql("UPDATE otp_requests SET consumed_at = now() WHERE id = :id").param("id", row.id()).update();
        return email;
    }

    private String hash(String email, String code) {
        return Hashing.sha256Hex(email + "|" + code + "|" + pepper);
    }

    static String normalise(String email) {
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || email.length() > 320) {
            throw ApiException.badRequest("EMAIL_INVALID", "Enter a valid email address");
        }
        return email.trim().toLowerCase();
    }
}
