package com.swiftdrop.dispatch.security;

public final class JwtTokenValidationException extends RuntimeException {
    public JwtTokenValidationException() {
        super("Bearer token is invalid");
    }
}
