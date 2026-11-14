package com.swiftdrop.parcel.otp;

import java.time.Instant;
import java.util.UUID;

/** Package-private handoff between the transactional writer and delivery coordinator. */
record PreparedOtpDelivery(OtpChallengeReceipt receipt, String parcelId,
                           String recipientPhone, char[] code) {
    UUID challengeId() { return receipt.challengeId(); }
    Instant expiresAt() { return receipt.expiresAt(); }

    @Override
    public String toString() {
        return "PreparedOtpDelivery[challengeId=" + challengeId() + ", parcelId=" + parcelId
                + ", recipientPhone=[REDACTED], code=[REDACTED]]";
    }
}
