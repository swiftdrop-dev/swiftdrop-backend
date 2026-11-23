package com.swiftdrop.gateway.security;

import java.security.Principal;

/** Identity established only after validating an Auth Service JWT. */
public record AuthenticatedJwtPrincipal(String subject, String role, String email) implements Principal {

    public AuthenticatedJwtPrincipal(String subject, String role) {
        this(subject, role, null);
    }

    @Override
    public String getName() {
        return subject;
    }
}

