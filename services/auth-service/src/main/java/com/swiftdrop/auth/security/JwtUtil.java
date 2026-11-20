package com.swiftdrop.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes of key material");
        }
        // Derive a HMAC-SHA256 key from the configured secret string
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public static final String ISSUER = "swiftdrop-auth-service";

    // ── Token Generation ─────────────────────────────────────

    public String generateToken(String userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId)
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", role != null ? role.trim().toLowerCase() : "")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    @Deprecated
    public String generateToken(String email, String role) {
        return generateToken(email, email, role);
    }

    // ── Token Validation ─────────────────────────────────────

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    // ── Claim Extraction ─────────────────────────────────────

    public String extractUserId(String token) {
        return extractClaim(token, claims -> {
            String userId = claims.get("userId", String.class);
            return userId != null ? userId : claims.getSubject();
        });
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> {
            String email = claims.get("email", String.class);
            return email != null ? email : claims.getSubject();
        });
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parseClaims(token));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

