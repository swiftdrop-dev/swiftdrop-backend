package com.swiftdrop.parcel.dto.response;

import java.time.Instant;

public record ParcelStatusUpdateResponse(
        String parcelId,
        String previousStatus,
        String status,
        Instant changedAt
) {
}
