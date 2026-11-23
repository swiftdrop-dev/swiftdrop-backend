package com.swiftdrop.parcel.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DeliveryCompletionRequest(
        @NotNull(message = "challengeId is required")
        UUID challengeId,

        @NotNull(message = "photoProofId is required")
        UUID photoProofId
) {
}
