package com.store.auth.security;

import com.store.common.error.ApiException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<AuthUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthUser require() {
        return get().orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Please log in"));
    }

    public static long id() {
        return require().id();
    }

    public static Long idOrNull() {
        return get().map(AuthUser::id).orElse(null);
    }
}
