package com.swiftdrop.courier.config;

import com.swiftdrop.courier.dto.response.CourierResponse;
import com.swiftdrop.courier.service.CourierService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import com.swiftdrop.courier.security.DispatchServiceAuthenticationFilter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CourierSecurityIntegrationTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourierService courierService;

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
    @DisplayName("Direct access: Missing JWT on protected endpoint /me returns 401")
    void missingJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/couriers/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Invalid JWT on protected endpoint /me returns 401")
    void invalidJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/couriers/me")
                        .header("Authorization", "Bearer invalid-garbage-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Expired JWT on protected endpoint /me returns 401")
    void expiredJwtReturns401() throws Exception {
        String expired = createToken("courier", Instant.now().minus(2, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS));
        mockMvc.perform(get("/api/v1/couriers/me")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Customer JWT cannot access courier /me endpoint (403)")
    void customerJwtReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/couriers/me")
                        .header("Authorization", "Bearer " + validToken("customer")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles: ROLE_ADMIN without token cannot elevate (401)")
    void forgedHeaderWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/couriers/me")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles: ROLE_ADMIN with Customer token cannot elevate (403)")
    void forgedHeaderWithCustomerTokenReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/couriers/me")
                        .header("Authorization", "Bearer " + validToken("customer"))
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Valid Courier JWT succeeds on /me (200 OK)")
    void validCourierJwtSucceeds() throws Exception {
        when(courierService.getCourierByUserId(anyString())).thenReturn(new CourierResponse(
                "cou_001", "Nimal Senanayake", "motorcycle", "ABC-1234",
                4.8, true, 3, 120));

        mockMvc.perform(get("/api/v1/couriers/me")
                        .header("Authorization", "Bearer " + validToken("courier")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cou_001"));
    }

    @Test
    @DisplayName("Direct access: Valid Admin JWT succeeds on /me (200 OK)")
    void validAdminJwtSucceeds() throws Exception {
        when(courierService.getCourierByUserId(anyString())).thenReturn(new CourierResponse(
                "cou_001", "Admin Courier", "van", "XYZ-9999",
                5.0, true, 0, 50));

        mockMvc.perform(get("/api/v1/couriers/me")
                        .header("Authorization", "Bearer " + validToken("admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Directory: anonymous, customer, and courier callers cannot list couriers")
    void directoryRequiresDispatcherOrAdmin() throws Exception {
        when(courierService.getAllCouriers()).thenReturn(java.util.List.of(new CourierResponse(
                "cou_001", "Nimal Senanayake", "motorcycle", "ABC-1234",
                4.8, true, 3, 120)));
        when(courierService.getCourierById("cou_001")).thenReturn(new CourierResponse(
                "cou_001", "Nimal Senanayake", "motorcycle", "ABC-1234",
                4.8, true, 3, 120));
        mockMvc.perform(get("/api/v1/couriers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/couriers").header("Authorization", "Bearer " + validToken("customer")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/couriers").header("Authorization", "Bearer " + validToken("courier")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/couriers").header("Authorization", "Bearer " + validToken("dispatcher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cou_001"))
                .andExpect(jsonPath("$[0].phone").doesNotExist());
        mockMvc.perform(get("/api/v1/couriers/cou_001").header("Authorization", "Bearer " + validToken("admin")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/couriers/cou_001")
                        .header(DispatchServiceAuthenticationFilter.HEADER, "test-dispatch-service-credential")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Public actuator health succeeds without auth (200 OK)")
    void publicActuatorHealthSucceedsWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
