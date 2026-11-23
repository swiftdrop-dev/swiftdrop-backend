package com.swiftdrop.notification.dto.response;

import java.util.List;

public record NotificationPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        long unreadCount
) {}
