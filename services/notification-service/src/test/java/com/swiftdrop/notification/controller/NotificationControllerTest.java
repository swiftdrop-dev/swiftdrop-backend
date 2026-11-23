package com.swiftdrop.notification.controller;

import com.swiftdrop.notification.dto.response.NotificationPageResponse;
import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.exception.AccessDeniedException;
import com.swiftdrop.notification.exception.GlobalExceptionHandler;
import com.swiftdrop.notification.exception.ResourceNotFoundException;
import com.swiftdrop.notification.exception.UnauthorizedException;
import com.swiftdrop.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void getNotifications_returns200WithPagedContent() throws Exception {
        NotificationResponse dto = new NotificationResponse(
                "notif-1", "evt-1", "pcl-1", "SWD-12345", "cust-1",
                "EMAIL", "BOOKING_CONFIRMED", "Booking Confirmed",
                "Your booking is confirmed", "SENT", false, null, Instant.now(), Instant.now()
        );
        NotificationPageResponse<NotificationResponse> pageResponse = new NotificationPageResponse<>(
                List.of(dto), 0, 20, 1, 1, false, 1
        );

        when(notificationService.getCustomerNotifications(eq("cust-1"), eq("ROLE_CUSTOMER"), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", "cust-1")
                        .header("X-User-Roles", "ROLE_CUSTOMER")
                        .param("page", "0")
                        .param("size", "20")
                        .param("unreadOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("notif-1"))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SWD-12345"))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void getNotifications_returns401WhenAnonymous() throws Exception {
        when(notificationService.getCustomerNotifications(null, null, 0, 20, null))
                .thenThrow(new UnauthorizedException("Authentication required"));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void getNotificationById_returns200WhenFound() throws Exception {
        NotificationResponse dto = new NotificationResponse(
                "notif-1", "evt-1", "pcl-1", "SWD-12345", "cust-1",
                "EMAIL", "BOOKING_CONFIRMED", "Booking Confirmed",
                "Your booking is confirmed", "SENT", true, Instant.now(), Instant.now(), Instant.now()
        );

        when(notificationService.getNotificationById("notif-1", "cust-1", "ROLE_CUSTOMER"))
                .thenReturn(dto);

        mockMvc.perform(get("/api/v1/notifications/notif-1")
                        .header("X-User-Id", "cust-1")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("notif-1"))
                .andExpect(jsonPath("$.isRead").value(true));
    }

    @Test
    void getNotificationById_returns403WhenAccessDenied() throws Exception {
        when(notificationService.getNotificationById("notif-1", "cust-2", "ROLE_CUSTOMER"))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/api/v1/notifications/notif-1")
                        .header("X-User-Id", "cust-2")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void getNotificationById_returns404WhenNotFound() throws Exception {
        when(notificationService.getNotificationById("notif-999", "cust-1", "ROLE_CUSTOMER"))
                .thenThrow(new ResourceNotFoundException("Notification not found with id: notif-999"));

        mockMvc.perform(get("/api/v1/notifications/notif-999")
                        .header("X-User-Id", "cust-1")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void markAsRead_returns200WithUpdatedNotification() throws Exception {
        NotificationResponse dto = new NotificationResponse(
                "notif-1", "evt-1", "pcl-1", "SWD-12345", "cust-1",
                "EMAIL", "BOOKING_CONFIRMED", "Booking Confirmed",
                "Your booking is confirmed", "SENT", true, Instant.now(), Instant.now(), Instant.now()
        );

        when(notificationService.markAsRead("notif-1", "cust-1", "ROLE_CUSTOMER"))
                .thenReturn(dto);

        mockMvc.perform(patch("/api/v1/notifications/notif-1/read")
                        .header("X-User-Id", "cust-1")
                        .header("X-User-Roles", "ROLE_CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));
    }

    @Test
    void markAllAsRead_returns200WithCount() throws Exception {
        when(notificationService.markAllAsRead("cust-1")).thenReturn(3L);

        mockMvc.perform(post("/api/v1/notifications/mark-all-read")
                        .header("X-User-Id", "cust-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.markedCount").value(3));
    }

    @Test
    void getUnreadCount_returns200WithCount() throws Exception {
        when(notificationService.getUnreadCount("cust-1")).thenReturn(4L);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("X-User-Id", "cust-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(4));
    }
}
