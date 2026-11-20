package com.swiftdrop.gateway.security;

/** Generic validation failure that never includes token or key material. */
public final class JwtTokenValidationException extends RuntimeException {

    public JwtTokenValidationException() {
        super("Bearer token is invalid");
    }
}
