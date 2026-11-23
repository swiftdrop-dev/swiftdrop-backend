package com.swiftdrop.auth.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.exception.GoogleTokenValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultGoogleTokenVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultGoogleTokenVerifier.class);
    private static final List<String> ACCEPTED_ISSUERS = List.of(
            "https://accounts.google.com",
            "accounts.google.com"
    );
    private static final String DEFAULT_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Duration KEY_CACHE_DURATION = Duration.ofHours(1);
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final String configuredClientId;
    private final boolean verifyOnline;
    private final String jwksUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // Public key cache for offline verification and key rotation
    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private volatile Instant lastKeysFetched = Instant.EPOCH;

    @Autowired
    public DefaultGoogleTokenVerifier(
            @Value("${google.client-id:}") String configuredClientId,
            @Value("${google.verify-online:true}") boolean verifyOnline,
            @Value("${google.jwks-url:" + DEFAULT_JWKS_URL + "}") String jwksUrl,
            ObjectMapper objectMapper) {

        this.configuredClientId = configuredClientId != null ? configuredClientId.trim() : "";
        this.verifyOnline = verifyOnline;
        this.jwksUrl = (jwksUrl != null && !jwksUrl.isBlank()) ? jwksUrl.trim() : DEFAULT_JWKS_URL;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    // Constructor for testing with custom RestClient and JWKS URL
    public DefaultGoogleTokenVerifier(
            String configuredClientId,
            boolean verifyOnline,
            String jwksUrl,
            RestClient restClient,
            ObjectMapper objectMapper) {

        this.configuredClientId = configuredClientId != null ? configuredClientId.trim() : "";
        this.verifyOnline = verifyOnline;
        this.jwksUrl = (jwksUrl != null && !jwksUrl.isBlank()) ? jwksUrl.trim() : DEFAULT_JWKS_URL;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    // Backward-compatible constructor for tests
    DefaultGoogleTokenVerifier(String configuredClientId, boolean verifyOnline, RestClient restClient, ObjectMapper objectMapper) {
        this(configuredClientId, verifyOnline, DEFAULT_JWKS_URL, restClient, objectMapper);
    }

    @Override
    public GoogleUserClaims verify(String idToken) {
        if (configuredClientId.isBlank()) {
            throw new IllegalStateException("Google OAuth 2.0 is not configured on this server (GOOGLE_CLIENT_ID missing).");
        }

        if (idToken == null || idToken.isBlank()) {
            throw new GoogleTokenValidationException("Google ID token cannot be blank.");
        }

        if (verifyOnline) {
            return verifyViaGoogleTokenInfo(idToken);
        } else {
            return verifyViaGooglePublicKey(idToken);
        }
    }

    private GoogleUserClaims verifyViaGoogleTokenInfo(String idToken) {
        try {
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            String responseBody = restClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("Google tokeninfo returned status {}: {}", response.getStatusCode(), body);
                        throw new GoogleTokenValidationException("Google rejected ID token: " + response.getStatusCode());
                    })
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new GoogleTokenValidationException("Empty response received from Google token verification.");
            }

            JsonNode node = objectMapper.readTree(responseBody);
            return validateClaimsNode(node);
        } catch (GoogleTokenValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google token verification network or parsing failure: {}", e.getMessage());
            throw new GoogleTokenValidationException("Unable to verify Google token: " + e.getMessage(), e);
        }
    }

    /**
     * Cryptographically verifies the Google ID token's RS256 signature using Google's trusted RSA public keys,
     * extracts claims, and asserts audience, issuer, expiry, and email verification.
     */
    public GoogleUserClaims verifyViaGooglePublicKey(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                throw new GoogleTokenValidationException("Invalid JWT structure: token must contain header, payload, and signature.");
            }

            // 1. Decode and validate header
            byte[] headerBytes;
            try {
                headerBytes = Base64.getUrlDecoder().decode(parts[0]);
            } catch (IllegalArgumentException e) {
                throw new GoogleTokenValidationException("Invalid base64 encoding in token header.", e);
            }

            JsonNode headerNode = objectMapper.readTree(new String(headerBytes, StandardCharsets.UTF_8));
            String alg = headerNode.path("alg").asText();
            if (!"RS256".equalsIgnoreCase(alg)) {
                throw new GoogleTokenValidationException("Unsupported signing algorithm: " + alg + ". Only RS256 is accepted.");
            }

            String kid = headerNode.path("kid").asText();
            if (kid == null || kid.isBlank()) {
                throw new GoogleTokenValidationException("Missing 'kid' in token header.");
            }

            // 2. Select trusted public key by kid (handling key rotation & caching)
            PublicKey publicKey = getPublicKey(kid);

            // 3. Cryptographically verify signature
            byte[] signatureBytes;
            try {
                signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
            } catch (IllegalArgumentException e) {
                throw new GoogleTokenValidationException("Invalid base64 encoding in token signature.", e);
            }

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));

            if (!verifier.verify(signatureBytes)) {
                throw new GoogleTokenValidationException("Google ID token signature verification failed.");
            }

            // 4. Decode payload and validate claims
            byte[] payloadBytes;
            try {
                payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            } catch (IllegalArgumentException e) {
                throw new GoogleTokenValidationException("Invalid base64 encoding in token payload.", e);
            }

            JsonNode payloadNode = objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            return validateClaimsNode(payloadNode);
        } catch (GoogleTokenValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new GoogleTokenValidationException("Failed to verify Google ID token cryptographically: " + e.getMessage(), e);
        }
    }

    private PublicKey getPublicKey(String kid) {
        PublicKey cached = keyCache.get(kid);
        if (cached != null && Instant.now().isBefore(lastKeysFetched.plus(KEY_CACHE_DURATION))) {
            return cached;
        }

        // Cache miss or expired: refresh from JWKS endpoint (handles key rotation)
        refreshKeys();

        PublicKey refreshed = keyCache.get(kid);
        if (refreshed == null) {
            throw new GoogleTokenValidationException("Untrusted or unknown Google signing key ID (kid: " + kid + ").");
        }
        return refreshed;
    }

    private synchronized void refreshKeys() {
        try {
            String response = restClient.get()
                    .uri(jwksUrl)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        throw new GoogleTokenValidationException("Failed to fetch Google public keys: HTTP " + res.getStatusCode());
                    })
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new GoogleTokenValidationException("Empty JWKS response from Google.");
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode keys = root.get("keys");
            if (keys == null || !keys.isArray()) {
                throw new GoogleTokenValidationException("Invalid JWKS: missing 'keys' array.");
            }

            Map<String, PublicKey> newKeys = new HashMap<>();
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            for (JsonNode key : keys) {
                String kty = key.path("kty").asText();
                String kid = key.path("kid").asText();
                String n = key.path("n").asText();
                String e = key.path("e").asText();

                if ("RSA".equalsIgnoreCase(kty) && !kid.isBlank() && !n.isBlank() && !e.isBlank()) {
                    BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
                    BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
                    RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                    newKeys.put(kid, keyFactory.generatePublic(spec));
                }
            }

            if (newKeys.isEmpty()) {
                throw new GoogleTokenValidationException("No valid RSA public keys found in Google JWKS response.");
            }

            keyCache.clear();
            keyCache.putAll(newKeys);
            lastKeysFetched = Instant.now();
            log.info("Refreshed {} Google RSA public keys from JWKS", newKeys.size());
        } catch (GoogleTokenValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch or parse Google JWKS: {}", e.getMessage());
            throw new GoogleTokenValidationException("Unable to load Google public keys: " + e.getMessage(), e);
        }
    }

    private GoogleUserClaims validateClaimsNode(JsonNode node) {
        // 1. Issuer check
        JsonNode issNode = node.get("iss");
        if (issNode == null || !ACCEPTED_ISSUERS.contains(issNode.asText())) {
            throw new GoogleTokenValidationException("Invalid token issuer: " + (issNode != null ? issNode.asText() : "null"));
        }

        // 2. Audience check
        JsonNode audNode = node.get("aud");
        if (audNode == null || !configuredClientId.equals(audNode.asText())) {
            throw new GoogleTokenValidationException("Token audience does not match configured Google Client ID.");
        }

        // 3. Expiration check with clock skew tolerance
        long now = Instant.now().getEpochSecond();
        JsonNode expNode = node.get("exp");
        if (expNode != null) {
            long expSeconds = expNode.asLong();
            if (now - CLOCK_SKEW_SECONDS > expSeconds) {
                throw new GoogleTokenValidationException("Google ID token has expired.");
            }
        } else {
            throw new GoogleTokenValidationException("Missing 'exp' claim in Google token.");
        }

        // 4. Issued-at / Not-before check (cannot be issued in the future)
        JsonNode iatNode = node.get("iat");
        if (iatNode != null) {
            long iatSeconds = iatNode.asLong();
            if (iatSeconds > now + CLOCK_SKEW_SECONDS) {
                throw new GoogleTokenValidationException("Google ID token was issued in the future (invalid iat).");
            }
        }

        // 5. Email verification check
        JsonNode emailVerifiedNode = node.get("email_verified");
        boolean emailVerified = emailVerifiedNode != null &&
                (emailVerifiedNode.asBoolean() || "true".equalsIgnoreCase(emailVerifiedNode.asText()));
        if (!emailVerified) {
            throw new GoogleTokenValidationException("Google account email is not verified.");
        }

        // 6. Subject & Email
        JsonNode subNode = node.get("sub");
        if (subNode == null || subNode.asText().isBlank()) {
            throw new GoogleTokenValidationException("Missing subject (sub) claim in Google token.");
        }
        String subject = subNode.asText();

        JsonNode emailNode = node.get("email");
        if (emailNode == null || emailNode.asText().isBlank()) {
            throw new GoogleTokenValidationException("Missing email claim in Google token.");
        }
        String email = emailNode.asText().trim().toLowerCase();

        JsonNode nameNode = node.get("name");
        String name = nameNode != null ? nameNode.asText() : null;

        JsonNode pictureNode = node.get("picture");
        String picture = pictureNode != null ? pictureNode.asText() : null;

        return new GoogleUserClaims(subject, email, emailVerified, name, picture);
    }
}
