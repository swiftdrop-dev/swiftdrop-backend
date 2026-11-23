package com.swiftdrop.parcel.otp;

import java.time.Instant;
import java.util.UUID;

/** Safe issue result; deliberately contains neither OTP nor HMAC. */
public record OtpChallengeReceipt(UUID challengeId, Instant expiresAt,
                                  Instant resendAvailableAt, String destinationMasked) {
}
