package com.swiftdrop.notification.service;

import com.swiftdrop.notification.dto.response.NotificationPageResponse;
import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.exception.AccessDeniedException;
import com.swiftdrop.notification.exception.ResourceNotFoundException;
import com.swiftdrop.notification.exception.UnauthorizedException;
import com.swiftdrop.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse<NotificationResponse> getCustomerNotifications(
            String userId, String userRoles, int page, int size, Boolean unreadOnly) {

        validateAuthentication(userId);

        if (page < 0) page = 0;
        if (size < 1 || size > 100) size = 20;

        var pageable = PageRequest.of(page, size);
        Page<Notification> resultPage;

        if (Boolean.TRUE.equals(unreadOnly)) {
            resultPage = repository.findByRecipientIdAndIsReadOrderByCreatedAtDesc(userId, false, pageable);
        } else {
            resultPage = repository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);
        }

        long unreadCount = repository.countByRecipientIdAndIsRead(userId, false);

        List<NotificationResponse> dtos = resultPage.getContent().stream()
                .map(this::toResponseDto)
                .toList();

        return new NotificationPageResponse<>(
                dtos,
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                resultPage.hasNext(),
                unreadCount
        );
    }

    @Transactional(readOnly = true)
    public NotificationResponse getNotificationById(String id, String userId, String userRoles) {
        validateAuthentication(userId);

        Notification notification = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));

        verifyOwnership(notification, userId, userRoles);

        return toResponseDto(notification);
    }

    @Transactional
    public NotificationResponse markAsRead(String id, String userId, String userRoles) {
        validateAuthentication(userId);

        Notification notification = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));

        verifyOwnership(notification, userId, userRoles);

        notification.markAsRead();
        Notification updated = repository.save(notification);
        log.info("Marked notification [{}] as read for recipient [{}]", id, userId);

        return toResponseDto(updated);
    }

    @Transactional
    public long markAllAsRead(String userId) {
        validateAuthentication(userId);

        List<Notification> unreadList = repository.findByRecipientIdAndIsReadFalse(userId);
        if (unreadList.isEmpty()) {
            return 0;
        }

        for (Notification n : unreadList) {
            n.markAsRead();
        }
        repository.saveAll(unreadList);
        log.info("Marked all [{}] notifications as read for recipient [{}]", unreadList.size(), userId);

        return unreadList.size();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        validateAuthentication(userId);
        return repository.countByRecipientIdAndIsRead(userId, false);
    }

    private void validateAuthentication(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Authentication required to access notification resources");
        }
    }

    private void verifyOwnership(Notification notification, String userId, String userRoles) {
        boolean isPrivileged = userRoles != null && (
                userRoles.toLowerCase().contains("admin") ||
                userRoles.toLowerCase().contains("dispatcher") ||
                userRoles.toLowerCase().contains("staff")
        );

        if (isPrivileged) {
            return;
        }

        if (notification.getRecipientId() == null || !notification.getRecipientId().trim().equalsIgnoreCase(userId.trim())) {
            log.warn("Access denied: user [{}] attempted to access notification [{}] owned by [{}]",
                    userId, notification.getId(), notification.getRecipientId());
            throw new AccessDeniedException("Access denied: You do not have permission to access this notification");
        }
    }

    public NotificationResponse toResponseDto(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getEventId(),
                n.getParcelId(),
                n.getTrackingNumber(),
                n.getRecipientId(),
                n.getChannel() != null ? n.getChannel().name() : null,
                n.getNotificationType() != null ? n.getNotificationType().name() : null,
                n.getSubject(),
                n.getMessage(),
                n.getDeliveryStatus() != null ? n.getDeliveryStatus().name() : null,
                n.isRead(),
                n.getReadAt(),
                n.getCreatedAt(),
                n.getSentAt()
        );
    }
}
