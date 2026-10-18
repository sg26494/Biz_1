package com.store.auth.security;

import com.store.user.domain.Role;

/** Principal built from the access token: no DB hit per request. */
public record AuthUser(long id, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
