package com.swiftdrop.parcel.otp;

import java.time.Instant;
import java.util.UUID;

/** Transient internal delivery payload. Implementations must not persist or log otpCode. */
public final class OtpDeliveryCommand {
    private final UUID challengeId;
    private final String parcelId;
    private final String recipientPhone;
    private final char[] otpCode;
    private final Instant expiresAt;

    OtpDeliveryCommand(UUID challengeId, String parcelId, String recipientPhone,
                       char[] otpCode, Instant expiresAt) {
        this.challengeId = challengeId;
        this.parcelId = parcelId;
        this.recipientPhone = recipientPhone;
        this.otpCode = otpCode;
        this.expiresAt = expiresAt;
    }

    public UUID challengeId() { return challengeId; }
    public String parcelId() { return parcelId; }
    public String recipientPhone() { return recipientPhone; }
    public char[] otpCode() { return otpCode; }
    public Instant expiresAt() { return expiresAt; }

    @Override
    public String toString() {
        return "OtpDeliveryCommand[challengeId=" + challengeId + ", parcelId=" + parcelId
                + ", recipientPhone=[REDACTED], otpCode=[REDACTED], expiresAt=" + expiresAt + "]";
    }
}
