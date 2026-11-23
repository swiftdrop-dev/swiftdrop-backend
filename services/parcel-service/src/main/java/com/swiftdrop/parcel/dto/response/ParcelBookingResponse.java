package com.swiftdrop.parcel.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ParcelBookingResponse(
        String id,
        String trackingNumber,
        String status,
        String senderId,
        String senderName,
        String senderPhone,
        String recipientName,
        String recipientPhone,
        String recipientAddress,
        String pickupAddress,
        BigDecimal weightKg,
        String parcelSize,
        String description,
        Boolean fragile,
        BigDecimal totalFee,
        String feeStatus,
        String paymentMethod,
        String paymentStatus,
        String courierId,
        String estimatedDeliveryDate,
        Instant createdAt,
        Instant updatedAt
) {
}
