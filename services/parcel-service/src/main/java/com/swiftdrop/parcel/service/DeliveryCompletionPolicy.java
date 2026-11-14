package com.swiftdrop.parcel.service;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Approval boundary for the two unresolved production policies.
 * Implementations must enforce the approved validity/expiry rules for both proofs and
 * convert the completion instant to the approved representation for delivered_at.
 */
public interface DeliveryCompletionPolicy {

    LocalDateTime validateProofsAndMapDeliveredAt(Instant otpChallengeExpiresAt,
                                                  Instant otpVerifiedAt,
                                                  Instant photoUploadedAt,
                                                  Instant photoVerifiedAt,
                                                  Instant completedAt);
}
