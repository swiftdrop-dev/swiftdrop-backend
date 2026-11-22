package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.otp.OtpVerificationOutcome;
import java.time.Instant;
import java.util.UUID;

public record OtpVerificationResponse(
        UUID challengeId,
        OtpVerificationOutcome outcome,
        Instant verifiedAt
) {
}
