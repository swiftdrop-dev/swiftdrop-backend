package com.swiftdrop.dispatch.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/** Validates the Auth Service contract: sub=email, role, iat and exp. */
public final class JwtTokenValidator {

    private static final Set<String> ALLOWED_ROLES = Set.of("customer", "merchant", "courier", "dispatcher");
    private final SecretKey verificationKey;

    public JwtTokenValidator(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured for Dispatch Service");
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
            Date now = Date.from(Instant.now());
            if (subject == null || subject.isBlank()
                    || !(roleClaim instanceof String role)
                    || !ALLOWED_ROLES.contains(role)
                    || issuedAt == null || issuedAt.after(now)
                    || expiration == null || !expiration.after(now)
                    || !expiration.after(issuedAt)) {
                throw new IllegalArgumentException("Invalid JWT claims");
            }
            return new UserPrincipal(subject, role);
        } catch (RuntimeException exception) {
            // Never propagate parser details or token content to callers.
            throw new JwtTokenValidationException();
        }
    }
}
