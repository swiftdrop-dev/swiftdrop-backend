package com.swiftdrop.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS);
    }

    @Test
    void shouldGenerateValidTokenWithCanonicalUserIdAndClaims() {
        String userId = UUID.randomUUID().toString();
        String email = "merchant@swiftdrop.com";
        String role = "MERCHANT";

        String token = jwtUtil.generateToken(userId, email, role);

        assertNotNull(token);
        assertTrue(jwtUtil.isTokenValid(token));
        assertEquals(userId, jwtUtil.extractUserId(token));
        assertEquals(email, jwtUtil.extractEmail(token));
        assertEquals("merchant", jwtUtil.extractRole(token));
    }

    @Test
    void shouldGenerateValidTokenForAdminRole() {
        String userId = UUID.randomUUID().toString();
        String email = "admin@swiftdrop.com";
        String role = "admin";

        String token = jwtUtil.generateToken(userId, email, role);

        assertTrue(jwtUtil.isTokenValid(token));
        assertEquals(userId, jwtUtil.extractUserId(token));
        assertEquals(email, jwtUtil.extractEmail(token));
        assertEquals("admin", jwtUtil.extractRole(token));
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtUtil shortLivedJwtUtil = new JwtUtil(SECRET, -1000); // already expired
        String userId = UUID.randomUUID().toString();

        String token = shortLivedJwtUtil.generateToken(userId, "user@test.com", "customer");

        assertFalse(shortLivedJwtUtil.isTokenValid(token));
    }

    @Test
    void shouldRejectTamperedToken() {
        String userId = UUID.randomUUID().toString();
        String token = jwtUtil.generateToken(userId, "user@test.com", "customer");
        String tamperedToken = token + "corrupted";

        assertFalse(jwtUtil.isTokenValid(tamperedToken));
    }

    @Test
    void shouldRejectTokenSignedWithDifferentSecret() {
        String differentSecret = "9876543210987654321098765432109876543210987654321098765432109876";
        JwtUtil otherJwtUtil = new JwtUtil(differentSecret, EXPIRATION_MS);
        String userId = UUID.randomUUID().toString();

        String token = otherJwtUtil.generateToken(userId, "user@test.com", "customer");

        assertFalse(jwtUtil.isTokenValid(token));
    }
}
