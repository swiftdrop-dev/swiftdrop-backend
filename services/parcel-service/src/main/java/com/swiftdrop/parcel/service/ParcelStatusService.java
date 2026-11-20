package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.exception.CourierAssignmentMismatchException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class ParcelStatusService {
    public static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final ParcelRepository parcelRepository;
    private final ParcelAssignmentRepository assignmentRepository;
    private final ParcelStatusHistoryRepository historyRepository;
    private final ParcelStatusTransitionValidator transitionValidator;
    private final Clock clock;

    public ParcelStatusService(ParcelRepository parcelRepository,
                               ParcelAssignmentRepository assignmentRepository,
                               ParcelStatusHistoryRepository historyRepository,
                               ParcelStatusTransitionValidator transitionValidator,
                               Clock clock) {
        this.parcelRepository = parcelRepository;
        this.assignmentRepository = assignmentRepository;
        this.historyRepository = historyRepository;
        this.transitionValidator = transitionValidator;
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
        return historyRepository.saveAndFlush(history);
    }

    private String validateFailureReason(String previousStatus, String requestedStatus, String reason) {
        boolean transitioningToFailed = ParcelStatusTransitionValidator.IN_TRANSIT.equals(previousStatus)
                && ParcelStatusTransitionValidator.FAILED.equals(requestedStatus);
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
