package com.swiftdrop.merchant.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenValidatorTest {

    private static final String SECRET = "test-only-merchant-jwt-key-material-at-least-32-bytes";
    private final JwtTokenValidator validator = new JwtTokenValidator(SECRET);

    @Test
    void validatesCurrentAuthServiceTokenContract() {
        UserPrincipal principal = validator.validate(token("merchant@example.test", "merchant",
                Instant.now().plusSeconds(120), SECRET));

        assertEquals("merchant@example.test", principal.getName());
        assertEquals("merchant", principal.getRole());
    }

    @Test
    void rejectsInvalidSignature() {
        assertInvalid(token("merchant@example.test", "merchant", Instant.now().plusSeconds(120),
                SECRET.replace("merchant", "MerchanT")));
    }

    @Test
    void rejectsExpiredToken() {
        assertInvalid(token("merchant@example.test", "merchant", Instant.now().minusSeconds(1), SECRET));
    }

    @Test
    void rejectsMalformedToken() {
        assertInvalid("not-a-jwt");
    }

    @Test
    void rejectsMissingSubject() {
        assertInvalid(token(null, "merchant", Instant.now().plusSeconds(120), SECRET));
    }

    @Test
    void rejectsMissingRole() {
        assertInvalid(token("merchant@example.test", null, Instant.now().plusSeconds(120), SECRET));
    }

    @Test
    void acceptsAdminRole() {
        UserPrincipal principal = validator.validate(token("admin@example.test", "admin",
                Instant.now().plusSeconds(120), SECRET));
        assertEquals("admin@example.test", principal.getName());
        assertEquals("admin", principal.getRole());
    }

    @Test
    void rejectsInvalidOrUnrecognizedRole() {
        assertInvalid(token("merchant@example.test", "superadmin", Instant.now().plusSeconds(120), SECRET));
    }

    @Test
    void rejectsUppercaseRole() {
        assertInvalid(token("merchant@example.test", "MERCHANT", Instant.now().plusSeconds(120), SECRET));
    }

    @Test
    void rejectsMissingExpiration() {
        var key = key(SECRET);
        String compact = Jwts.builder()
                .subject("merchant@example.test")
                .claim("role", "merchant")
                .issuedAt(new Date())
                .signWith(key)
                .compact();
        assertInvalid(compact);
    }

    @Test
    void missingSecretFailsConstruction() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenValidator(" "));
    }

    @Test
    void shortSecretFailsConstruction() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenValidator("short-test-key"));
    }

    private void assertInvalid(String compact) {
        assertThrows(JwtTokenValidationException.class, () -> validator.validate(compact));
    }

    private static String token(String subject, String role, Instant expiration, String secret) {
        var builder = Jwts.builder();
        if (subject != null) {
            builder.subject(subject);
        }
        if (role != null) {
            builder.claim("role", role);
        }
        Date now = new Date();
        return builder.issuedAt(now)
                .expiration(Date.from(expiration))
                .signWith(key(secret))
                .compact();
    }

    private static javax.crypto.SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
