package com.swiftdrop.parcel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParcelAssignmentRequest(
        @NotBlank @Size(max = 64) String courierId,
        @Size(max = 1000) String reason
) {
}
