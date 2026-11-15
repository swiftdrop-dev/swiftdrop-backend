package com.swiftdrop.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.Mac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenValidatorTest {

    private static final String SECRET = "test-only-gateway-key-material-with-at-least-32-bytes";

    private final JwtTokenValidator validator = new JwtTokenValidator(SECRET);

    @Test
    void validatesAuthServiceContractAndReturnsTrustedPrincipal() {
        AuthenticatedJwtPrincipal principal = validator.validate(token("customer@example.test", "customer",
                Instant.now().plusSeconds(300), key(SECRET)));

        assertEquals("customer@example.test", principal.getName());
        assertEquals("customer", principal.role());
    }

    @Test
    void rejectsInvalidSignature() {
        assertInvalid(token("customer@example.test", "customer", Instant.now().plusSeconds(300),
                key(SECRET.replace("gateway", "gatewaY"))));
    }

    @Test
    void rejectsExpiredToken() {
        assertInvalid(token("customer@example.test", "customer", Instant.now().minusSeconds(1), key(SECRET)));
    }

    @Test
    void rejectsMalformedToken() {
        assertInvalid("not-a-jwt");
    }

    @Test
    void rejectsMissingSubject() {
        assertInvalid(token(null, "customer", Instant.now().plusSeconds(300), key(SECRET)));
    }

    @Test
    void rejectsMissingRole() {
        assertInvalid(token("customer@example.test", null, Instant.now().plusSeconds(300), key(SECRET)));
    }

    @Test
    void rejectsRoleOutsideAuthServiceRoleSet() {
        assertInvalid(token("customer@example.test", "admin", Instant.now().plusSeconds(300), key(SECRET)));
    }

    @Test
    void rejectsUppercaseRoleBecauseCurrentContractUsesLowercaseValues() {
        assertInvalid(token("customer@example.test", "CUSTOMER", Instant.now().plusSeconds(300), key(SECRET)));
    }

    @Test
    void rejectsTokenMissingExpiration() {
        String noExpiration = Jwts.builder()
                .subject("customer@example.test")
                .claim("role", "customer")
                .issuedAt(new Date())
                .signWith(key(SECRET))
                .compact();

        assertInvalid(noExpiration);
    }

    @Test
    void rejectsSubjectWithUnexpectedClaimType() {
        assertInvalid(signedRawClaims("{\"sub\":12345,\"role\":\"customer\",\"iat\":1700000000,\"exp\":4102444800}", key(SECRET)));
    }

    @Test
    void rejectsIssuedAtWithUnexpectedClaimType() {
        assertInvalid(signedRawClaims(
                "{\"sub\":\"customer@example.test\",\"role\":\"customer\",\"iat\":\"not-a-timestamp\",\"exp\":4102444800}",
                key(SECRET)));
    }

    @Test
    void rejectsRoleWithUnexpectedClaimType() {
        var now = new Date();
        String token = Jwts.builder()
                .subject("customer@example.test")
                .claim("role", List.of("admin"))
                .issuedAt(now)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(SECRET))
                .compact();

        assertInvalid(token);
    }

    @Test
    void rejectsMissingSigningSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenValidator(" "));
    }

    @Test
    void rejectsSigningSecretShorterThan32Utf8Bytes() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenValidator("short-test-key"));
    }

    private void assertInvalid(String token) {
        assertThrows(JwtTokenValidationException.class, () -> validator.validate(token));
    }

    private static String token(String subject, String role, Instant expiration, javax.crypto.SecretKey key) {
        var builder = Jwts.builder();
        if (subject != null) {
            builder.subject(subject);
        }
        if (role != null) {
            builder.claim("role", role);
        }
        Instant now = Instant.now();
        return builder.issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }

    private static javax.crypto.SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String signedRawClaims(String payloadJson, javax.crypto.SecretKey key) {
        String algorithm = switch (key.getAlgorithm()) {
            case "HmacSHA256" -> "HS256";
            case "HmacSHA384" -> "HS384";
            case "HmacSHA512" -> "HS512";
            default -> throw new IllegalArgumentException("Unexpected test HMAC algorithm");
        };
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\"}").getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        try {
            Mac mac = Mac.getInstance(key.getAlgorithm());
            mac.init(key);
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
            return signingInput + "." + signature;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create malformed test token", exception);
        }
    }
}
