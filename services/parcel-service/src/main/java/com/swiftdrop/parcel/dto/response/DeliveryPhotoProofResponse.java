package com.swiftdrop.parcel.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DeliveryPhotoProofResponse(
        UUID proofId,
        String format,
        long byteSize,
        Instant uploadedAt
) {
}
