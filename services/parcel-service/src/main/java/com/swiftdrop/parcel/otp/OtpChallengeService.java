package com.swiftdrop.parcel.otp;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/** OTP orchestration without an HTTP or notification-provider implementation. */
@Service
public class OtpChallengeService {
    private final OtpChallengeWriter writer;
    private final Optional<OtpDeliveryGateway> deliveryGateway;

    public OtpChallengeService(OtpChallengeWriter writer, Optional<OtpDeliveryGateway> deliveryGateway) {
        this.writer = writer;
        this.deliveryGateway = deliveryGateway;
    }

    /**
     * The OTP is passed only to the private delivery port and never returned.
     * Without a configured delivery adapter this fails before creating a challenge.
     */
    public OtpChallengeReceipt issue(String parcelId, String trustedCourierId) {
        OtpDeliveryGateway gateway = deliveryGateway.orElseThrow(OtpDeliveryUnavailableException::new);
        PreparedOtpDelivery prepared = writer.prepare(parcelId, trustedCourierId);
        try {
            gateway.sendOtp(new OtpDeliveryCommand(prepared.challengeId(), prepared.parcelId(),
                    prepared.recipientPhone(), prepared.code(), prepared.expiresAt()));
            return prepared.receipt();
        } catch (RuntimeException deliveryFailure) {
            writer.invalidateAfterDeliveryFailure(prepared.parcelId(), prepared.challengeId());
            throw new OtpDeliveryUnavailableException();
        } finally {
            Arrays.fill(prepared.code(), '\0');
        }
    }

    public OtpVerificationResult verify(String parcelId, UUID challengeId,
                                        String trustedCourierId, char[] code) {
        try {
            return writer.verify(parcelId, challengeId, trustedCourierId, code);
        } finally {
            if (code != null) {
                Arrays.fill(code, '\0');
            }
        }
    }
}
