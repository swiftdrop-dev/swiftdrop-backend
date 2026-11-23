package com.swiftdrop.parcel.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ParcelStatusUpdateRequest(
        @NotBlank(message = "Status is required")
        String status,

        String failureReason
) {
}
