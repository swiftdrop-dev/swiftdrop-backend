package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.entity.ParcelDeliveryPhotoProof;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryPhotoProofRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ParcelAssignmentTransactionTest {
    private static final String TEST_CONSTRAINT = "ck_test_reject_assignment_reason";

    @Autowired
    private ParcelAssignmentService assignmentService;

    @Autowired
    private ParcelAssignmentRepository assignmentRepository;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private ParcelDeliveryPhotoProofRepository photoProofRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTestRowsAndConstraint() {
        dropFailureConstraint();
        photoProofRepository.deleteAll();
        assignmentRepository.deleteAll();
        parcelRepository.deleteAll();
    }

    @AfterEach
    void removeFailureConstraint() {
        dropFailureConstraint();
    }

    @Test
    void failedNewHistoryInsertRollsBackParcelUpdateAndPreviousClosure() {
        parcelRepository.saveAndFlush(testParcel("prc_rollback", "cou_old"));
        ParcelAssignment oldAssignment = assignmentRepository.saveAndFlush(new ParcelAssignment(
                "prc_rollback", "cou_old", Instant.now().minusSeconds(120), "dispatcher-old", "Original"));
        ParcelDeliveryPhotoProof oldProof = photoProofRepository.saveAndFlush(new ParcelDeliveryPhotoProof(
                UUID.randomUUID(), "prc_rollback", oldAssignment.getId(), "cou_old", "asset-rollback",
                "proof/rollback", "png", "image/png", 42, null, Instant.now().minusSeconds(60)));
        jdbcTemplate.execute("ALTER TABLE public.parcel_assignments ADD CONSTRAINT " + TEST_CONSTRAINT
                + " CHECK (reason <> 'force-failure')");

        assertThrows(DataIntegrityViolationException.class, () -> assignmentService.assign(
                "prc_rollback", "cou_new", "dispatcher-new", "force-failure"));

        Parcel parcelAfterFailure = parcelRepository.findById("prc_rollback").orElseThrow();
        ParcelAssignment activeAfterFailure = assignmentRepository
                .findByParcelIdAndUnassignedAtIsNull("prc_rollback").orElseThrow();

        assertEquals("cou_old", parcelAfterFailure.getCourierId());
        assertEquals(oldAssignment.getId(), activeAfterFailure.getId());
        assertNull(activeAfterFailure.getUnassignedAt());
        assertNull(photoProofRepository.findById(oldProof.getId()).orElseThrow().getInvalidatedAt());
        assertEquals(1, assignmentRepository.count());
    }

    @Test
    void concurrentAssignmentsForOneParcelAreSerializedByParcelLock() throws Exception {
        parcelRepository.saveAndFlush(testParcel("prc_concurrent", null));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = workers.submit(() -> {
                ready.countDown();
                start.await();
                return assignmentService.assign("prc_concurrent", "cou_first", "dispatcher-1", "First");
            });
            Future<?> second = workers.submit(() -> {
                ready.countDown();
                start.await();
                return assignmentService.assign("prc_concurrent", "cou_second", "dispatcher-2", "Second");
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both assignment calls should be ready");
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            List<ParcelAssignment> assignments = assignmentRepository.findAll().stream()
                    .filter(assignment -> assignment.getParcelId().equals("prc_concurrent"))
                    .toList();
            List<ParcelAssignment> activeAssignments = assignments.stream()
                    .filter(assignment -> assignment.getUnassignedAt() == null)
                    .toList();
            Parcel parcel = parcelRepository.findById("prc_concurrent").orElseThrow();

            assertEquals(2, assignments.size());
            assertEquals(1, activeAssignments.size());
            assertEquals(parcel.getCourierId(), activeAssignments.get(0).getCourierId());
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void assignedAndHistoryQueriesUseIdAsTimestampTieBreaker() {
        Instant tiedAt = Instant.parse("2026-11-14T06:25:00Z");
        parcelRepository.saveAndFlush(testParcel("prc_tie_a", null));
        parcelRepository.saveAndFlush(testParcel("prc_tie_b", null));
        parcelRepository.saveAndFlush(testParcel("prc_tie_history", null));

        ParcelAssignment activeA = assignmentRepository.saveAndFlush(
                new ParcelAssignment("prc_tie_a", "cou_001", tiedAt, null, null));
        ParcelAssignment activeB = assignmentRepository.saveAndFlush(
                new ParcelAssignment("prc_tie_b", "cou_001", tiedAt, null, null));
        List<Long> assignedIds = assignmentRepository
                .findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanAndUnassignedAtIsNullOrderByAssignedAtAscIdAsc(
                        "cou_001", tiedAt.minusSeconds(1), tiedAt.plusSeconds(1), PageRequest.of(0, 10))
                .getContent().stream().map(ParcelAssignment::getId).toList();

        ParcelAssignment earlierId = new ParcelAssignment("prc_tie_history", "cou_old", tiedAt, null, null);
        earlierId.closeAt(tiedAt.plusSeconds(1));
        ParcelAssignment laterId = new ParcelAssignment("prc_tie_history", "cou_new", tiedAt, null, null);
        laterId.closeAt(tiedAt.plusSeconds(2));
        earlierId = assignmentRepository.saveAndFlush(earlierId);
        laterId = assignmentRepository.saveAndFlush(laterId);
        List<Long> historyIds = assignmentRepository.findByParcelIdOrderByAssignedAtAscIdAsc("prc_tie_history")
                .stream().map(ParcelAssignment::getId).toList();

        assertEquals(List.of(activeA.getId(), activeB.getId()), assignedIds);
        assertEquals(List.of(earlierId.getId(), laterId.getId()), historyIds);
    }

    @Test
    void realReassignmentInvalidatesUnconsumedPhotoProofInSameTransaction() {
        parcelRepository.saveAndFlush(testParcel("prc_photo_reassign", "cou_old"));
        ParcelAssignment oldAssignment = assignmentRepository.saveAndFlush(new ParcelAssignment(
                "prc_photo_reassign", "cou_old", Instant.now().minusSeconds(60), null, null));
        ParcelDeliveryPhotoProof proof = new ParcelDeliveryPhotoProof(UUID.randomUUID(), "prc_photo_reassign",
                oldAssignment.getId(), "cou_old", "asset-reassign", "proof/opaque", "png", "image/png", 42,
                null, Instant.now().minusSeconds(30));
        photoProofRepository.saveAndFlush(proof);

        assignmentService.assign("prc_photo_reassign", "cou_new", null, "reassignment");

        assertTrue(photoProofRepository.findById(proof.getId()).orElseThrow().getInvalidatedAt() != null);
        assertEquals("cou_new", parcelRepository.findById("prc_photo_reassign").orElseThrow().getCourierId());
    }

    @Test
    void idempotentSameCourierAssignmentPreservesPhotoProof() {
        parcelRepository.saveAndFlush(testParcel("prc_photo_same", "cou_old"));
        ParcelAssignment active = assignmentRepository.saveAndFlush(new ParcelAssignment(
                "prc_photo_same", "cou_old", Instant.now().minusSeconds(60), null, null));
        ParcelDeliveryPhotoProof proof = new ParcelDeliveryPhotoProof(UUID.randomUUID(), "prc_photo_same",
                active.getId(), "cou_old", "asset-same", "proof/opaque", "jpeg", "image/jpeg", 42,
                null, Instant.now().minusSeconds(30));
        photoProofRepository.saveAndFlush(proof);

        assignmentService.assign("prc_photo_same", "cou_old", null, null);

        assertNull(photoProofRepository.findById(proof.getId()).orElseThrow().getInvalidatedAt());
        assertNull(active.getUnassignedAt());
    }

    private void dropFailureConstraint() {
        jdbcTemplate.execute("ALTER TABLE public.parcel_assignments DROP CONSTRAINT IF EXISTS " + TEST_CONSTRAINT);
    }

    private Parcel testParcel(String id, String courierId) {
        return new Parcel(id, courierId, "SWF-TEST-" + id, "pending", null,
                "Test Recipient", "0700000000", "Test delivery address");
    }
}
