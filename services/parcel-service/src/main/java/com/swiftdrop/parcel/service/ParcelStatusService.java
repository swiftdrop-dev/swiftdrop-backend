package com.swiftdrop.parcel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.event.ParcelBookedApplicationEvent;
import com.swiftdrop.parcel.exception.CourierAssignmentMismatchException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ParcelStatusService {
    private static final Logger log = LoggerFactory.getLogger(ParcelStatusService.class);
    public static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final ParcelRepository parcelRepository;
    private final ParcelAssignmentRepository assignmentRepository;
    private final ParcelStatusHistoryRepository historyRepository;
    private final ParcelStatusTransitionValidator transitionValidator;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public ParcelStatusService(ParcelRepository parcelRepository,
                               ParcelAssignmentRepository assignmentRepository,
                               ParcelStatusHistoryRepository historyRepository,
                               ParcelStatusTransitionValidator transitionValidator,
                               Clock clock) {
        this(parcelRepository, assignmentRepository, historyRepository, transitionValidator, null, null, null, clock);
    }

    @Autowired
    public ParcelStatusService(ParcelRepository parcelRepository,
                               ParcelAssignmentRepository assignmentRepository,
                               ParcelStatusHistoryRepository historyRepository,
                               ParcelStatusTransitionValidator transitionValidator,
                               OutboxEventService outboxEventService,
                               ObjectMapper objectMapper,
                               ApplicationEventPublisher applicationEventPublisher,
                               Clock clock) {
        this.parcelRepository = parcelRepository;
        this.assignmentRepository = assignmentRepository;
        this.historyRepository = historyRepository;
        this.transitionValidator = transitionValidator;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    /**
     * trustedCourierId must be obtained from an approved authentication integration.
     * No HTTP endpoint currently supplies this value because Parcel Service has no such integration.
     */
    @Transactional
    public ParcelStatusHistory updateStatus(String parcelId, String trustedCourierId,
                                            String requestedStatus, String failureReason) {
        if (trustedCourierId == null || trustedCourierId.isBlank() || trustedCourierId.length() > 128) {
            throw new IllegalArgumentException("A trusted courier identity is required");
        }

        var parcel = parcelRepository.findByIdForUpdate(parcelId)
                .orElseThrow(() -> new ParcelNotFoundException(parcelId));

        // Keep lock order aligned with ParcelAssignmentService: parcel row, then assignment row.
        var activeAssignment = assignmentRepository.findActiveByParcelIdForUpdate(parcelId)
                .orElseThrow(() -> new ParcelStatusConflictException(
                        "Parcel has no current active courier assignment"));
        if (!trustedCourierId.equals(activeAssignment.getCourierId())) {
            throw new CourierAssignmentMismatchException();
        }

        transitionValidator.validate(parcel.getStatus(), requestedStatus);
        String persistedFailureReason = validateFailureReason(
                parcel.getStatus(), requestedStatus, failureReason);

        ParcelStatusHistory history = new ParcelStatusHistory(parcelId, parcel.getStatus(), requestedStatus,
                clock.instant(), trustedCourierId, persistedFailureReason);
        parcel.changeStatus(requestedStatus);
        parcelRepository.saveAndFlush(parcel);
        ParcelStatusHistory savedHistory = historyRepository.saveAndFlush(history);

        if (outboxEventService != null && objectMapper != null) {
            String eventType = "PARCEL_" + requestedStatus.toUpperCase();
            Map<String, Object> event = new HashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("eventType", eventType);
            event.put("eventVersion", "1.0");
            event.put("occurredAt", clock.instant().toString());
            event.put("parcelId", parcel.getId());
            event.put("trackingNumber", parcel.getTrackingNumber());
            event.put("status", requestedStatus.toLowerCase());
            event.put("courierId", trustedCourierId);
            event.put("senderId", parcel.getSenderId());
            event.put("customerId", parcel.getSenderId());
            if (persistedFailureReason != null) {
                event.put("failureReason", persistedFailureReason);
                event.put("description", "Delivery attempt failed: " + persistedFailureReason);
            }
            try {
                String payload = objectMapper.writeValueAsString(event);
                outboxEventService.saveEvent("PARCEL", parcel.getId(), eventType, payload);
                if (applicationEventPublisher != null) {
                    applicationEventPublisher.publishEvent(new ParcelBookedApplicationEvent(parcel.getId()));
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize outbox event for parcel status update", e);
            }
        }

        return savedHistory;
    }

    private String validateFailureReason(String previousStatus, String requestedStatus, String reason) {
        boolean transitioningToFailed = ParcelStatusTransitionValidator.FAILED.equalsIgnoreCase(requestedStatus)
                || ParcelStatusTransitionValidator.DELIVERY_FAILED.equalsIgnoreCase(requestedStatus);
        if (!transitioningToFailed) {
            return null;
        }
        if (reason == null || reason.isBlank()) {
            throw new ParcelStatusConflictException("A failure reason is required when marking a parcel failed");
        }
        String normalized = reason.trim();
        if (normalized.length() > MAX_FAILURE_REASON_LENGTH) {
            throw new ParcelStatusConflictException(
                    "Failure reason must be at most " + MAX_FAILURE_REASON_LENGTH + " characters");
        }
        return normalized;
    }
}
