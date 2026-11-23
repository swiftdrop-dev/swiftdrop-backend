package com.swiftdrop.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.exception.GoogleTokenValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DefaultGoogleTokenVerifierTest {

    private static final String CLIENT_ID = "test-google-client-id.apps.googleusercontent.com";
    private static final String JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private ObjectMapper objectMapper;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private KeyPair keyPair;
    private String modulusBase64;
    private String exponentBase64;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        RSAPublicKey pub = (RSAPublicKey) keyPair.getPublic();
        modulusBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getModulus().toByteArray());
        exponentBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getPublicExponent().toByteArray());
    }

    private String createSignedJwt(String kid, Map<String, Object> claims, KeyPair signingKeys) throws Exception {
        String headerJson = objectMapper.writeValueAsString(Map.of(
                "alg", "RS256",
                "typ", "JWT",
                "kid", kid
        ));
        String payloadJson = objectMapper.writeValueAsString(claims);

        String headerBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String signingInput = headerBase64 + "." + payloadBase64;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(signingKeys.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        byte[] sigBytes = signature.sign();

        String sigBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
        return signingInput + "." + sigBase64;
    }

    private String createJwksResponse(String kid, String n, String e) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "keys", java.util.List.of(Map.of(
                        "kty", "RSA",
                        "alg", "RS256",
                        "use", "sig",
                        "kid", kid,
                        "n", n,
                        "e", e
                ))
        ));
    }

    @Test
    @DisplayName("Cryptographic RSA verification: valid signed Google token succeeds")
    void testVerifyViaGooglePublicKey_Success() throws Exception {
        RestClient restClient = restClientBuilder.build();
        DefaultGoogleTokenVerifier verifier = new DefaultGoogleTokenVerifier(
                CLIENT_ID, false, JWKS_URL, restClient, objectMapper
        );

        String jwks = createJwksResponse("key-1", modulusBase64, exponentBase64);
        mockServer.expect(requestTo(JWKS_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jwks, MediaType.APPLICATION_JSON));

        Map<String, Object> claims = Map.of(
                "iss", "https://accounts.google.com",
                "aud", CLIENT_ID,
                "sub", "google-sub-12345",
                "email", "alice@example.com",
                "email_verified", true,
                "name", "Alice Example",
                "exp", Instant.now().plusSeconds(3600).getEpochSecond(),
                "iat", Instant.now().getEpochSecond()
        );

        String token = createSignedJwt("key-1", claims, keyPair);
        GoogleUserClaims userClaims = verifier.verify(token);

        assertNotNull(userClaims);
        assertEquals("google-sub-12345", userClaims.subject());
        assertEquals("alice@example.com", userClaims.email());
        assertTrue(userClaims.emailVerified());
        assertEquals("Alice Example", userClaims.name());
        mockServer.verify();
    }

    @Test
    @DisplayName("Cryptographic RSA verification: tampered payload fails signature check")
    void testVerifyViaGooglePublicKey_TamperedPayload_Fails() throws Exception {
        RestClient restClient = restClientBuilder.build();
        DefaultGoogleTokenVerifier verifier = new DefaultGoogleTokenVerifier(
                CLIENT_ID, false, JWKS_URL, restClient, objectMapper
        );

        String jwks = createJwksResponse("key-1", modulusBase64, exponentBase64);
        mockServer.expect(requestTo(JWKS_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jwks, MediaType.APPLICATION_JSON));

        Map<String, Object> claims = Map.of(
                "iss", "https://accounts.google.com",
                "aud", CLIENT_ID,
                "sub", "google-sub-12345",
                "email", "alice@example.com",
                "email_verified", true,
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        );

        String token = createSignedJwt("key-1", claims, keyPair);
        String[] parts = token.split("\\.");

        // Tamper payload to attacker email
        Map<String, Object> tamperedClaims = Map.of(
                "iss", "https://accounts.google.com",
                "aud", CLIENT_ID,
                "sub", "google-sub-12345",
                "email", "attacker@evil.com",
                "email_verified", true,
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        );
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                objectMapper.writeValueAsString(tamperedClaims).getBytes(StandardCharsets.UTF_8)
        );

        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThrows(GoogleTokenValidationException.class, () -> verifier.verify(tamperedToken));
        mockServer.verify();
    }

    @Test
    @DisplayName("Cryptographic RSA verification: unknown or untrusted kid fails closed")
    void testVerifyViaGooglePublicKey_UntrustedKid_Fails() throws Exception {
        RestClient restClient = restClientBuilder.build();
        DefaultGoogleTokenVerifier verifier = new DefaultGoogleTokenVerifier(
                CLIENT_ID, false, JWKS_URL, restClient, objectMapper
        );

        String jwks = createJwksResponse("key-1", modulusBase64, exponentBase64);
        mockServer.expect(requestTo(JWKS_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jwks, MediaType.APPLICATION_JSON));

        Map<String, Object> claims = Map.of(
                "iss", "https://accounts.google.com",
                "aud", CLIENT_ID,
                "sub", "google-sub-12345",
                "email", "alice@example.com",
                "email_verified", true,
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        );

        // Sign with an unknown kid
        String token = createSignedJwt("untrusted-key-999", claims, keyPair);

        GoogleTokenValidationException ex = assertThrows(GoogleTokenValidationException.class,
                () -> verifier.verify(token));
        assertTrue(ex.getMessage().contains("Untrusted or unknown Google signing key ID"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Key rotation: cache miss on new kid triggers fresh JWKS fetch")
    void testVerifyViaGooglePublicKey_KeyRotation_Success() throws Exception {
        RestClient restClient = restClientBuilder.build();
        DefaultGoogleTokenVerifier verifier = new DefaultGoogleTokenVerifier(
                CLIENT_ID, false, JWKS_URL, restClient, objectMapper
        );

        // First key pair
        String jwks1 = createJwksResponse("key-1", modulusBase64, exponentBase64);

        // Second key pair representing rotated key
        KeyPairGenerator gen2 = KeyPairGenerator.getInstance("RSA");
        gen2.initialize(2048);
        KeyPair keyPair2 = gen2.generateKeyPair();
        RSAPublicKey pub2 = (RSAPublicKey) keyPair2.getPublic();
        String mod2 = Base64.getUrlEncoder().withoutPadding().encodeToString(pub2.getModulus().toByteArray());
        String exp2 = Base64.getUrlEncoder().withoutPadding().encodeToString(pub2.getPublicExponent().toByteArray());

        String jwks2 = objectMapper.writeValueAsString(Map.of(
                "keys", java.util.List.of(
                        Map.of("kty", "RSA", "alg", "RS256", "use", "sig", "kid", "key-1", "n", modulusBase64, "e", exponentBase64),
                        Map.of("kty", "RSA", "alg", "RS256", "use", "sig", "kid", "key-2", "n", mod2, "e", exp2)
                )
        ));

        mockServer.expect(requestTo(JWKS_URL)).andRespond(withSuccess(jwks1, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(JWKS_URL)).andRespond(withSuccess(jwks2, MediaType.APPLICATION_JSON));

        Map<String, Object> claims1 = Map.of(
                "iss", "https://accounts.google.com",
                "aud", CLIENT_ID,
                "sub", "user-1",
                "email", "user1@example.com",
                "email_verified", true,
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        );
        String token1 = createSignedJwt("key-1", claims1, keyPair);
        assertEquals("user-1", verifier.verify(token1).subject());

        // Token signed with rotated key-2 triggers refresh
        Map<String, Object> claims2 = Map.of(
                "iss", "https://accounts.google.com",
                "aud", CLIENT_ID,
                "sub", "user-2",
                "email", "user2@example.com",
                "email_verified", true,
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        );
        String token2 = createSignedJwt("key-2", claims2, keyPair2);
        assertEquals("user-2", verifier.verify(token2).subject());

        mockServer.verify();
    }

    @Test
    @DisplayName("Claims validation: unverified email is rejected")
    void testVerifyViaGooglePublicKey_UnverifiedEmail_Fails() throws Exception {
        RestClient restClient = restClientBuilder.build();
        DefaultGoogleTokenVerifier verifier = new DefaultGoogleTokenVerifier(
                CLIENT_ID, false, JWKS_URL, restClient, objectMapper
        );

        String jwks = createJwksResponse("key-1", modulusBase64, exponentBase64);
        mockServer.expect(requestTo(JWKS_URL)).andRespond(withSuccess(jwks, MediaType.APPLICATION_JSON));

        Map<String, Object> claims = Map.of(
                "iss", "https://accounts.google.com",
                "aud", CLIENT_ID,
                "sub", "sub-123",
                "email", "unverified@example.com",
                "email_verified", false,
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        );

        String token = createSignedJwt("key-1", claims, keyPair);
        GoogleTokenValidationException ex = assertThrows(GoogleTokenValidationException.class, () -> verifier.verify(token));
        assertTrue(ex.getMessage().contains("email is not verified"));
    }

    @Test
    @DisplayName("Claims validation: expired token is rejected")
    void testVerifyViaGooglePublicKey_ExpiredToken_Fails() throws Exception {
        RestClient restClient = restClientBuilder.build();
        DefaultGoogleTokenVerifier verifier = new DefaultGoogleTokenVerifier(
                CLIENT_ID, false, JWKS_URL, restClient, objectMapper
        );

        String jwks = createJwksResponse("key-1", modulusBase64, exponentBase64);
        mockServer.expect(requestTo(JWKS_URL)).andRespond(withSuccess(jwks, MediaType.APPLICATION_JSON));

        Map<String, Object> claims = Map.of(
                "iss", "https://accounts.google.com",
                "aud", CLIENT_ID,
                "sub", "sub-123",
                "email", "expired@example.com",
                "email_verified", true,
                "exp", Instant.now().minusSeconds(300).getEpochSecond() // Expired 5 minutes ago
        );

        String token = createSignedJwt("key-1", claims, keyPair);
        GoogleTokenValidationException ex = assertThrows(GoogleTokenValidationException.class, () -> verifier.verify(token));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("Claims validation: audience mismatch is rejected")
    void testVerifyViaGooglePublicKey_AudienceMismatch_Fails() throws Exception {
        RestClient restClient = restClientBuilder.build();
        DefaultGoogleTokenVerifier verifier = new DefaultGoogleTokenVerifier(
                CLIENT_ID, false, JWKS_URL, restClient, objectMapper
        );

        String jwks = createJwksResponse("key-1", modulusBase64, exponentBase64);
        mockServer.expect(requestTo(JWKS_URL)).andRespond(withSuccess(jwks, MediaType.APPLICATION_JSON));

        Map<String, Object> claims = Map.of(
                "iss", "https://accounts.google.com",
                "aud", "wrong-aud.apps.googleusercontent.com",
                "sub", "sub-123",
                "email", "test@example.com",
                "email_verified", true,
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        );

        String token = createSignedJwt("key-1", claims, keyPair);
        GoogleTokenValidationException ex = assertThrows(GoogleTokenValidationException.class, () -> verifier.verify(token));
        assertTrue(ex.getMessage().contains("audience does not match"));
    }

    @Test
    @DisplayName("Online verification: successful tokeninfo response returns claims")
    void testVerifyOnline_Success() throws Exception {
        RestClient restClient = restClientBuilder.build();
        DefaultGoogleTokenVerifier verifier = new DefaultGoogleTokenVerifier(
                CLIENT_ID, true, JWKS_URL, restClient, objectMapper
        );

        String token = "dummy-online-token";
        String googleResponse = objectMapper.writeValueAsString(Map.of(
                "iss", "https://accounts.google.com",
                "aud", CLIENT_ID,
                "sub", "online-sub-456",
                "email", "online@example.com",
                "email_verified", "true",
                "name", "Online User",
                "exp", String.valueOf(Instant.now().plusSeconds(3600).getEpochSecond())
        ));

        mockServer.expect(requestTo(TOKENINFO_URL + token))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(googleResponse, MediaType.APPLICATION_JSON));

        GoogleUserClaims claims = verifier.verify(token);
        assertEquals("online-sub-456", claims.subject());
        assertEquals("online@example.com", claims.email());
        assertTrue(claims.emailVerified());
        assertEquals("Online User", claims.name());
        mockServer.verify();
    }

    @Test
    @DisplayName("Online verification: tokeninfo error returns 401 and fails closed")
    void testVerifyOnline_GoogleError_FailsClosed() {
        RestClient restClient = restClientBuilder.build();
        DefaultGoogleTokenVerifier verifier = new DefaultGoogleTokenVerifier(
                CLIENT_ID, true, JWKS_URL, restClient, objectMapper
        );

        mockServer.expect(requestTo(TOKENINFO_URL + "bad-token"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withBadRequest().body("{\"error_description\": \"Invalid Value\"}"));

        assertThrows(GoogleTokenValidationException.class, () -> verifier.verify("bad-token"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Configuration: missing client ID throws IllegalStateException")
    void testVerify_MissingClientId_ThrowsIllegalStateException() {
        DefaultGoogleTokenVerifier verifier = new DefaultGoogleTokenVerifier(
                "", false, JWKS_URL, restClientBuilder.build(), objectMapper
        );

        assertThrows(IllegalStateException.class, () -> verifier.verify("some-token"));
    }
}
