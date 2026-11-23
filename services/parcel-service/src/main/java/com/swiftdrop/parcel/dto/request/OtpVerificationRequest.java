package com.swiftdrop.parcel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OtpVerificationRequest(
        @NotNull(message = "challengeId is required")
        UUID challengeId,

        @NotBlank(message = "code is required")
        String code
) {
}
