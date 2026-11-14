package com.swiftdrop.parcel.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;

/** Fail-closed policy until owners approve proof expiry and delivered_at UTC-naive semantics. */
@Component
public class UnapprovedDeliveryCompletionPolicy implements DeliveryCompletionPolicy {

    @Override
    public LocalDateTime validateProofsAndMapDeliveredAt(Instant otpChallengeExpiresAt,
                                                          Instant otpVerifiedAt,
                                                          Instant photoUploadedAt,
                                                          Instant photoVerifiedAt,
                                                          Instant completedAt) {
        throw new DeliveryCompletionPolicyNotApprovedException();
    }
}
