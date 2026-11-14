package com.swiftdrop.parcel.photo;

import com.swiftdrop.parcel.entity.ParcelDeliveryPhotoProof;
import com.swiftdrop.parcel.exception.CourierAssignmentMismatchException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryPhotoProofRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.storage.DeliveryPhotoStorage;
import com.swiftdrop.parcel.storage.StoredDeliveryPhoto;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.sql.SQLException;
import java.util.UUID;

/** Internal only: callers must provide courier identity obtained from a future trusted auth integration. */
@Service
public class DeliveryPhotoProofService {
    private final ParcelRepository parcelRepository;
    private final ParcelAssignmentRepository assignmentRepository;
    private final ParcelDeliveryPhotoProofRepository proofRepository;
    private final DeliveryPhotoValidator validator;
    private final DeliveryPhotoStorage storage;
    private final Clock clock;

    public DeliveryPhotoProofService(ParcelRepository parcelRepository,
                                     ParcelAssignmentRepository assignmentRepository,
                                     ParcelDeliveryPhotoProofRepository proofRepository,
                                     DeliveryPhotoValidator validator,
                                     DeliveryPhotoStorage storage,
                                     Clock clock) {
        this.parcelRepository = parcelRepository;
        this.assignmentRepository = assignmentRepository;
        this.proofRepository = proofRepository;
        this.validator = validator;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional
    public DeliveryPhotoProofReceipt upload(String parcelId, Long assignmentId, String courierId,
                                            String declaredContentType, byte[] imageBytes) {
        if (parcelId == null || assignmentId == null || courierId == null || courierId.isBlank()) {
            throw new IllegalArgumentException("Parcel, assignment, and courier identity are required");
        }

        // Keep the established lock order: parcel, then assignment, then proof.
        parcelRepository.findByIdForUpdate(parcelId)
                .orElseThrow(() -> new ParcelNotFoundException(parcelId));
        var assignment = assignmentRepository.findActiveByIdAndParcelIdForUpdate(assignmentId, parcelId)
                .orElseThrow(() -> new ParcelStatusConflictException("Assignment is not active for this parcel"));
        if (!courierId.equals(assignment.getCourierId())) {
            throw new CourierAssignmentMismatchException();
        }
        if (proofRepository.findActiveByAssignmentIdForUpdate(assignmentId).isPresent()) {
            throw new DeliveryPhotoProofConflictException("An active delivery photo proof already exists");
        }

        ValidatedDeliveryPhoto verified = validator.validate(imageBytes, declaredContentType);
        StoredDeliveryPhoto stored = storage.upload(imageBytes);
        verifyProviderMetadata(stored, verified);

        // Do not delete uploaded assets during rollback compensation. A private asset
        // may be left unreferenced if persistence fails; reconciliation is deferred.
        Instant now = clock.instant();
        ParcelDeliveryPhotoProof proof = new ParcelDeliveryPhotoProof(UUID.randomUUID(), parcelId,
                assignment.getId(), assignment.getCourierId(), stored.assetId(), stored.publicId(),
                verified.format(), verified.contentType(), verified.byteSize(), verified.sha256Digest(), now);
        try {
            proofRepository.saveAndFlush(proof);
        } catch (DataIntegrityViolationException exception) {
            if (isAssetIdUniquenessViolation(exception)) {
                // Preserve the existing row and its Cloudinary asset. Physical cleanup is
                // deliberately deferred to a future reconciliation workflow.
                throw new DeliveryPhotoProofConflictException(
                        "Cloudinary asset is already referenced by a delivery photo proof");
            }
            throw exception;
        }
        return new DeliveryPhotoProofReceipt(proof.getId(), proof.getVerifiedFormat(),
                proof.getByteSize(), proof.getUploadedAt());
    }

    private void verifyProviderMetadata(StoredDeliveryPhoto stored, ValidatedDeliveryPhoto verified) {
        String expectedProviderFormat = "jpeg".equals(verified.format()) ? "jpg" : "png";
        if (stored == null || stored.assetId() == null || stored.assetId().isBlank()
                || stored.publicId() == null || stored.publicId().isBlank()
                || !"image".equals(stored.resourceType())
                || !expectedProviderFormat.equalsIgnoreCase(stored.format())
                || stored.byteSize() != verified.byteSize()) {
            throw new IllegalStateException("Image storage returned invalid metadata");
        }
    }

    private boolean isAssetIdUniquenessViolation(Throwable failure) {
        String expectedName = "uq_parcel_photo_proof_asset";
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(java.util.Locale.ROOT).contains(expectedName)) {
                return true;
            }
            if (cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().toLowerCase(java.util.Locale.ROOT).contains(expectedName)) {
                return true;
            }
        }
        return false;
    }
}
