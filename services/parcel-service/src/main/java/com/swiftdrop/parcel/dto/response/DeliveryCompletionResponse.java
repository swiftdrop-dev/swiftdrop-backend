package com.swiftdrop.parcel.dto.response;

import java.time.Instant;

public record DeliveryCompletionResponse(
        String parcelId,
        String status,
        Instant completedAt
) {
}
