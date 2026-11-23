package com.swiftdrop.notification.security;

public final class JwtTokenValidationException extends RuntimeException {
    public JwtTokenValidationException() {
        super("Bearer token is invalid");
    }
}
