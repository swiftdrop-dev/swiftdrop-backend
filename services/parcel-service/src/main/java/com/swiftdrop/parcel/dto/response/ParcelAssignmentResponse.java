package com.swiftdrop.parcel.dto.response;

import java.time.Instant;

public record ParcelAssignmentResponse(
        Long id,
        String parcelId,
        String courierId,
        Instant assignedAt,
        Instant unassignedAt
) {
}
