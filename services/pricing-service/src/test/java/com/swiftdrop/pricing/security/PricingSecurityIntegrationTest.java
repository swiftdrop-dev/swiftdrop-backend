package com.swiftdrop.pricing.security;

import com.swiftdrop.pricing.client.ParcelServiceClient;
import com.swiftdrop.pricing.dto.response.ZoneDto;
import com.swiftdrop.pricing.service.ZoneService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PricingSecurityIntegrationTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ZoneService zoneService;

    @MockBean
    private ParcelServiceClient parcelServiceClient;

    private String createToken(String role, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("userId", UUID.randomUUID().toString())
                .claim("email", role + "@swiftdrop.lk")
                .claim("role", role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(KEY)
                .compact();
    }

    private String validToken(String role) {
        return createToken(role, Instant.now().minus(1, ChronoUnit.MINUTES), Instant.now().plus(1, ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("Direct access: Missing JWT on admin endpoint is rejected with 401")
    void missingJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/zones/admin/all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Invalid JWT on admin endpoint is rejected with 401")
    void invalidJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/zones/admin/all")
                        .header("Authorization", "Bearer invalid-garbage-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Expired JWT on admin endpoint is rejected with 401")
    void expiredJwtReturns401() throws Exception {
        String expired = createToken("admin", Instant.now().minus(2, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS));
        mockMvc.perform(get("/api/v1/zones/admin/all")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Customer JWT cannot access admin endpoint (403)")
    void customerJwtReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/zones/admin/all")
                        .header("Authorization", "Bearer " + validToken("customer")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Dispatcher JWT cannot access admin endpoint (403)")
    void dispatcherJwtReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/zones/admin/all")
                        .header("Authorization", "Bearer " + validToken("dispatcher")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Courier JWT cannot access admin endpoint (403)")
    void courierJwtReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/zones/admin/all")
                        .header("Authorization", "Bearer " + validToken("courier")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles: ROLE_ADMIN without token cannot elevate (401)")
    void forgedHeaderWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/zones/admin/all")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles: ROLE_ADMIN with Customer token cannot elevate (403)")
    void forgedHeaderWithCustomerTokenReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/zones/admin/all")
                        .header("Authorization", "Bearer " + validToken("customer"))
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles: ROLE_ADMIN with Dispatcher token cannot elevate (403)")
    void forgedHeaderWithDispatcherTokenReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/zones/admin/all")
                        .header("Authorization", "Bearer " + validToken("dispatcher"))
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Valid Admin JWT succeeds (200 OK)")
    void validAdminJwtSucceeds() throws Exception {
        when(zoneService.getAllZones()).thenReturn(List.of(new ZoneDto(1L, "Colombo", "CMB", true, null)));

        mockMvc.perform(get("/api/v1/zones/admin/all")
                        .header("Authorization", "Bearer " + validToken("admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Direct access: Public active zones endpoint accessible without token (200 OK)")
    void publicZonesAccessibleWithoutToken() throws Exception {
        when(zoneService.getAllActiveZones()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/zones"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Direct access: Public actuator health accessible without token (200 OK)")
    void publicActuatorHealthAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
