package com.swiftdrop.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.dto.request.GoogleAuthRequest;
import com.swiftdrop.auth.entity.Role;
import com.swiftdrop.auth.entity.User;
import com.swiftdrop.auth.exception.GoogleTokenValidationException;
import com.swiftdrop.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "DB_URL=jdbc:h2:mem:auth-google-tests;DB_CLOSE_DELAY=-1;USER=sa;PASSWORD=test-only-h2-password",
        "DB_USERNAME=sa",
        "DB_PASSWORD=test-only-h2-password",
        "JWT_SECRET=test-only-not-a-production-secret-with-32-bytes-minimum",
        "google.client-id=test-google-client-id-123.apps.googleusercontent.com",
        "google.verify-online=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthGoogleSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestGoogleTokenVerifier tokenVerifier;

    @TestConfiguration
    static class TestGoogleConfig {
        @Bean
        @Primary
        TestGoogleTokenVerifier testGoogleTokenVerifier() {
            return new TestGoogleTokenVerifier();
        }
    }

    static class TestGoogleTokenVerifier implements GoogleTokenVerifier {
        private GoogleUserClaims nextClaims;
        private RuntimeException nextException;

        public void setNextClaims(GoogleUserClaims claims) {
            this.nextClaims = claims;
            this.nextException = null;
        }

        public void setNextException(RuntimeException ex) {
            this.nextException = ex;
            this.nextClaims = null;
        }

        @Override
        public GoogleUserClaims verify(String idToken) {
            if (nextException != null) {
                throw nextException;
            }
            if (nextClaims != null) {
                return nextClaims;
            }
            throw new GoogleTokenValidationException("Invalid token signature");
        }
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Test 1: New Google user registration creates CUSTOMER user and returns valid SwiftDrop JWT")
    void newGoogleUserRegistrationSuccess() throws Exception {
        tokenVerifier.setNextClaims(new GoogleUserClaims(
                "google_sub_1001",
                "newuser@gmail.com",
                true,
                "New Google User",
                "https://picture.url/avatar.jpg"
        ));

        GoogleAuthRequest request = new GoogleAuthRequest("valid.google.jwt.token");

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("newuser@gmail.com"))
                .andExpect(jsonPath("$.name").value("New Google User"))
                .andExpect(jsonPath("$.role").value("customer"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        Optional<User> savedOpt = userRepository.findByEmail("newuser@gmail.com");
        assertTrue(savedOpt.isPresent());
        User saved = savedOpt.get();
        assertEquals("google", saved.getProvider());
        assertEquals("google_sub_1001", saved.getProviderId());
        assertEquals(Role.customer, saved.getRole());
        assertTrue(saved.isActive());
        assertTrue(jwtUtil.isTokenValid(jwtUtil.generateToken(saved.getId(), saved.getEmail(), saved.getRole().name())));
    }

    @Test
    @DisplayName("Test 2: Existing account linking links Google identity and preserves existing role and password")
    void existingAccountLinkingPreservesRoleAndPassword() throws Exception {
        // Pre-existing user with password and admin role
        String originalPasswordHash = passwordEncoder.encode("superSecretPassword");
        User existingAdmin = new User("Admin User", "existingadmin@swiftdrop.lk", originalPasswordHash, "0771234567", Role.admin, true);
        existingAdmin = userRepository.save(existingAdmin);

        tokenVerifier.setNextClaims(new GoogleUserClaims(
                "google_sub_admin_999",
                "existingadmin@swiftdrop.lk",
                true,
                "Admin User",
                null
        ));

        GoogleAuthRequest request = new GoogleAuthRequest("valid.admin.google.token");

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("existingadmin@swiftdrop.lk"))
                .andExpect(jsonPath("$.role").value("admin")); // Role preserved!

        User updatedUser = userRepository.findById(existingAdmin.getId()).orElseThrow();
        assertEquals("google", updatedUser.getProvider());
        assertEquals("google_sub_admin_999", updatedUser.getProviderId());
        assertEquals(Role.admin, updatedUser.getRole());
        assertEquals(originalPasswordHash, updatedUser.getPassword(), "Original password hash must be preserved!");
    }

    @Test
    @DisplayName("Test 3: Existing linked Google user can log in again")
    void existingLinkedGoogleUserCanLogIn() throws Exception {
        User user = new User("Linked User", "linked@swiftdrop.lk", passwordEncoder.encode("pw123"), null, Role.customer, true, "google", "sub_linked_888");
        userRepository.save(user);

        tokenVerifier.setNextClaims(new GoogleUserClaims(
                "sub_linked_888",
                "linked@swiftdrop.lk",
                true,
                "Linked User",
                null
        ));

        GoogleAuthRequest request = new GoogleAuthRequest("valid.linked.token");

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("linked@swiftdrop.lk"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Test 4: Deactivated user logging in via Google is rejected with 403 Forbidden")
    void deactivatedUserRejected() throws Exception {
        User deactivated = new User("Deactivated", "deactivated@swiftdrop.lk", passwordEncoder.encode("pw"), null, Role.customer, false, "google", "sub_deact_555");
        userRepository.save(deactivated);

        tokenVerifier.setNextClaims(new GoogleUserClaims(
                "sub_deact_555",
                "deactivated@swiftdrop.lk",
                true,
                "Deactivated User",
                null
        ));

        GoogleAuthRequest request = new GoogleAuthRequest("deactivated.token");

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Account is deactivated")));
    }

    @Test
    @DisplayName("Test 5: Invalid Google token signature is rejected with 401 Unauthorized")
    void invalidTokenSignatureRejected() throws Exception {
        tokenVerifier.setNextException(new GoogleTokenValidationException("Google rejected ID token: 400 Bad Request"));

        GoogleAuthRequest request = new GoogleAuthRequest("invalid.tampered.token");

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Google rejected ID token")));
    }

    @Test
    @DisplayName("Test 6: Expired Google token is rejected with 401 Unauthorized")
    void expiredGoogleTokenRejected() throws Exception {
        tokenVerifier.setNextException(new GoogleTokenValidationException("Google ID token has expired."));

        GoogleAuthRequest request = new GoogleAuthRequest("expired.token");

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("has expired")));
    }

    @Test
    @DisplayName("Test 7: Incorrect audience or issuer rejected with 401 Unauthorized")
    void incorrectAudienceOrIssuerRejected() throws Exception {
        tokenVerifier.setNextException(new GoogleTokenValidationException("Token audience does not match configured Google Client ID."));

        GoogleAuthRequest request = new GoogleAuthRequest("wrong.aud.token");

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Token audience does not match")));
    }

    @Test
    @DisplayName("Test 8: Unverified email from Google is rejected with 401 Unauthorized")
    void unverifiedEmailRejected() throws Exception {
        tokenVerifier.setNextClaims(new GoogleUserClaims(
                "sub_unverified",
                "unverified@example.com",
                false, // email_verified is false!
                "Unverified User",
                null
        ));

        GoogleAuthRequest request = new GoogleAuthRequest("unverified.email.token");

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("email is not verified")));
    }

    @Test
    @DisplayName("Test 9: Account linking with conflicting provider ID rejected")
    void conflictingProviderIdRejected() throws Exception {
        // User already linked to google_sub_AAA
        User user = new User("Alice", "alice@example.com", passwordEncoder.encode("pw"), null, Role.customer, true, "google", "google_sub_AAA");
        userRepository.save(user);

        // Another Google token with same email but DIFFERENT sub: google_sub_BBB
        tokenVerifier.setNextClaims(new GoogleUserClaims(
                "google_sub_BBB",
                "alice@example.com",
                true,
                "Alice",
                null
        ));

        GoogleAuthRequest request = new GoogleAuthRequest("conflicting.sub.token");

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("already linked to a different provider identity")));

    }

    @Test
    @DisplayName("Test 10: Missing Google configuration produces clear error")
    void missingGoogleConfigProducesClearError() throws Exception {
        tokenVerifier.setNextException(new IllegalStateException("Google OAuth 2.0 is not configured on this server (GOOGLE_CLIENT_ID missing)."));

        GoogleAuthRequest request = new GoogleAuthRequest("any.token");

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(containsString("GOOGLE_CLIENT_ID missing")));
    }

    @Test
    @DisplayName("Test 11: Blank token request is rejected by validation")
    void blankTokenRequestRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Google ID token is required")));
    }
}
