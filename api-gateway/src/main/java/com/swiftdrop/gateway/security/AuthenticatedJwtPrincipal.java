package com.swiftdrop.gateway.security;

import java.security.Principal;

/** Identity established only after validating an Auth Service JWT. */
public record AuthenticatedJwtPrincipal(String subject, String role) implements Principal {

    @Override
    public String getName() {
        return subject;
    }
}
