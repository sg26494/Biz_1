package com.store.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.store.common.config.AppProperties;
import com.store.common.error.ApiException;
import java.util.List;
import org.springframework.stereotype.Component;

/** Verifies Google ID tokens: signature, audience = our client id, issuer, expiry, email verified. */
@Component
public class GoogleTokenVerifier {

    public record GoogleIdentity(String subject, String email, String name) {}

    private final GoogleIdTokenVerifier verifier;
    private final String clientId;

    public GoogleTokenVerifier(AppProperties props) {
        this.clientId = props.google().clientId();
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId == null ? "" : clientId))
                .build();
    }

    public GoogleIdentity verify(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NOT_CONFIGURED",
                    "Google sign-in is not configured");
        }
        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);
        } catch (Exception e) {
            token = null;
        }
        if (token == null) {
            throw ApiException.unauthorized("GOOGLE_TOKEN_INVALID", "Google sign-in failed, please try again");
        }
        GoogleIdToken.Payload p = token.getPayload();
        if (!Boolean.TRUE.equals(p.getEmailVerified())) {
            throw ApiException.unauthorized("EMAIL_NOT_VERIFIED", "Your Google email is not verified");
        }
        Object name = p.get("name");
        return new GoogleIdentity(p.getSubject(), p.getEmail().toLowerCase(), name == null ? null : name.toString());
    }
}
