package com.swiftdrop.parcel.photo;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.entity.ParcelDeliveryPhotoProof;
import com.swiftdrop.parcel.exception.CourierAssignmentMismatchException;
import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryPhotoProofRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.storage.DeliveryPhotoStorage;
import com.swiftdrop.parcel.storage.StoredDeliveryPhoto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryPhotoProofServiceTest {
    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final ParcelAssignmentRepository assignments = mock(ParcelAssignmentRepository.class);
    private final ParcelDeliveryPhotoProofRepository proofs = mock(ParcelDeliveryPhotoProofRepository.class);
    private final DeliveryPhotoStorage storage = mock(DeliveryPhotoStorage.class);
    private final DeliveryPhotoProofService service = new DeliveryPhotoProofService(parcels, assignments, proofs,
            new DeliveryPhotoValidator(), storage, Clock.fixed(Instant.parse("2026-11-14T08:00:00Z"), ZoneOffset.UTC));
    private final byte[] png = image();

    @BeforeEach
    void prepareMocks() {
        when(parcels.findByIdForUpdate("prc_001")).thenReturn(Optional.of(new Parcel("prc_001", "cou_001")));
        ParcelAssignment assignment = new ParcelAssignment("prc_001", "cou_001", Instant.now(), null, null);
        ReflectionTestUtils.setField(assignment, "id", 77L);
        when(assignments.findActiveByIdAndParcelIdForUpdate(77L, "prc_001"))
                .thenReturn(Optional.of(assignment));
        when(proofs.findActiveByAssignmentIdForUpdate(77L)).thenReturn(Optional.empty());
        when(storage.upload(png)).thenReturn(new StoredDeliveryPhoto(
                "asset-immutable-77", "swiftdrop/parcel-delivery-proof/opaque", "png", png.length, "image"));
        when(proofs.saveAndFlush(any(ParcelDeliveryPhotoProof.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void uploadsOnlyForMatchingActiveAssignmentAndReturnsSafeReceipt() {
        DeliveryPhotoProofReceipt receipt = service.upload("prc_001", 77L, "cou_001", "image/png", png);

        assertEquals("png", receipt.format());
        assertEquals((long) png.length, receipt.byteSize());
        verify(proofs).saveAndFlush(any(ParcelDeliveryPhotoProof.class));
        verify(storage).upload(png);
        assertEquals("DeliveryPhotoProofReceipt[proofId=" + receipt.proofId()
                + ", format=png, byteSize=" + png.length + ", uploadedAt=2026-11-14T08:00:00Z]", receipt.toString());
        verify(storage, never()).deleteByAssetId(any());
    }

    @Test
    void rejectsWrongCourierAndInactiveAssignmentBeforeStorage() {
        assertThrows(CourierAssignmentMismatchException.class,
                () -> service.upload("prc_001", 77L, "cou_other", "image/png", png));
        when(assignments.findActiveByIdAndParcelIdForUpdate(78L, "prc_001"))
                .thenReturn(Optional.empty());
        assertThrows(ParcelStatusConflictException.class,
                () -> service.upload("prc_001", 78L, "cou_001", "image/png", png));
        verify(storage, never()).upload(any());
    }

    @Test
    void rejectsDuplicateActiveProofBeforeStorage() {
        when(proofs.findActiveByAssignmentIdForUpdate(77L))
                .thenReturn(Optional.of(mock(ParcelDeliveryPhotoProof.class)));

        assertThrows(DeliveryPhotoProofConflictException.class,
                () -> service.upload("prc_001", 77L, "cou_001", "image/png", png));
        verify(storage, never()).upload(any());
    }

    @Test
    void invalidImageAndProviderFailureNeverPersistProof() {
        assertThrows(InvalidDeliveryPhotoException.class,
                () -> service.upload("prc_001", 77L, "cou_001", "image/jpeg", png));
        verify(storage, never()).upload(any());

        org.mockito.Mockito.when(storage.upload(png))
                .thenThrow(new com.swiftdrop.parcel.storage.CloudinaryStorageException(
                        "Cloudinary image upload failed"));
        assertThrows(com.swiftdrop.parcel.storage.CloudinaryStorageException.class,
                () -> service.upload("prc_001", 77L, "cou_001", "image/png", png));
        verify(proofs, never()).saveAndFlush(any(ParcelDeliveryPhotoProof.class));
    }

    @Test
    void databasePersistenceFailureDoesNotDeleteCloudinaryAsset() {
        when(proofs.saveAndFlush(any(ParcelDeliveryPhotoProof.class)))
                .thenThrow(new DataIntegrityViolationException("test database failure"));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.upload("prc_001", 77L, "cou_001", "image/png", png));
        verify(storage, never()).deleteByAssetId(any());
    }

    private static byte[] image() {
        try {
            BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
