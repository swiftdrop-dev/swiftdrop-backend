package com.swiftdrop.parcel.dto.response;

import java.time.Instant;

public record ParcelAssignmentHistoryResponse(
        Long id,
        String courierId,
        Instant assignedAt,
        Instant unassignedAt,
        String changedBy,
        String reason
) {
}
