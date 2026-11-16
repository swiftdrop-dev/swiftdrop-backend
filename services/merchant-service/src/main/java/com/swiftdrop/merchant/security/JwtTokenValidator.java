package com.swiftdrop.merchant.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public final class JwtTokenValidator {

    private static final Set<String> ALLOWED_ROLES = Set.of("customer", "merchant", "courier", "dispatcher", "admin");
    private final SecretKey verificationKey;

    public JwtTokenValidator(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured for Merchant Service");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes of key material");
        }
        this.verificationKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public UserPrincipal validate(String compactToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(verificationKey)
                    .build()
                    .parseSignedClaims(compactToken)
                    .getPayload();

            String subject = claims.getSubject();
            Object roleClaim = claims.get("role");
            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();
            if (subject == null || subject.isBlank()
                    || !(roleClaim instanceof String role)
                    || !ALLOWED_ROLES.contains(role)
                    || issuedAt == null
                    || expiration == null
                    || !expiration.after(new Date())) {
                throw new IllegalArgumentException("JWT claims are invalid");
            }

            UUID userId = null;
            String userIdClaim = claims.get("userId", String.class);
            if (userIdClaim != null) {
                try {
                    userId = UUID.fromString(userIdClaim);
                } catch (IllegalArgumentException ignored) { }
            }
            if (userId == null && subject != null) {
                try {
                    userId = UUID.fromString(subject);
                } catch (IllegalArgumentException ignored) { }
            }

            String email = claims.get("email", String.class);
            if (email == null) {
                email = subject;
            }

            return new UserPrincipal(userId, email, (String) roleClaim);
        } catch (RuntimeException exception) {
            throw new JwtTokenValidationException();
        }
    }
}
