package com.swiftdrop.notification.security;

import com.swiftdrop.notification.dto.response.NotificationPageResponse;
import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.exception.AccessDeniedException;
import com.swiftdrop.notification.service.NotificationService;
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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationSecurityIntegrationTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

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
    @DisplayName("Direct access: Missing JWT on notifications endpoint is rejected with 401")
    void missingJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Invalid JWT on notifications endpoint is rejected with 401")
    void invalidJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer invalid-garbage-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Expired JWT on notifications endpoint is rejected with 401")
    void expiredJwtReturns401() throws Exception {
        String expired = createToken("admin", UUID.randomUUID().toString(),
                Instant.now().minus(2, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS));
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Customer JWT accessing another customer's notifications is rejected with 403")
    void customerJwtDeniedOnOtherNotificationsReturns403() throws Exception {
        String customerId = UUID.randomUUID().toString();
        when(notificationService.getNotificationById(eq("notif-other"), eq(customerId), eq("ROLE_CUSTOMER")))
                .thenThrow(new AccessDeniedException("Access denied: You can only view your own notifications"));

        mockMvc.perform(get("/api/v1/notifications/notif-other")
                        .header("Authorization", "Bearer " + validToken("customer", customerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles: ROLE_ADMIN without token cannot elevate (401)")
    void forgedHeaderWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles: ROLE_ADMIN with Customer token cannot elevate to admin (403)")
    void forgedHeaderWithCustomerTokenReturns403() throws Exception {
        String customerId = UUID.randomUUID().toString();
        when(notificationService.getNotificationById(eq("notif-other"), eq(customerId), eq("ROLE_CUSTOMER")))
                .thenThrow(new AccessDeniedException("Access denied: You can only view your own notifications"));

        mockMvc.perform(get("/api/v1/notifications/notif-other")
                        .header("Authorization", "Bearer " + validToken("customer", customerId))
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Valid Customer JWT succeeds on their notifications (200 OK)")
    void validCustomerJwtSucceeds() throws Exception {
        String customerId = UUID.randomUUID().toString();
        NotificationPageResponse<NotificationResponse> page = new NotificationPageResponse<>(
                List.of(), 0, 20, 0, 0, false, 0
        );
        when(notificationService.getCustomerNotifications(eq(customerId), eq("ROLE_CUSTOMER"), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + validToken("customer", customerId)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Direct access: Valid Admin JWT succeeds (200 OK)")
    void validAdminJwtSucceeds() throws Exception {
        String adminId = UUID.randomUUID().toString();
        NotificationPageResponse<NotificationResponse> page = new NotificationPageResponse<>(
                List.of(), 0, 20, 0, 0, false, 0
        );
        when(notificationService.getCustomerNotifications(eq(adminId), eq("ROLE_ADMIN"), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + validToken("admin", adminId)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Direct access: Public actuator health accessible without token (200 OK)")
    void publicActuatorHealthAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
