package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelDeliveryOtpChallenge;
import com.swiftdrop.parcel.exception.AssignmentConflictException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryOtpChallengeRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryPhotoProofRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.SQLException;
import java.util.Objects;

@Service
public class ParcelAssignmentService {
    private final ParcelAssignmentRepository assignmentRepository;
    private final ParcelRepository parcelRepository;
    private final ParcelDeliveryOtpChallengeRepository otpChallengeRepository;
    private final ParcelDeliveryPhotoProofRepository photoProofRepository;

    public ParcelAssignmentService(ParcelAssignmentRepository assignmentRepository, ParcelRepository parcelRepository,
                                   ParcelDeliveryOtpChallengeRepository otpChallengeRepository,
                                   ParcelDeliveryPhotoProofRepository photoProofRepository) {
        this.assignmentRepository = assignmentRepository;
        this.parcelRepository = parcelRepository;
        this.otpChallengeRepository = otpChallengeRepository;
        this.photoProofRepository = photoProofRepository;
    }

    /**
     * Records a new assignment operation. If a legacy parcel has courier_id set but no
     * assignment history, no earlier history is fabricated; this call starts history now.
     */
    @Transactional
    public AssignmentOutcome assign(String parcelId, String courierId, String changedBy, String reason) {
        Parcel parcel = parcelRepository.findByIdForUpdate(parcelId)
                .orElseThrow(() -> new ParcelNotFoundException(parcelId));
        Instant now = Instant.now();
        var activeAssignment = assignmentRepository.findActiveByParcelIdForUpdate(parcelId);

        if (activeAssignment.isPresent()) {
            ParcelAssignment current = activeAssignment.get();
            if (Objects.equals(current.getCourierId(), courierId)) {
                if (!Objects.equals(parcel.getCourierId(), courierId)) {
                    parcel.assignTo(courierId);
                    parcelRepository.saveAndFlush(parcel);
                }
                return new AssignmentOutcome(current, false);
            }
        }

        var outstandingChallenges = otpChallengeRepository.findUsableByParcelIdForUpdate(parcelId);
        outstandingChallenges.forEach(challenge -> challenge.invalidateAt(now));
        if (!outstandingChallenges.isEmpty()) {
            otpChallengeRepository.saveAllAndFlush(outstandingChallenges);
        }

        if (activeAssignment.isPresent()) {
            ParcelAssignment current = activeAssignment.get();
            // Photo proof rows are invalidated under the parcel lock, after the assignment
            // row lock, in the same transaction as closing this assignment.
            photoProofRepository.invalidateUnconsumedByAssignmentId(current.getId(), now);
            current.closeAt(now);
            // Flush the close before inserting a replacement; the partial unique index
            // must no longer see the previous row as active when the identity insert runs.
            assignmentRepository.saveAndFlush(current);
        }

        parcel.assignTo(courierId);
        parcelRepository.saveAndFlush(parcel);

        try {
            ParcelAssignment created = assignmentRepository.saveAndFlush(
                    new ParcelAssignment(parcelId, courierId, now, changedBy, reason));
            return new AssignmentOutcome(created, true);
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueConstraintViolation(exception)) {
                throw new AssignmentConflictException(parcelId, exception);
            }
            throw exception;
        }
    }

    private boolean isUniqueConstraintViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
