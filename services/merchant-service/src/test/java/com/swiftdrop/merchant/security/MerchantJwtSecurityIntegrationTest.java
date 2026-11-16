package com.swiftdrop.merchant.security;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MerchantJwtSecurityIntegrationTest {

    private static final String SECRET = "test-only-merchant-jwt-key-material-at-least-32-bytes";
    private static final String PRINCIPAL_PATH = "/__test/security-principal";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void validJwtCreatesPrincipalFromEmailAndRole() throws Exception {
        mockMvc.perform(get(PRINCIPAL_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer("merchant@example.test", "merchant", 120)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.name").value("merchant@example.test"))
                .andExpect(jsonPath("$.authorities", hasItem("ROLE_MERCHANT")));
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get(PRINCIPAL_PATH)
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer("merchant@example.test", "merchant", 120,
                                        SECRET.replace("merchant", "MerchanT"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        mockMvc.perform(get(PRINCIPAL_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer("merchant@example.test", "merchant", -30)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTokenCannotAccessAuthenticatedTestEndpoint() throws Exception {
        mockMvc.perform(get(PRINCIPAL_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void spoofedIdentityHeadersDoNotAuthenticateProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/onboarding/me")
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000001")
                        .header("X-User-Roles", "ROLE_MERCHANT")
                        .header("X-User-Email", "attacker@example.test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerRoleCannotUseMerchantProfileEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/onboarding/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer("customer@example.test", "customer", 120)))
                .andExpect(status().isForbidden());
    }

    @Test
    void merchantEndpointsFailClosedWithoutUserIdMapping() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/onboarding/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer("merchant@example.test", "merchant", 120)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointsAreDeniedForNonAdminRoles() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/admin/pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer("dispatcher@example.test", "dispatcher", 120)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointsAllowAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/admin/pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin@example.test", "admin", 120)))
                .andExpect(status().isOk());
    }

    @Test
    void jwtFilterRunsBeforeAnonymousAndAuthorizationFilters() {
        List<Filter> filters = filterChainProxy.getFilters(PRINCIPAL_PATH);
        int jwtIndex = indexOf(filters, JwtAuthenticationFilter.class);
        int anonymousIndex = indexOf(filters, AnonymousAuthenticationFilter.class);
        int authorizationIndex = indexOf(filters, AuthorizationFilter.class);

        assertTrue(jwtIndex >= 0);
        assertTrue(anonymousIndex > jwtIndex);
        assertTrue(authorizationIndex > jwtIndex);
    }

    private static int indexOf(List<Filter> filters, Class<?> type) {
        for (int i = 0; i < filters.size(); i++) {
            if (type.isInstance(filters.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String bearer(String subject, String role, long expiryOffsetSeconds) {
        return bearer(subject, role, expiryOffsetSeconds, SECRET);
    }

    private static String bearer(String subject, String role, long expiryOffsetSeconds, String secret) {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return "Bearer " + io.jsonwebtoken.Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(now)
                .expiration(Date.from(Instant.now().plusSeconds(expiryOffsetSeconds)))
                .signWith(key)
                .compact();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEndpointConfiguration {

        @Bean
        PrincipalEndpoint principalEndpoint() {
            return new PrincipalEndpoint();
        }
    }

    @RestController
    static class PrincipalEndpoint {

        @GetMapping(PRINCIPAL_PATH)
        PrincipalView view() {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) {
                return new PrincipalView(false, null, List.of());
            }
            boolean authenticated = auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
            return new PrincipalView(authenticated, auth.getName(),
                    auth.getAuthorities().stream().map(a -> a.getAuthority()).toList());
        }
    }

    record PrincipalView(boolean authenticated, String name, List<String> authorities) {
    }
}
