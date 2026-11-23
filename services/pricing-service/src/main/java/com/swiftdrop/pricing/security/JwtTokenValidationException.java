package com.swiftdrop.pricing.security;

public final class JwtTokenValidationException extends RuntimeException {
    public JwtTokenValidationException() {
        super("Bearer token is invalid");
    }
}
