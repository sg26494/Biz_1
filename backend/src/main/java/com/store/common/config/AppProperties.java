package com.store.common.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicBaseUrl,
        Cors cors,
        Jobs jobs,
        Security security,
        Admin admin,
        Google google,
        Email email,
        Razorpay razorpay,
        Shiprocket shiprocket,
        Storage storage,
        Frontend frontend) {

    public record Cors(List<String> allowedOrigins) {}

    public record Jobs(boolean enabled) {}

    public record Security(
            String jwtSecret,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            Duration signupTokenTtl,
            boolean cookieSecure,
            String otpPepper) {}

    public record Admin(List<String> seedEmails) {}

    public record Google(String clientId) {}

    public record Email(String provider, String from, List<String> adminAlertTo, String resendApiKey) {}

    public record Razorpay(String keyId, String keySecret, String webhookSecret, String baseUrl) {}

    public record Shiprocket(boolean enabled, String email, String password, String baseUrl) {}

    public record Storage(
            String endpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey,
            String publicBaseUrl,
            boolean pathStyle) {}

    public record Frontend(String revalidateUrl, String revalidateSecret) {}
}
