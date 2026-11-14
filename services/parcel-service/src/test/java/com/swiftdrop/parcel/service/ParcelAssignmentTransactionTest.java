package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTestRowsAndConstraint() {
        dropFailureConstraint();
        assignmentRepository.deleteAll();
        parcelRepository.deleteAll();
    }

    @AfterEach
    void removeFailureConstraint() {
        dropFailureConstraint();
    }

    @Test
    void failedNewHistoryInsertRollsBackParcelUpdateAndPreviousClosure() {
        parcelRepository.saveAndFlush(new Parcel("prc_rollback", "cou_old"));
        ParcelAssignment oldAssignment = assignmentRepository.saveAndFlush(new ParcelAssignment(
                "prc_rollback", "cou_old", Instant.now().minusSeconds(120), "dispatcher-old", "Original"));
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
        assertEquals(1, assignmentRepository.count());
    }

    @Test
    void concurrentAssignmentsForOneParcelAreSerializedByParcelLock() throws Exception {
        parcelRepository.saveAndFlush(new Parcel("prc_concurrent", null));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);

        try {
            Future<ParcelAssignment> first = workers.submit(() -> {
                ready.countDown();
                start.await();
                return assignmentService.assign("prc_concurrent", "cou_first", "dispatcher-1", "First");
            });
            Future<ParcelAssignment> second = workers.submit(() -> {
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

    private void dropFailureConstraint() {
        jdbcTemplate.execute("ALTER TABLE public.parcel_assignments DROP CONSTRAINT IF EXISTS " + TEST_CONSTRAINT);
    }
}
