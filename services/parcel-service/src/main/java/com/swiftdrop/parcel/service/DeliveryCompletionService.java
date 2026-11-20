package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.exception.CourierAssignmentMismatchException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryOtpChallengeRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryPhotoProofRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal-only delivery completion core. trustedCourierId must come from a future
 * approved authenticated principal/service identity; it is never a request credential.
 * The production policy bean currently fails closed, so this operation is not enabled.
 */
@Service
public class DeliveryCompletionService {
    private final ParcelRepository parcelRepository;
    private final ParcelAssignmentRepository assignmentRepository;
    private final ParcelDeliveryOtpChallengeRepository otpChallengeRepository;
    private final ParcelDeliveryPhotoProofRepository photoProofRepository;
    private final ParcelStatusHistoryRepository historyRepository;
    private final ParcelStatusTransitionValidator transitionValidator;
    private final DeliveryCompletionPolicy completionPolicy;
    private final Clock clock;

    public DeliveryCompletionService(ParcelRepository parcelRepository,
                                     ParcelAssignmentRepository assignmentRepository,
                                     ParcelDeliveryOtpChallengeRepository otpChallengeRepository,
                                     ParcelDeliveryPhotoProofRepository photoProofRepository,
                                     ParcelStatusHistoryRepository historyRepository,
                                     ParcelStatusTransitionValidator transitionValidator,
                                     DeliveryCompletionPolicy completionPolicy,
                                     Clock clock) {
        this.parcelRepository = parcelRepository;
        this.assignmentRepository = assignmentRepository;
        this.otpChallengeRepository = otpChallengeRepository;
        this.photoProofRepository = photoProofRepository;
        this.historyRepository = historyRepository;
        this.transitionValidator = transitionValidator;
        this.completionPolicy = completionPolicy;
        this.clock = clock;
    }

    /**
     * Completes one in-transit parcel. The courier ID must already have been resolved
     * from a trusted internal identity; callers must not forward a body/header value as
     * this argument without authenticating its source.
     */
    @Transactional
    public DeliveryCompletionResult complete(String parcelId, UUID challengeId, UUID photoProofId,
                                             String trustedCourierId) {
        if (parcelId == null || challengeId == null || photoProofId == null
                || trustedCourierId == null || trustedCourierId.isBlank()) {
            throw new IllegalArgumentException("Parcel, proof identifiers, and trusted courier identity are required");
        }

        // Global lock order: parcel -> active assignment -> OTP challenge -> photo proof.
        var parcel = parcelRepository.findByIdForUpdate(parcelId)
                .orElseThrow(() -> new ParcelNotFoundException(parcelId));
        var assignment = assignmentRepository.findActiveByParcelIdForUpdate(parcelId)
                .orElseThrow(() -> new ParcelStatusConflictException(
                        "Parcel has no current active courier assignment"));
        if (!trustedCourierId.equals(assignment.getCourierId())) {
            throw new CourierAssignmentMismatchException();
        }
        if (!Objects.equals(parcel.getCourierId(), assignment.getCourierId())) {
            throw new ParcelStatusConflictException("Parcel and active assignment courier do not match");
        }
        transitionValidator.validateDeliveryCompletion(parcel.getStatus());

        var challenge = otpChallengeRepository.findByIdAndParcelIdForUpdate(challengeId, parcelId)
                .orElseThrow(() -> new ParcelStatusConflictException("Verified OTP proof was not found"));
        if (!challenge.getAssignmentId().equals(assignment.getId())
                || !challenge.getCourierId().equals(assignment.getCourierId())) {
            throw new ParcelStatusConflictException("OTP proof does not match the active assignment");
        }
        if (challenge.getVerifiedAt() == null || challenge.getOtpConsumedAt() == null
                || challenge.getProofConsumedAt() != null || challenge.getInvalidatedAt() != null) {
            throw new ParcelStatusConflictException("OTP proof is unverified, consumed, or invalidated");
        }

        var photoProof = photoProofRepository.findByIdAndAssignmentIdForUpdate(photoProofId, assignment.getId())
                .orElseThrow(() -> new ParcelStatusConflictException("Photo proof was not found for the active assignment"));
        if (!parcelId.equals(photoProof.getParcelId())
                || !assignment.getCourierId().equals(photoProof.getCourierId())) {
            throw new ParcelStatusConflictException("Photo proof does not match the active assignment");
        }
        if (photoProof.getVerifiedAt() == null || photoProof.getConsumedAt() != null
                || photoProof.getInvalidatedAt() != null) {
            throw new ParcelStatusConflictException("Photo proof is unverified, consumed, or invalidated");
        }

        Instant completedAt = clock.instant();
        // This policy currently fails closed in production. It must enforce the approved
        // proof-expiry policy and delivered_at timestamp convention before any mutation.
        var deliveredAt = completionPolicy.validateProofsAndMapDeliveredAt(
                challenge.getExpiresAt(), challenge.getVerifiedAt(), photoProof.getUploadedAt(),
                photoProof.getVerifiedAt(), completedAt);
        if (deliveredAt == null) {
            throw new IllegalStateException("Delivery completion policy returned no delivered_at value");
        }

        challenge.consumeProofAt(completedAt);
        photoProof.consumeAt(completedAt);
        parcel.completeDelivery(deliveredAt);
        parcelRepository.saveAndFlush(parcel);
        historyRepository.saveAndFlush(new ParcelStatusHistory(parcelId, ParcelStatusTransitionValidator.IN_TRANSIT,
                ParcelStatusTransitionValidator.DELIVERED, completedAt, trustedCourierId, null));

        return new DeliveryCompletionResult(parcelId, ParcelStatusTransitionValidator.DELIVERED, completedAt);
    }
}
