package com.swiftdrop.auth.security;

public record GoogleUserClaims(
        String subject,
        String email,
        boolean emailVerified,
        String name,
        String picture
) {
    public GoogleUserClaims {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Google subject cannot be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google email cannot be blank");
        }
    }
}
