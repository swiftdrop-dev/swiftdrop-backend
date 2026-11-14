package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.exception.CourierAssignmentMismatchException;
import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ParcelStatusTransactionTest {
    private static final String FAILURE_CONSTRAINT = "ck_test_reject_status_history";

    @Autowired private ParcelStatusService statusService;
    @Autowired private ParcelAssignmentService assignmentService;
    @Autowired private ParcelRepository parcelRepository;
    @Autowired private ParcelAssignmentRepository assignmentRepository;
    @Autowired private ParcelStatusHistoryRepository historyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanRows() {
        dropFailureConstraint();
        historyRepository.deleteAll();
        assignmentRepository.deleteAll();
        parcelRepository.deleteAll();
    }

    @AfterEach
    void cleanupRows() {
        dropFailureConstraint();
        historyRepository.deleteAll();
        assignmentRepository.deleteAll();
        parcelRepository.deleteAll();
    }

    @Test
    void failedRetryTransitionsCreateSeparateHistoryRowsWithoutReplacingFailureReasons() {
        saveAssignedParcel("prc_retries", "cou_001", "pending");

        statusService.updateStatus("prc_retries", "cou_001", "in_transit", null);
        statusService.updateStatus("prc_retries", "cou_001", "failed", "First attempt failed");
        statusService.updateStatus("prc_retries", "cou_001", "in_transit", null);
        statusService.updateStatus("prc_retries", "cou_001", "failed", "Second attempt failed");

        List<ParcelStatusHistory> history = historyRepository
                .findByParcelIdOrderByChangedAtAscIdAsc("prc_retries");
        assertEquals(4, history.size());
        assertEquals(List.of("in_transit", "failed", "in_transit", "failed"),
                history.stream().map(ParcelStatusHistory::getNewStatus).toList());
        assertEquals("First attempt failed", history.get(1).getFailureReason());
        assertEquals("Second attempt failed", history.get(3).getFailureReason());
        assertEquals("cou_001", history.get(0).getActorId());
        assertNotNull(history.get(0).getChangedAt());
    }

    @Test
    void historyInsertFailureRollsBackParcelStatusUpdate() {
        saveAssignedParcel("prc_status_rollback", "cou_001", "in_transit");
        jdbcTemplate.execute("ALTER TABLE public.parcel_status_history ADD CONSTRAINT " + FAILURE_CONSTRAINT
                + " CHECK (new_status <> 'failed')");

        assertThrows(RuntimeException.class, () -> statusService.updateStatus(
                "prc_status_rollback", "cou_001", "failed", "Recipient unavailable"));

        assertEquals("in_transit", parcelRepository.findById("prc_status_rollback").orElseThrow().getStatus());
        assertEquals(0, historyRepository.count());
    }

    @Test
    void simultaneousSameTransitionSerializesAndOnlyOneHistoryRowIsWritten() throws Exception {
        saveAssignedParcel("prc_status_concurrent", "cou_001", "pending");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = workers.submit(() -> updateAfterBarrier(ready, start));
            Future<Boolean> second = workers.submit(() -> updateAfterBarrier(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0));
            assertEquals("in_transit", parcelRepository.findById("prc_status_concurrent").orElseThrow().getStatus());
            assertEquals(1, historyRepository.count());
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void statusUpdateAndReassignmentSerializeOnParcelRow() throws Exception {
        saveAssignedParcel("prc_status_reassign_race", "cou_old", "pending");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> status = workers.submit(() -> {
                ready.countDown();
                await(start);
                try {
                    statusService.updateStatus("prc_status_reassign_race", "cou_old", "in_transit", null);
                } catch (CourierAssignmentMismatchException ignored) {
                    // Reassignment acquired the parcel lock first, so the old courier must be rejected.
                }
                return null;
            });
            Future<?> reassignment = workers.submit(() -> {
                ready.countDown();
                await(start);
                assignmentService.assign("prc_status_reassign_race", "cou_new", null, "Reassigned");
                return null;
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            status.get(10, TimeUnit.SECONDS);
            reassignment.get(10, TimeUnit.SECONDS);

            Parcel parcel = parcelRepository.findById("prc_status_reassign_race").orElseThrow();
            ParcelAssignment active = assignmentRepository
                    .findByParcelIdAndUnassignedAtIsNull("prc_status_reassign_race").orElseThrow();
            assertEquals("cou_new", parcel.getCourierId());
            assertEquals(parcel.getCourierId(), active.getCourierId());
            assertTrue(List.of("pending", "in_transit").contains(parcel.getStatus()));
            assertEquals(parcel.getStatus().equals("pending") ? 0 : 1, historyRepository.count());
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    private boolean updateAfterBarrier(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            statusService.updateStatus("prc_status_concurrent", "cou_001", "in_transit", null);
            return true;
        } catch (ParcelStatusConflictException expected) {
            return false;
        }
    }

    private void saveAssignedParcel(String parcelId, String courierId, String status) {
        parcelRepository.saveAndFlush(new Parcel(parcelId, courierId, "SWF-TEST-" + parcelId, status,
                null, "Test Recipient", "0700000000", "Test address"));
        assignmentRepository.saveAndFlush(new ParcelAssignment(
                parcelId, courierId, Instant.now(), null, "Test assignment"));
    }

    private void dropFailureConstraint() {
        jdbcTemplate.execute("ALTER TABLE public.parcel_status_history DROP CONSTRAINT IF EXISTS "
                + FAILURE_CONSTRAINT);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
