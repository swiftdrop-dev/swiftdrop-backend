package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@Primary
public class StandardDeliveryCompletionPolicy implements DeliveryCompletionPolicy {

    @Override
    public LocalDateTime validateProofsAndMapDeliveredAt(Instant otpChallengeExpiresAt,
                                                          Instant otpVerifiedAt,
                                                          Instant photoUploadedAt,
                                                          Instant photoVerifiedAt,
                                                          Instant completedAt) {
        if (otpVerifiedAt == null || photoVerifiedAt == null) {
            throw new ParcelStatusConflictException("Both OTP and photo proofs must be verified");
        }
        if (otpChallengeExpiresAt != null && completedAt != null && completedAt.isAfter(otpChallengeExpiresAt)) {
            throw new ParcelStatusConflictException("OTP challenge has expired");
        }
        return LocalDateTime.ofInstant(completedAt != null ? completedAt : Instant.now(), ZoneOffset.UTC);
    }
}
