package com.swiftdrop.tracking.security;

public final class JwtTokenValidationException extends RuntimeException {
    public JwtTokenValidationException() {
        super("Bearer token is invalid");
    }
}
