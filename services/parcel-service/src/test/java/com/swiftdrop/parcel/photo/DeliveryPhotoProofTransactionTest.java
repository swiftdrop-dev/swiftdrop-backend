package com.swiftdrop.parcel.photo;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.entity.ParcelDeliveryPhotoProof;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryPhotoProofRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.storage.DeliveryPhotoStorage;
import com.swiftdrop.parcel.storage.StoredDeliveryPhoto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class DeliveryPhotoProofTransactionTest {
    private static final String FAILURE_CONSTRAINT = "ck_test_photo_persistence_failure";

    @Autowired private DeliveryPhotoProofService photoProofService;
    @Autowired private ParcelRepository parcelRepository;
    @Autowired private ParcelAssignmentRepository assignmentRepository;
    @Autowired private ParcelDeliveryPhotoProofRepository proofRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockBean
    private DeliveryPhotoStorage storage;

    @BeforeEach
    void prepareIsolatedRows() {
        dropFailureConstraint();
        proofRepository.deleteAll();
        assignmentRepository.deleteAll();
        parcelRepository.deleteAll();
        clearInvocations(storage);
    }

    @AfterEach
    void removeFailureConstraint() {
        dropFailureConstraint();
    }

    @Test
    void persistenceFailureRollsBackDatabaseAndLeavesPrivateAssetForReconciliation() throws Exception {
        Long assignmentId = createParcelAndAssignment("prc_tx_new");
        byte[] png = png();
        when(storage.upload(any(byte[].class))).thenReturn(new StoredDeliveryPhoto(
                "asset-new-unreferenced", "proof/force-failure", "png", png.length, "image"));
        jdbcTemplate.execute("ALTER TABLE public.parcel_delivery_photo_proofs ADD CONSTRAINT "
                + FAILURE_CONSTRAINT + " CHECK (cloudinary_public_id <> 'proof/force-failure')");

        assertThrows(DataIntegrityViolationException.class, () -> photoProofService.upload(
                "prc_tx_new", assignmentId, "cou_tx", "image/png", png));

        assertEquals(0, proofRepository.count());
        verify(storage, never()).deleteByAssetId("asset-new-unreferenced");
    }

    @Test
    void duplicateAssetIdConflictNeverDeletesAssetReferencedByExistingProof() throws Exception {
        createParcelAndAssignment("prc_tx_existing");
        Long incomingAssignmentId = createParcelAndAssignment("prc_tx_incoming");
        byte[] png = png();
        ParcelAssignment existingAssignment = assignmentRepository
                .findByParcelIdAndUnassignedAtIsNull("prc_tx_existing").orElseThrow();
        ParcelDeliveryPhotoProof existing = proofRepository.saveAndFlush(new ParcelDeliveryPhotoProof(
                UUID.randomUUID(), "prc_tx_existing", existingAssignment.getId(), "cou_tx",
                "asset-already-referenced", "proof/existing", "png", "image/png", png.length,
                null, Instant.now()));
        when(storage.upload(any(byte[].class))).thenReturn(new StoredDeliveryPhoto(
                "asset-already-referenced", "proof/incoming", "png", png.length, "image"));

        assertThrows(DeliveryPhotoProofConflictException.class, () -> photoProofService.upload(
                "prc_tx_incoming", incomingAssignmentId, "cou_tx", "image/png", png));

        assertEquals(existing.getId(), proofRepository.findById(existing.getId()).orElseThrow().getId());
        assertEquals(1, proofRepository.count());
        verify(storage, never()).deleteByAssetId("asset-already-referenced");
    }

    @Test
    void lifecycleConstraintsRejectConsumedAndInvalidatedProofAndPreUploadTimestamps() throws Exception {
        Long assignmentId = createParcelAndAssignment("prc_tx_lifecycle");
        Instant uploadedAt = Instant.now();
        ParcelDeliveryPhotoProof proof = proofRepository.saveAndFlush(new ParcelDeliveryPhotoProof(
                UUID.randomUUID(), "prc_tx_lifecycle", assignmentId, "cou_tx", "asset-lifecycle",
                "proof/lifecycle", "png", "image/png", png().length, null, uploadedAt));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE public.parcel_delivery_photo_proofs SET consumed_at = ?, invalidated_at = ? WHERE id = ?",
                Timestamp.from(uploadedAt.plusSeconds(2)), Timestamp.from(uploadedAt.plusSeconds(3)), proof.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE public.parcel_delivery_photo_proofs SET verified_at = ? WHERE id = ?",
                Timestamp.from(uploadedAt.minusSeconds(1)), proof.getId()));
    }

    private Long createParcelAndAssignment(String parcelId) {
        parcelRepository.saveAndFlush(new Parcel(parcelId, null,
                "SWF-TX-" + parcelId, "pending", null, "Recipient", "0700000000", "Address"));
        return assignmentRepository.saveAndFlush(new ParcelAssignment(parcelId, "cou_tx",
                Instant.now(), null, null)).getId();
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void dropFailureConstraint() {
        jdbcTemplate.execute("ALTER TABLE public.parcel_delivery_photo_proofs DROP CONSTRAINT IF EXISTS "
                + FAILURE_CONSTRAINT);
    }
}
