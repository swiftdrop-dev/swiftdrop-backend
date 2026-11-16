package com.swiftdrop.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/** Validates the existing Auth Service HS-signed JWT contract. */
public final class JwtTokenValidator {

    private static final Set<String> ALLOWED_ROLES = Set.of("customer", "merchant", "courier", "dispatcher", "admin");
    private final SecretKey verificationKey;

    public JwtTokenValidator(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured for API Gateway");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes of key material");
        }
        this.verificationKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public AuthenticatedJwtPrincipal validate(String compactToken) {
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
                    || issuedAt == null
                    || issuedAt.after(now)
                    || expiration == null
                    || !expiration.after(now)
                    || !expiration.after(issuedAt)) {
                throw new IllegalArgumentException("JWT claims are invalid");
            }

            return new AuthenticatedJwtPrincipal(subject, (String) roleClaim);
        } catch (RuntimeException exception) {
            throw new JwtTokenValidationException();
        }
    }
}
