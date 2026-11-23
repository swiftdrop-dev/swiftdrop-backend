package com.swiftdrop.notification.controller;

import com.swiftdrop.notification.dto.response.NotificationPageResponse;
import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.dto.response.UnreadCountResponse;
import com.swiftdrop.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private String resolveUserId(String headerUserId) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.swiftdrop.notification.security.UserPrincipal principal) {
            return principal.getId() != null ? principal.getId().toString() : principal.getEmail();
        }
        return headerUserId;
    }

    private String resolveUserRoles(String headerRoles) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.swiftdrop.notification.security.UserPrincipal principal) {
            return "ROLE_" + principal.getRole().toUpperCase(java.util.Locale.ROOT);
        }
        return headerRoles;
    }

    @GetMapping
    public ResponseEntity<NotificationPageResponse<NotificationResponse>> getNotifications(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean unreadOnly) {

        NotificationPageResponse<NotificationResponse> response =
                notificationService.getCustomerNotifications(resolveUserId(userId), resolveUserRoles(userRoles), page, size, unreadOnly);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> getNotificationById(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {

        NotificationResponse response = notificationService.getNotificationById(id, resolveUserId(userId), resolveUserRoles(userRoles));
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {

        NotificationResponse response = notificationService.markAsRead(id, resolveUserId(userId), resolveUserRoles(userRoles));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Map<String, Object>> markAllAsRead(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        long count = notificationService.markAllAsRead(resolveUserId(userId));
        return ResponseEntity.ok(Map.of(
                "success", true,
                "markedCount", count
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        long count = notificationService.getUnreadCount(resolveUserId(userId));
        return ResponseEntity.ok(new UnreadCountResponse(count));
    }
}
