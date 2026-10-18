package com.store.auth.service;

import com.store.auth.domain.AuthIdentity;
import com.store.auth.domain.AuthIdentityRepository;
import com.store.auth.dto.AuthDtos.AuthResult;
import com.store.auth.dto.AuthDtos.LoginResponse;
import com.store.auth.dto.AuthDtos.SignupCompleteRequest;
import com.store.auth.security.JwtService;
import com.store.auth.security.JwtService.SignupClaims;
import com.store.cart.service.CartService;
import com.store.common.error.ApiException;
import com.store.common.outbox.EventTypes;
import com.store.common.outbox.OutboxService;
import com.store.common.util.Db;
import com.store.common.web.RateLimiter;
import com.store.user.domain.Role;
import com.store.user.domain.User;
import com.store.user.domain.UserRepository;
import com.store.user.dto.UserDtos.MeResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-step signup: verifying Google/OTP never creates a user. A new person gets SIGNUP_REQUIRED plus a
 * short-lived signup token; POST /auth/signup/complete then creates users + auth_identities together.
 */
@Service
public class AuthService {

    public record ClientInfo(String ip, String userAgent, String guestSessionId) {}

    private final GoogleTokenVerifier google;
    private final OtpService otp;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final UserRepository users;
    private final AuthIdentityRepository identities;
    private final OutboxService outbox;
    private final CartService cartService;
    private final RateLimiter rateLimiter;
    private final JdbcClient jdbc;
    private final Clock clock;

    public AuthService(GoogleTokenVerifier google, OtpService otp, JwtService jwt, RefreshTokenService refreshTokens,
                       UserRepository users, AuthIdentityRepository identities, OutboxService outbox,
                       CartService cartService, RateLimiter rateLimiter, JdbcClient jdbc, Clock clock) {
        this.google = google;
        this.otp = otp;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.identities = identities;
        this.outbox = outbox;
        this.cartService = cartService;
        this.rateLimiter = rateLimiter;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public AuthResult loginWithGoogle(String idToken, ClientInfo client) {
        rateLimiter.check("login-ip:" + client.ip(), 30, Duration.ofMinutes(10));
        GoogleTokenVerifier.GoogleIdentity gid = google.verify(idToken);
        return loginOrSignup(AuthIdentity.GOOGLE, gid.subject(), gid.email(), gid.name(), client);
    }

    public void requestOtp(String email, ClientInfo client) {
        otp.request(email, client.ip());
    }

    /** noRollbackFor: a wrong code must still commit the attempt counter. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult verifyOtp(String email, String code, ClientInfo client) {
        String verified = otp.verify(email, code, client.ip());
        return loginOrSignup(AuthIdentity.EMAIL_OTP, verified, verified, null, client);
    }

    private AuthResult loginOrSignup(String provider, String providerUserId, String email, String name,
                                     ClientInfo client) {
        Optional<AuthIdentity> identity = identities.findByProviderAndProviderUserId(provider, providerUserId);
        Optional<User> user = identity.flatMap(i -> users.findById(i.getUserId()));
        if (user.isEmpty()) {
            // Same verified email already has an account (other login method, or an admin added by email): link it.
            user = users.findByEmailIgnoreCase(email);
            user.ifPresent(u -> linkIdentity(u.getId(), provider, providerUserId, email));
        }
        if (user.isEmpty()) {
            String token = jwt.issueSignupToken(email, name, provider, providerUserId);
            return new AuthResult(LoginResponse.signupRequired(token, email, name), null);
        }
        return issueSession(user.get(), client);
    }

    @Transactional
    public AuthResult completeSignup(SignupCompleteRequest req, ClientInfo client) {
        SignupClaims claims = jwt.parseSignupToken(req.signupToken());
        int consumed = jdbc.sql("""
                INSERT INTO used_signup_tokens (jti, expires_at) VALUES (:jti, :exp) ON CONFLICT DO NOTHING
                """).param("jti", claims.jti()).param("exp", Db.ts(claims.expiresAt())).update();
        if (consumed == 0) {
            throw ApiException.unauthorized("SIGNUP_TOKEN_USED", "This signup link was already used, please log in");
        }
        if (users.findByEmailIgnoreCase(claims.email()).isPresent()) {
            throw ApiException.conflict("ACCOUNT_EXISTS", "An account with this email already exists, please log in");
        }
        if (users.findByPhone(req.phone()).isPresent()) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "PHONE_TAKEN",
                    "This mobile number is already registered", Map.of("field", "phone"));
        }
        User u = new User();
        u.setEmail(claims.email());
        u.setName(req.name().trim());
        u.setPhone(req.phone());
        u.setRole(Role.USER);
        u.setTermsAcceptedAt(clock.instant());
        users.saveAndFlush(u);
        linkIdentity(u.getId(), claims.provider(), claims.providerUserId(), claims.email());
        outbox.enqueue("user", u.getId(), EventTypes.USER_SIGNED_UP,
                Map.of("userId", u.getId(), "email", u.getEmail(), "name", u.getName()));
        return issueSession(u, client);
    }

    /** noRollbackFor: token-reuse revocation and block revocation must commit even though we answer 401/403. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken, ClientInfo client) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken, client.userAgent(), client.ip());
        User u = users.findById(rotation.userId())
                .orElseThrow(() -> ApiException.unauthorized("REFRESH_INVALID", "Session expired"));
        if (u.isBlocked()) {
            refreshTokens.revokeAll(u.getId());
            throw ApiException.forbidden("ACCOUNT_BLOCKED", "This account is blocked. Please contact support.");
        }
        String access = jwt.issueAccessToken(u.getId(), u.getRole());
        return new AuthResult(LoginResponse.loggedIn(access, jwt.accessTokenTtlSeconds(), MeResponse.of(u)),
                rotation.newToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    private void linkIdentity(long userId, String provider, String providerUserId, String email) {
        if (identities.findByProviderAndProviderUserId(provider, providerUserId).isPresent()) {
            return;
        }
        AuthIdentity ai = new AuthIdentity();
        ai.setUserId(userId);
        ai.setProvider(provider);
        ai.setProviderUserId(providerUserId);
        ai.setEmail(email);
        identities.save(ai);
    }

    private AuthResult issueSession(User u, ClientInfo client) {
        if (u.isBlocked()) {
            throw ApiException.forbidden("ACCOUNT_BLOCKED", "This account is blocked. Please contact support.");
        }
        u.setLastLoginAt(clock.instant());
        if (client.guestSessionId() != null) {
            cartService.mergeGuestCart(client.guestSessionId(), u.getId());
        }
        String access = jwt.issueAccessToken(u.getId(), u.getRole());
        String refresh = refreshTokens.issue(u.getId(), null, client.userAgent(), client.ip());
        return new AuthResult(LoginResponse.loggedIn(access, jwt.accessTokenTtlSeconds(), MeResponse.of(u)), refresh);
    }
}
