package com.store.auth.controller;

import com.store.auth.dto.AuthDtos.AuthResult;
import com.store.auth.dto.AuthDtos.GoogleLoginRequest;
import com.store.auth.dto.AuthDtos.LoginResponse;
import com.store.auth.dto.AuthDtos.OtpRequest;
import com.store.auth.dto.AuthDtos.OtpVerifyRequest;
import com.store.auth.dto.AuthDtos.SignupCompleteRequest;
import com.store.auth.service.AuthService;
import com.store.auth.service.AuthService.ClientInfo;
import com.store.cart.controller.CartController;
import com.store.common.config.AppProperties;
import com.store.common.web.RequestContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String REFRESH_COOKIE = "rt";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService auth;
    private final AppProperties props;

    public AuthController(AuthService auth, AppProperties props) {
        this.auth = auth;
        this.props = props;
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> google(@Valid @RequestBody GoogleLoginRequest req, HttpServletRequest http) {
        return respond(auth.loginWithGoogle(req.idToken(), client(http)));
    }

    @PostMapping("/otp/request")
    public ResponseEntity<Map<String, Object>> requestOtp(@Valid @RequestBody OtpRequest req, HttpServletRequest http) {
        auth.requestOtp(req.email(), client(http));
        return ResponseEntity.ok(Map.of("sent", true, "resendAfterSeconds", 60));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<LoginResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest req, HttpServletRequest http) {
        return respond(auth.verifyOtp(req.email(), req.code(), client(http)));
    }

    @PostMapping("/signup/complete")
    public ResponseEntity<LoginResponse> completeSignup(@Valid @RequestBody SignupCompleteRequest req,
                                                        HttpServletRequest http) {
        return respond(auth.completeSignup(req, client(http)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String rt,
                                                 HttpServletRequest http) {
        return respond(auth.refresh(rt, client(http)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String rt) {
        auth.logout(rt);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO)).build();
    }

    private ResponseEntity<LoginResponse> respond(AuthResult result) {
        var builder = ResponseEntity.ok();
        if (result.refreshToken() != null) {
            builder.header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), props.security().refreshTokenTtl()));
            // The guest cart was merged into the user's cart; drop the guest session cookie.
            builder.header(HttpHeaders.SET_COOKIE, CartController.clearSessionCookie(props.security().cookieSecure()));
        }
        return builder.body(result.body());
    }

    private String refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.security().cookieSecure())
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }

    private ClientInfo client(HttpServletRequest http) {
        String sid = null;
        if (http.getCookies() != null) {
            for (var c : http.getCookies()) {
                if (CartController.SESSION_COOKIE.equals(c.getName())) {
                    sid = c.getValue();
                }
            }
        }
        return new ClientInfo(RequestContextFilter.clientIp(http), http.getHeader(HttpHeaders.USER_AGENT), sid);
    }
}
