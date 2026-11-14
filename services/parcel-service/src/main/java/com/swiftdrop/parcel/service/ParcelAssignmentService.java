package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.exception.AssignmentConflictException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
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

    public ParcelAssignmentService(ParcelAssignmentRepository assignmentRepository, ParcelRepository parcelRepository) {
        this.assignmentRepository = assignmentRepository;
        this.parcelRepository = parcelRepository;
    }

    /**
     * Records a new assignment operation. If a legacy parcel has courier_id set but no
     * assignment history, no earlier history is fabricated; this call starts history now.
     */
    @Transactional
    public ParcelAssignment assign(String parcelId, String courierId, String changedBy, String reason) {
        Parcel parcel = parcelRepository.findByIdForUpdate(parcelId)
                .orElseThrow(() -> new ParcelNotFoundException(parcelId));
        Instant now = Instant.now();
        var activeAssignment = assignmentRepository.findByParcelIdAndUnassignedAtIsNull(parcelId);

        if (activeAssignment.isPresent()) {
            ParcelAssignment current = activeAssignment.get();
            if (Objects.equals(current.getCourierId(), courierId)) {
                if (!Objects.equals(parcel.getCourierId(), courierId)) {
                    parcel.assignTo(courierId);
                    parcelRepository.saveAndFlush(parcel);
                }
                return current;
            }
            current.closeAt(now);
            // Flush the close before inserting a replacement; the partial unique index
            // must no longer see the previous row as active when the identity insert runs.
            assignmentRepository.saveAndFlush(current);
        }

        parcel.assignTo(courierId);
        parcelRepository.saveAndFlush(parcel);

        try {
            return assignmentRepository.saveAndFlush(
                    new ParcelAssignment(parcelId, courierId, now, changedBy, reason));
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
