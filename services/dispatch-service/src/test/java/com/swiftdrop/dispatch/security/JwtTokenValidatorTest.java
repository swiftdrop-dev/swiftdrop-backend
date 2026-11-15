package com.swiftdrop.dispatch.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenValidatorTest {
    private static final String SECRET = "dispatch-test-signing-key-material-32-bytes-minimum";
    private final JwtTokenValidator validator = new JwtTokenValidator(SECRET);

    @Test
    void acceptsAuthServiceContract() {
        UserPrincipal principal = validator.validate(token(SECRET, "courier", "courier@example.test", Instant.now().minusSeconds(2), Instant.now().plusSeconds(300)));
        assertThat(principal.getEmail()).isEqualTo("courier@example.test");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_COURIER");
    }

    @Test
    void rejectsInvalidSignature() {
        assertThatThrownBy(() -> validator.validate(token("different-test-key-material-32-bytes-minimum", "dispatcher", "d@example.test", Instant.now().minusSeconds(1), Instant.now().plusSeconds(60))))
                .isInstanceOf(JwtTokenValidationException.class).hasMessage("Bearer token is invalid");
    }

    @Test
    void rejectsExpiredToken() {
        assertThatThrownBy(() -> validator.validate(token(SECRET, "dispatcher", "d@example.test", Instant.now().minusSeconds(120), Instant.now().minusSeconds(1))))
                .isInstanceOf(JwtTokenValidationException.class);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> validator.validate("not.a.jwt"))
                .isInstanceOf(JwtTokenValidationException.class).hasMessage("Bearer token is invalid");
    }

    @Test
    void rejectsMissingSubjectRoleIssuedAtAndNonLowercaseRole() {
        assertInvalid(Jwts.builder().claim("role", "courier").issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(60))).signWith(key(SECRET)).compact());
        assertInvalid(Jwts.builder().subject("x@example.test").issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(60))).signWith(key(SECRET)).compact());
        assertInvalid(Jwts.builder().subject("x@example.test").claim("role", "courier").expiration(Date.from(Instant.now().plusSeconds(60))).signWith(key(SECRET)).compact());
        assertInvalid(token(SECRET, "COURIER", "x@example.test", Instant.now().minusSeconds(1), Instant.now().plusSeconds(60)));
        assertInvalid(token(SECRET, "admin", "x@example.test", Instant.now().minusSeconds(1), Instant.now().plusSeconds(60)));
    }

    @Test
    void rejectsFutureIssuedAt() {
        assertInvalid(token(SECRET, "dispatcher", "d@example.test", Instant.now().plusSeconds(60), Instant.now().plusSeconds(120)));
    }

    @Test
    void rejectsMissingOrShortSecret() {
        assertThatThrownBy(() -> new JwtTokenValidator(" ")).isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new JwtTokenValidator("short")).isInstanceOf(IllegalStateException.class).hasMessageContaining("32 UTF-8 bytes");
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> validator.validate(token)).isInstanceOf(JwtTokenValidationException.class);
    }

    static String token(String secret, String role, String subject, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder().subject(subject).claim("role", role)
                .issuedAt(Date.from(issuedAt)).expiration(Date.from(expiresAt))
                .signWith(key(secret)).compact();
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
