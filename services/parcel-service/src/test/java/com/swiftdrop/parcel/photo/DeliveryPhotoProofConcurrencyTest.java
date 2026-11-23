package com.swiftdrop.parcel.photo;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryPhotoProofRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.storage.DeliveryPhotoStorage;
import com.swiftdrop.parcel.storage.StoredDeliveryPhoto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class DeliveryPhotoProofConcurrencyTest {
    private Long assignmentId;
    @Autowired private DeliveryPhotoProofService photoProofService;
    @Autowired private ParcelRepository parcelRepository;
    @Autowired private ParcelAssignmentRepository assignmentRepository;
    @Autowired private ParcelDeliveryPhotoProofRepository proofRepository;

    @MockBean
    private DeliveryPhotoStorage storage;

    @SpyBean
    private ParcelRepository parcelRepositorySpy;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void clearRows() {
        proofRepository.deleteAll();
        assignmentRepository.deleteAll();
        parcelRepository.deleteAll();
        parcelRepository.saveAndFlush(new Parcel("prc_photo_concurrent", null,
                "SWF-PHOTO-CONCURRENT", "pending", null, "Recipient", "0700000000", "Address"));
        assignmentId = assignmentRepository.saveAndFlush(new ParcelAssignment("prc_photo_concurrent", "cou_photo",
                Instant.now(), null, null)).getId();
        clearInvocations(storage);
    }

    @Test
    void concurrentUploadsSerializeAndPersistOnlyOneActiveProof() throws Exception {
        byte[] image = png();
        CountDownLatch storageEntered = new CountDownLatch(1);
        CountDownLatch allowStorageReturn = new CountDownLatch(1);
        CountDownLatch secondParcelLockAttempted = new CountDownLatch(1);
        AtomicInteger parcelLockCalls = new AtomicInteger();
        doAnswer(invocation -> {
            if (parcelLockCalls.incrementAndGet() == 2) {
                secondParcelLockAttempted.countDown();
            }
            return entityManager.createQuery("select parcel from Parcel parcel where parcel.id = :parcelId",
                            Parcel.class)
                    .setParameter("parcelId", invocation.getArgument(0))
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getResultStream()
                    .findFirst();
        }).when(parcelRepositorySpy).findByIdForUpdate("prc_photo_concurrent");
        when(storage.upload(any(byte[].class))).thenAnswer(invocation -> {
            storageEntered.countDown();
            if (!allowStorageReturn.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test upload timeout");
            }
            return new StoredDeliveryPhoto("asset-concurrent", "proof/opaque", "png", image.length, "image");
        });

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<DeliveryPhotoProofReceipt> first = workers.submit(() ->
                    photoProofService.upload("prc_photo_concurrent", assignmentId, "cou_photo", "image/png", image));
            if (!storageEntered.await(5, TimeUnit.SECONDS)) {
                first.get(1, TimeUnit.SECONDS);
                throw new AssertionError("Request A did not reach the storage boundary");
            }
            Future<?> second = workers.submit(() -> {
                return photoProofService.upload("prc_photo_concurrent", assignmentId, "cou_photo", "image/png", image);
            });
            assertTrue(secondParcelLockAttempted.await(5, TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> second.get(250, TimeUnit.MILLISECONDS),
                    "Request B should remain blocked while request A owns the parcel lock");
            allowStorageReturn.countDown();
            first.get(10, TimeUnit.SECONDS);
            ExecutionException conflict = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                    () -> second.get(10, TimeUnit.SECONDS));
            assertInstanceOf(DeliveryPhotoProofConflictException.class, conflict.getCause());
            assertEquals(1, proofRepository.count());
            verify(storage, org.mockito.Mockito.times(1)).upload(any(byte[].class));
            verify(storage, org.mockito.Mockito.never()).deleteByAssetId(any());
        } finally {
            allowStorageReturn.countDown();
            workers.shutdownNow();
        }
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
