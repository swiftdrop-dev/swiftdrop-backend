package com.swiftdrop.parcel.dto.response;

public record ParcelDeliveryResponse(
        String trackingNumber,
        String status,
        String scheduledDate,
        String recipientName,
        String recipientPhone,
        String recipientAddress
) {
}
