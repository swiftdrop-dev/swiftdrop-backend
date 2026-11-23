package com.swiftdrop.parcel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.event.ParcelBookedApplicationEvent;
import com.swiftdrop.parcel.exception.CourierAssignmentMismatchException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryOtpChallengeRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryPhotoProofRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal-only delivery completion core. trustedCourierId must come from a future
 * approved authenticated principal/service identity; it is never a request credential.
 * The production policy bean currently fails closed, so this operation is not enabled.
 */
@Service
public class DeliveryCompletionService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryCompletionService.class);

    private final ParcelRepository parcelRepository;
    private final ParcelAssignmentRepository assignmentRepository;
    private final ParcelDeliveryOtpChallengeRepository otpChallengeRepository;
    private final ParcelDeliveryPhotoProofRepository photoProofRepository;
    private final ParcelStatusHistoryRepository historyRepository;
    private final ParcelStatusTransitionValidator transitionValidator;
    private final DeliveryCompletionPolicy completionPolicy;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public DeliveryCompletionService(ParcelRepository parcelRepository,
                                     ParcelAssignmentRepository assignmentRepository,
                                     ParcelDeliveryOtpChallengeRepository otpChallengeRepository,
                                     ParcelDeliveryPhotoProofRepository photoProofRepository,
                                     ParcelStatusHistoryRepository historyRepository,
                                     ParcelStatusTransitionValidator transitionValidator,
                                     DeliveryCompletionPolicy completionPolicy,
                                     Clock clock) {
        this(parcelRepository, assignmentRepository, otpChallengeRepository, photoProofRepository,
                historyRepository, transitionValidator, completionPolicy, null, null, null, clock);
    }

    @Autowired
    public DeliveryCompletionService(ParcelRepository parcelRepository,
                                     ParcelAssignmentRepository assignmentRepository,
                                     ParcelDeliveryOtpChallengeRepository otpChallengeRepository,
                                     ParcelDeliveryPhotoProofRepository photoProofRepository,
                                     ParcelStatusHistoryRepository historyRepository,
                                     ParcelStatusTransitionValidator transitionValidator,
                                     DeliveryCompletionPolicy completionPolicy,
                                     OutboxEventService outboxEventService,
                                     ObjectMapper objectMapper,
                                     ApplicationEventPublisher applicationEventPublisher,
                                     Clock clock) {
        this.parcelRepository = parcelRepository;
        this.assignmentRepository = assignmentRepository;
        this.otpChallengeRepository = otpChallengeRepository;
        this.photoProofRepository = photoProofRepository;
        this.historyRepository = historyRepository;
        this.transitionValidator = transitionValidator;
        this.completionPolicy = completionPolicy;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
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
        String previousStatus = parcel.getStatus();
        parcel.completeDelivery(deliveredAt);
        parcelRepository.saveAndFlush(parcel);
        historyRepository.saveAndFlush(new ParcelStatusHistory(parcelId, previousStatus,
                ParcelStatusTransitionValidator.DELIVERED, completedAt, trustedCourierId, null));

        if (outboxEventService != null && objectMapper != null) {
            String eventType = "PARCEL_DELIVERED";
            Map<String, Object> event = new HashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("eventType", eventType);
            event.put("eventVersion", "1.0");
            event.put("occurredAt", completedAt.toString());
            event.put("parcelId", parcel.getId());
            event.put("trackingNumber", parcel.getTrackingNumber());
            event.put("status", "delivered");
            event.put("courierId", trustedCourierId);
            event.put("senderId", parcel.getSenderId());
            event.put("customerId", parcel.getSenderId());
            event.put("deliveredAt", deliveredAt.toString());
            try {
                String payload = objectMapper.writeValueAsString(event);
                outboxEventService.saveEvent("PARCEL", parcel.getId(), eventType, payload);
                if (applicationEventPublisher != null) {
                    applicationEventPublisher.publishEvent(new ParcelBookedApplicationEvent(parcel.getId()));
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize outbox event for delivery completion", e);
            }
        }

        return new DeliveryCompletionResult(parcelId, ParcelStatusTransitionValidator.DELIVERED, completedAt);
    }
}
