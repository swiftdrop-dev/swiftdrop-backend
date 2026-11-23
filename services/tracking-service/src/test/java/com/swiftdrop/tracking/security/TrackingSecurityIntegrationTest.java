package com.swiftdrop.tracking.security;

import com.swiftdrop.tracking.dto.response.CustomerTrackingResponse;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import com.swiftdrop.tracking.dto.response.TrackingTimelineItemDto;
import com.swiftdrop.tracking.exception.AccessDeniedException;
import com.swiftdrop.tracking.service.TrackingQueryService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TrackingSecurityIntegrationTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrackingQueryService trackingQueryService;

    private String createToken(String role, String userId, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(userId)
                .claim("userId", userId)
                .claim("email", role + "@swiftdrop.lk")
                .claim("role", role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(KEY)
                .compact();
    }

    private String validToken(String role, String userId) {
        return createToken(role, userId, Instant.now().minus(1, ChronoUnit.MINUTES), Instant.now().plus(1, ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("Direct access: Missing JWT on detailed tracking endpoint is rejected with 401")
    void missingJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/TRK-001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Invalid JWT on detailed tracking endpoint is rejected with 401")
    void invalidJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/TRK-001")
                        .header("Authorization", "Bearer invalid-garbage-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Expired JWT on detailed tracking endpoint is rejected with 401")
    void expiredJwtReturns401() throws Exception {
        String expired = createToken("admin", UUID.randomUUID().toString(),
                Instant.now().minus(2, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS));
        mockMvc.perform(get("/api/v1/tracking/TRK-001")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Customer JWT accessing another parcel is rejected with 403")
    void customerJwtDeniedOnOtherParcelReturns403() throws Exception {
        String customerId = UUID.randomUUID().toString();
        when(trackingQueryService.getCustomerTracking(eq("TRK-001"), eq(customerId), eq("ROLE_CUSTOMER")))
                .thenThrow(new AccessDeniedException("Access denied: You do not have permission to view detailed tracking for this parcel"));

        mockMvc.perform(get("/api/v1/tracking/TRK-001")
                        .header("Authorization", "Bearer " + validToken("customer", customerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles: ROLE_ADMIN without token cannot elevate (401)")
    void forgedHeaderWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/TRK-001")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles: ROLE_ADMIN with Customer token cannot elevate to admin (403)")
    void forgedHeaderWithCustomerTokenReturns403() throws Exception {
        String customerId = UUID.randomUUID().toString();
        when(trackingQueryService.getCustomerTracking(eq("TRK-001"), eq(customerId), eq("ROLE_CUSTOMER")))
                .thenThrow(new AccessDeniedException("Access denied: You do not have permission to view detailed tracking for this parcel"));

        mockMvc.perform(get("/api/v1/tracking/TRK-001")
                        .header("Authorization", "Bearer " + validToken("customer", customerId))
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Valid Admin JWT succeeds on detailed tracking (200 OK)")
    void validAdminJwtSucceeds() throws Exception {
        String adminId = UUID.randomUUID().toString();
        CustomerTrackingResponse response = new CustomerTrackingResponse(
                "TRK-001", "prc_001", "delivered", Instant.now(), List.of()
        );
        when(trackingQueryService.getCustomerTracking(eq("TRK-001"), eq(adminId), eq("ROLE_ADMIN")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/tracking/TRK-001")
                        .header("Authorization", "Bearer " + validToken("admin", adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRK-001"));
    }

    @Test
    @DisplayName("Direct access: Public tracking endpoint accessible without token (200 OK)")
    void publicTrackingAccessibleWithoutToken() throws Exception {
        PublicTrackingResponse response = new PublicTrackingResponse(
                "TRK-001", "in_transit", Instant.now(), List.of()
        );
        when(trackingQueryService.getPublicTracking("TRK-001")).thenReturn(response);

        mockMvc.perform(get("/api/v1/tracking/public/TRK-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRK-001"));
    }

    @Test
    @DisplayName("Direct access: Public actuator health accessible without token (200 OK)")
    void publicActuatorHealthAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
