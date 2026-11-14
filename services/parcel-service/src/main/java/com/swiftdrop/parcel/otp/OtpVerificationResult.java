package com.swiftdrop.parcel.otp;

import java.time.Instant;
import java.util.UUID;

/** Contains verification state only. It never includes the submitted OTP or its hash. */
public record OtpVerificationResult(UUID challengeId, OtpVerificationOutcome outcome,
                                    Instant verifiedAt) {
}
