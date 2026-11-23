package com.swiftdrop.reporting.security;

import com.swiftdrop.reporting.dto.response.DashboardKpisResponse;
import com.swiftdrop.reporting.dto.response.MerchantVolumeResponse;
import com.swiftdrop.reporting.service.ReportingAggregationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReportingSecurityIntegrationTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportingAggregationService aggregationService;

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
    @DisplayName("Direct access: Missing JWT is rejected with 401")
    void missingJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/reports/dashboard-kpis"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Invalid JWT is rejected with 401")
    void invalidJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/reports/dashboard-kpis")
                        .header("Authorization", "Bearer invalid-garbage-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Expired JWT is rejected with 401")
    void expiredJwtReturns401() throws Exception {
        String expired = createToken("admin", Instant.now().minus(2, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS));
        mockMvc.perform(get("/api/v1/reports/dashboard-kpis")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Customer JWT cannot access reporting (403)")
    void customerJwtDeniedReporting() throws Exception {
        String token = validToken("customer");
        mockMvc.perform(get("/api/v1/reports/dashboard-kpis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Courier JWT cannot access reporting (403)")
    void courierJwtDeniedReporting() throws Exception {
        String token = validToken("courier");
        mockMvc.perform(get("/api/v1/reports/dashboard-kpis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles header without valid JWT cannot elevate privileges (401)")
    void forgedHeaderWithoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/reports/dashboard-kpis")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles header with Customer JWT cannot elevate privileges (403)")
    void forgedHeaderWithCustomerJwtReturns403() throws Exception {
        String token = validToken("customer");
        mockMvc.perform(get("/api/v1/reports/dashboard-kpis")
                        .header("Authorization", "Bearer " + token)
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Dispatcher JWT can access operational dashboard KPIs")
    void dispatcherJwtCanAccessDashboardKpis() throws Exception {
        String token = validToken("dispatcher");
        DashboardKpisResponse mockResponse = new DashboardKpisResponse(
                50L, 10L, 4L, 8L, 15L, 20L, 5L, 8L, 12L, null, null, null, 45000.0, 80.0, 3.8, true, Map.of(), Instant.now()
        );
        when(aggregationService.getDashboardKpis(eq("ROLE_DISPATCHER"), any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/reports/dashboard-kpis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Direct access: Dispatcher JWT cannot access admin-only merchant volume (403)")
    void dispatcherJwtDeniedMerchantVolume() throws Exception {
        String token = validToken("dispatcher");
        mockMvc.perform(get("/api/v1/reports/merchant-volume")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles: ROLE_ADMIN with Dispatcher JWT cannot access merchant volume (403)")
    void forgedAdminHeaderWithDispatcherJwtDeniedMerchantVolume() throws Exception {
        String token = validToken("dispatcher");
        mockMvc.perform(get("/api/v1/reports/merchant-volume")
                        .header("Authorization", "Bearer " + token)
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Admin JWT can access dashboard KPIs and merchant volume")
    void adminJwtCanAccessAllReports() throws Exception {
        String token = validToken("admin");
        when(aggregationService.getDashboardKpis(eq("ROLE_ADMIN"), any())).thenReturn(
                new DashboardKpisResponse(50L, 10L, 4L, 8L, 15L, 20L, 5L, 8L, 12L, 35L, 15L, 2L, 45000.0, 80.0, 3.8, false, Map.of(), Instant.now())
        );
        when(aggregationService.getMerchantVolume(eq("ROLE_ADMIN"), any())).thenReturn(
                new MerchantVolumeResponse(1, 0, List.of())
        );

        mockMvc.perform(get("/api/v1/reports/dashboard-kpis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reports/merchant-volume")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Direct access: Public actuator endpoint is accessible without authentication")
    void publicActuatorHealthAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
