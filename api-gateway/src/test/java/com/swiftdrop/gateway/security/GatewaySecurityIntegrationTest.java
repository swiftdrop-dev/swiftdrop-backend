package com.swiftdrop.gateway.security;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
class GatewaySecurityIntegrationTest {

    private static final String SECRET = "test-only-gateway-jwt-key-material-at-least-32-bytes";
    private static final String TEST_PATH = "/__test/security-principal";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Test
    void anonymousRequestRemainsPermittedUnderTemporaryPolicy() throws Exception {
        mockMvc.perform(get(TEST_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void validJwtEstablishesPrincipalAndRoleInActualSecurityChain() throws Exception {
        mockMvc.perform(get(TEST_PATH).header(HttpHeaders.AUTHORIZATION, bearer("customer@example.test", "customer", 300)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.name").value("customer@example.test"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_CUSTOMER")));
    }

    @Test
    void invalidJwtReturnsUnauthorized() throws Exception {
        String wrongKeyToken = bearer("customer@example.test", "customer", 300,
                "another-test-only-key-material-at-least-32-bytes");
        mockMvc.perform(get(TEST_PATH).header(HttpHeaders.AUTHORIZATION, wrongKeyToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(TEST_PATH).header(HttpHeaders.AUTHORIZATION,
                        bearer("customer@example.test", "customer", -30)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingJwtLeavesRequestAnonymousEvenWithSpoofedIdentityHeaders() throws Exception {
        mockMvc.perform(get(TEST_PATH)
                        .header("X-User-Id", "caller-selected-id")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .header("X-User-Email", "attacker@example.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void spoofedIdentityHeadersCannotOverrideValidatedJwtPrincipal() throws Exception {
        mockMvc.perform(get(TEST_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer("courier@example.test", "courier", 300))
                        .header("X-User-Id", "caller-selected-id")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .header("X-User-Email", "attacker@example.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("courier@example.test"))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_COURIER")))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.not(hasItem("ROLE_ADMIN"))));
    }

    @Test
    void jwtFilterIsRegisteredBeforeAnonymousAndAuthorizationFilters() {
        List<Filter> filters = filterChainProxy.getFilters(TEST_PATH);
        int jwtIndex = indexOf(filters, JwtAuthenticationFilter.class);
        int anonymousIndex = indexOf(filters, AnonymousAuthenticationFilter.class);
        int authorizationIndex = indexOf(filters, AuthorizationFilter.class);

        assertTrue(jwtIndex >= 0, "JWT filter must be in the Spring Security chain");
        assertTrue(anonymousIndex > jwtIndex, "JWT filter must run before anonymous authentication");
        assertTrue(authorizationIndex > jwtIndex, "JWT filter must run before authorization");
    }

    private static int indexOf(List<Filter> filters, Class<?> filterType) {
        for (int i = 0; i < filters.size(); i++) {
            if (filterType.isInstance(filters.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String bearer(String subject, String role, long expiryOffsetSeconds) {
        return bearer(subject, role, expiryOffsetSeconds, SECRET);
    }

    private static String bearer(String subject, String role, long expiryOffsetSeconds, String signingSecret) {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8));
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
        TestPrincipalEndpoint testPrincipalEndpoint() {
            return new TestPrincipalEndpoint();
        }
    }

    @RestController
    static class TestPrincipalEndpoint {

        @GetMapping(TEST_PATH)
        PrincipalView getPrincipal() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                return new PrincipalView(false, null, List.of());
            }
            boolean authenticated = authentication.isAuthenticated()
                    && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
            return new PrincipalView(authenticated, authentication.getName(),
                    authentication.getAuthorities().stream().map(authority -> authority.getAuthority()).toList());
        }
    }

    record PrincipalView(boolean authenticated, String name, List<String> roles) {
    }
}
