package com.swiftdrop.notification.dto.response;

import java.time.Instant;

public record NotificationResponse(
        String id,
        String eventId,
        String parcelId,
        String trackingNumber,
        String recipientId,
        String channel,
        String notificationType,
        String subject,
        String message,
        String deliveryStatus,
        boolean isRead,
        Instant readAt,
        Instant createdAt,
        Instant sentAt
) {}
