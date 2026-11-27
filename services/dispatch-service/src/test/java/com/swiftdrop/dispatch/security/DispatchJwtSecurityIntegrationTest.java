package com.swiftdrop.dispatch.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(DispatchJwtSecurityIntegrationTest.ProbeConfiguration.class)
class DispatchJwtSecurityIntegrationTest {
    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Autowired MockMvc mockMvc;

    @Test
    void publicHealthRemainsAvailableAnonymously() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void anonymousProtectedRequestRemainsAnonymousAndIsRejected() throws Exception {
        mockMvc.perform(get("/security-test/principal")).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenEstablishesEmailPrincipalAndLowercaseRoleAuthority() throws Exception {
        mockMvc.perform(get("/security-test/principal").header(HttpHeaders.AUTHORIZATION, bearer("dispatcher", Instant.now().plusSeconds(300))))
                .andExpect(status().isOk()).andExpect(content().string("dispatcher@example.test:ROLE_DISPATCHER"));
    }

    @Test
    void invalidAndExpiredTokensReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/security-test/principal").header(HttpHeaders.AUTHORIZATION, "Bearer malformed"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/security-test/principal").header(HttpHeaders.AUTHORIZATION, bearer("courier", Instant.now().minusSeconds(1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void spoofedIdentityHeadersDoNotAuthenticate() throws Exception {
        mockMvc.perform(get("/security-test/principal")
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000001")
                        .header("X-User-Roles", "dispatcher")
                        .header("X-User-Email", "attacker@example.test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ambiguousAuthorizationHeadersAreRejected() throws Exception {
        mockMvc.perform(get("/security-test/principal")
                        .header(HttpHeaders.AUTHORIZATION, bearer("dispatcher", Instant.now().plusSeconds(300)), "Bearer second"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthorizedCallersCannotAssignCouriers() throws Exception {
        String body = "{\"courierId\":\"00000000-0000-0000-0000-000000000002\",\"dispatcherNotes\":\"offer\"}";
        mockMvc.perform(post("/api/v1/dispatch/jobs/00000000-0000-0000-0000-000000000001/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/dispatch/jobs/00000000-0000-0000-0000-000000000001/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer("customer", Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/dispatch/jobs/00000000-0000-0000-0000-000000000001/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer("courier", Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyDispatchersCanUseDispatchManagementRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/jobs/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer("customer", Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dispatch/jobs/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer("dispatcher", Instant.now().plusSeconds(300))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/dispatch/jobs/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin", Instant.now().plusSeconds(300))))
                .andExpect(status().isOk());
    }

    @Test
    void courierRoutesRequireCourierRoleAndProjectionSyncFailsClosed() throws Exception {
        mockMvc.perform(get("/api/v1/dispatch/courier/jobs/my-jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer("dispatcher", Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/dispatch/projections/couriers/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer("dispatcher", Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden());
    }

    @Test
    void idDependentDispatchOperationFailsClosedWithoutAuthoritativeMapping() throws Exception {
        mockMvc.perform(post("/api/v1/dispatch/jobs/00000000-0000-0000-0000-000000000001/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer("dispatcher", Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dispatch/courier/jobs/my-jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer("courier", Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden());
    }

    private String bearer(String role, Instant expiry) {
        String token = Jwts.builder().subject("dispatcher@example.test").claim("role", role)
                .issuedAt(Date.from(Instant.now().minusSeconds(2))).expiration(Date.from(expiry))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        return "Bearer " + token;
    }

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean
        SecurityProbe securityProbe() { return new SecurityProbe(); }
    }

    @RestController
    static class SecurityProbe {
        @GetMapping("/security-test/principal")
        String principal() {
            UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return principal.getEmail() + ":" + principal.getAuthorities().iterator().next().getAuthority();
        }
    }
}
