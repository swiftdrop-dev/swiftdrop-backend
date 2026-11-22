package com.swiftdrop.parcel.dto.response;

import java.time.Instant;

public record AssignedDelivery(
        String parcelId,
        Long assignmentId,
        String trackingNumber,
        String status,
        String scheduledDate,
        Instant assignedAt,
        String recipientName,
        String recipientPhone,
        String deliveryAddress
) {
}
