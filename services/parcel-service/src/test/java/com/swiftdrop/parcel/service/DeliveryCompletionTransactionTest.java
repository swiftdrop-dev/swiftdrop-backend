package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.entity.ParcelDeliveryOtpChallenge;
import com.swiftdrop.parcel.entity.ParcelDeliveryPhotoProof;
import com.swiftdrop.parcel.exception.CourierAssignmentMismatchException;
import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryOtpChallengeRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryPhotoProofRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class DeliveryCompletionTransactionTest {
    private static final String FAILURE_CONSTRAINT = "ck_test_delivery_completion_failure";

    @Autowired private DeliveryCompletionService completionService;
    @Autowired private ParcelAssignmentService assignmentService;
    @Autowired private ParcelRepository parcelRepository;
    @Autowired private ParcelAssignmentRepository assignmentRepository;
    @Autowired private ParcelDeliveryOtpChallengeRepository otpRepository;
    @Autowired private ParcelDeliveryPhotoProofRepository photoRepository;
    @Autowired private ParcelStatusHistoryRepository historyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockBean private DeliveryCompletionPolicy completionPolicy;

    @SpyBean private ParcelRepository parcelRepositorySpy;

    @PersistenceContext private EntityManager entityManager;

    @BeforeEach
    void prepareIsolatedRowsAndTestPolicy() {
        dropFailureConstraint();
        photoRepository.deleteAll();
        otpRepository.deleteAll();
        historyRepository.deleteAll();
        assignmentRepository.deleteAll();
        parcelRepository.deleteAll();
        clearInvocations(completionPolicy, parcelRepositorySpy);
        when(completionPolicy.validateProofsAndMapDeliveredAt(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> LocalDateTime.ofInstant(invocation.getArgument(4), ZoneOffset.UTC));
    }

    @AfterEach
    void removeFailureConstraint() {
        dropFailureConstraint();
    }

    @Test
    void completesWithBothProofsAndConsumesThemAtomically() {
        Fixture fixture = createFixture("prc_complete_ok", "in_transit", "cou_complete");

        DeliveryCompletionResult result = completionService.complete(fixture.parcelId(), fixture.challengeId(),
                fixture.photoProofId(), "cou_complete");

        Parcel parcel = parcelRepository.findById(fixture.parcelId()).orElseThrow();
        ParcelDeliveryOtpChallenge challenge = otpRepository.findById(fixture.challengeId()).orElseThrow();
        ParcelDeliveryPhotoProof photo = photoRepository.findById(fixture.photoProofId()).orElseThrow();
        var history = historyRepository.findByParcelIdOrderByChangedAtAscIdAsc(fixture.parcelId());
        assertEquals("delivered", result.status());
        assertEquals("delivered", parcel.getStatus());
        Instant persistedCompletionInstant = result.completedAt();
        assertWithinOneMicrosecond(LocalDateTime.ofInstant(persistedCompletionInstant, ZoneOffset.UTC),
                parcel.getDeliveredAt());
        assertWithinOneMicrosecond(persistedCompletionInstant, challenge.getProofConsumedAt());
        assertWithinOneMicrosecond(persistedCompletionInstant, photo.getConsumedAt());
        assertEquals(1, history.size());
        assertEquals("in_transit", history.getFirst().getPreviousStatus());
        assertEquals("delivered", history.getFirst().getNewStatus());
        assertEquals("cou_complete", history.getFirst().getActorId());
    }

    @Test
    void rejectsWrongCourier() {
        Fixture fixture = createFixture("prc_complete_wrong_courier", "in_transit", "cou_expected");

        assertThrows(CourierAssignmentMismatchException.class, () -> completionService.complete(
                fixture.parcelId(), fixture.challengeId(), fixture.photoProofId(), "cou_other"));
        assertUnchanged(fixture);
    }

    @Test
    void rejectsOtpFromAnotherParcelAndPhotoFromAnotherAssignment() {
        Fixture first = createFixture("prc_complete_tuple_a", "in_transit", "cou_tuple");
        Fixture second = createFixture("prc_complete_tuple_b", "in_transit", "cou_tuple");

        assertThrows(ParcelStatusConflictException.class, () -> completionService.complete(
                first.parcelId(), second.challengeId(), first.photoProofId(), "cou_tuple"));
        assertThrows(ParcelStatusConflictException.class, () -> completionService.complete(
                first.parcelId(), first.challengeId(), second.photoProofId(), "cou_tuple"));
        assertUnchanged(first);
        assertUnchanged(second);
    }

    @Test
    void rejectsMissingOtpAndMissingPhotoProof() {
        Fixture fixture = createFixture("prc_complete_missing", "in_transit", "cou_missing");

        assertThrows(ParcelStatusConflictException.class, () -> completionService.complete(
                fixture.parcelId(), UUID.randomUUID(), fixture.photoProofId(), "cou_missing"));
        assertThrows(ParcelStatusConflictException.class, () -> completionService.complete(
                fixture.parcelId(), fixture.challengeId(), UUID.randomUUID(), "cou_missing"));
        assertUnchanged(fixture);
    }

    @Test
    void rejectsConsumedAndInvalidatedProofs() {
        Fixture otpConsumed = createFixture("prc_complete_otp_consumed", "in_transit", "cou_state");
        var consumedChallenge = otpRepository.findById(otpConsumed.challengeId()).orElseThrow();
        consumedChallenge.consumeProofAt(Instant.now());
        otpRepository.saveAndFlush(consumedChallenge);
        assertThrows(ParcelStatusConflictException.class, () -> completionService.complete(
                otpConsumed.parcelId(), otpConsumed.challengeId(), otpConsumed.photoProofId(), "cou_state"));

        Fixture photoConsumed = createFixture("prc_complete_photo_consumed", "in_transit", "cou_state");
        var consumedPhoto = photoRepository.findById(photoConsumed.photoProofId()).orElseThrow();
        consumedPhoto.consumeAt(Instant.now());
        photoRepository.saveAndFlush(consumedPhoto);
        assertThrows(ParcelStatusConflictException.class, () -> completionService.complete(
                photoConsumed.parcelId(), photoConsumed.challengeId(), photoConsumed.photoProofId(), "cou_state"));

        Fixture invalidated = createFixture("prc_complete_invalidated", "in_transit", "cou_state");
        var invalidatedChallenge = otpRepository.findById(invalidated.challengeId()).orElseThrow();
        invalidatedChallenge.invalidateAt(Instant.now());
        otpRepository.saveAndFlush(invalidatedChallenge);
        var invalidatedPhoto = photoRepository.findById(invalidated.photoProofId()).orElseThrow();
        invalidatedPhoto.invalidateAt(Instant.now());
        photoRepository.saveAndFlush(invalidatedPhoto);
        assertThrows(ParcelStatusConflictException.class, () -> completionService.complete(
                invalidated.parcelId(), invalidated.challengeId(), invalidated.photoProofId(), "cou_state"));
        assertUnchanged(invalidated);

        Fixture invalidatedPhotoFixture = createFixture(
                "prc_complete_photo_invalidated", "in_transit", "cou_state");
        var invalidatedOnlyPhoto = photoRepository.findById(invalidatedPhotoFixture.photoProofId()).orElseThrow();
        invalidatedOnlyPhoto.invalidateAt(Instant.now());
        photoRepository.saveAndFlush(invalidatedOnlyPhoto);
        assertThrows(ParcelStatusConflictException.class, () -> completionService.complete(
                invalidatedPhotoFixture.parcelId(), invalidatedPhotoFixture.challengeId(),
                invalidatedPhotoFixture.photoProofId(), "cou_state"));
        assertUnchanged(invalidatedPhotoFixture);
    }

    @Test
    void rejectsWrongParcelStatusAndOrdinaryValidatorStillBlocksDelivery() {
        Fixture fixture = createFixture("prc_complete_wrong_status", "pending", "cou_pending");

        assertThrows(ParcelStatusConflictException.class, () -> completionService.complete(
                fixture.parcelId(), fixture.challengeId(), fixture.photoProofId(), "cou_pending"));
        assertThrows(ParcelStatusConflictException.class,
                () -> new ParcelStatusTransitionValidator().validate("in_transit", "delivered"));
        assertUnchanged(fixture);
    }

    @Test
    void productionPolicyFailsClosedUntilExpiryAndTimestampRulesAreApproved() {
        Fixture fixture = createFixture("prc_complete_disabled", "in_transit", "cou_disabled");
        DeliveryCompletionService failClosedService = new DeliveryCompletionService(parcelRepository,
                assignmentRepository, otpRepository, photoRepository, historyRepository,
                new ParcelStatusTransitionValidator(), new UnapprovedDeliveryCompletionPolicy(),
                java.time.Clock.systemUTC());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        assertThrows(DeliveryCompletionPolicyNotApprovedException.class, () -> transactionTemplate.execute(status ->
                failClosedService.complete(fixture.parcelId(), fixture.challengeId(), fixture.photoProofId(),
                        "cou_disabled")));
        assertUnchanged(fixture);
    }

    @Test
    void transactionRollbackRestoresBothProofsParcelAndHistory() {
        Fixture fixture = createFixture("prc_complete_rollback", "in_transit", "cou_rollback");
        jdbcTemplate.execute("ALTER TABLE public.parcel_status_history ADD CONSTRAINT " + FAILURE_CONSTRAINT
                + " CHECK (new_status <> 'delivered')");

        assertThrows(DataIntegrityViolationException.class, () -> completionService.complete(
                fixture.parcelId(), fixture.challengeId(), fixture.photoProofId(), "cou_rollback"));

        assertUnchanged(fixture);
    }

    @Test
    void concurrentCompletionSerializesAndRejectsReplay() throws Exception {
        Fixture fixture = createFixture("prc_complete_concurrent", "in_transit", "cou_concurrent");
        CountDownLatch firstPolicyEntered = new CountDownLatch(1);
        CountDownLatch allowFirstToFinish = new CountDownLatch(1);
        CountDownLatch secondParcelLockAttempted = new CountDownLatch(1);
        AtomicInteger lockCalls = new AtomicInteger();
        AtomicInteger policyCalls = new AtomicInteger();
        installParcelLockProbe(lockCalls, secondParcelLockAttempted, fixture.parcelId());
        doAnswer(invocation -> {
            if (policyCalls.incrementAndGet() == 1) {
                firstPolicyEntered.countDown();
                if (!allowFirstToFinish.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test coordination timed out");
                }
            }
            return LocalDateTime.ofInstant(invocation.getArgument(2), ZoneOffset.UTC);
        }).when(completionPolicy).validateProofsAndMapDeliveredAt(any(), any(), any(), any(), any());

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<DeliveryCompletionResult> first = workers.submit(() -> completionService.complete(
                    fixture.parcelId(), fixture.challengeId(), fixture.photoProofId(), "cou_concurrent"));
            assertTrue(firstPolicyEntered.await(5, TimeUnit.SECONDS));
            Future<?> second = workers.submit(() -> completionService.complete(
                    fixture.parcelId(), fixture.challengeId(), fixture.photoProofId(), "cou_concurrent"));
            assertTrue(secondParcelLockAttempted.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> second.get(250, TimeUnit.MILLISECONDS));
            allowFirstToFinish.countDown();
            first.get(10, TimeUnit.SECONDS);
            ExecutionException replay = assertThrows(ExecutionException.class,
                    () -> second.get(10, TimeUnit.SECONDS));
            assertInstanceOf(ParcelStatusConflictException.class, replay.getCause());
            assertEquals(1, historyRepository.findByParcelIdOrderByChangedAtAscIdAsc(fixture.parcelId()).size());
            assertEquals(1, photoRepository.count());
            assertEquals(1, otpRepository.count());
        } finally {
            allowFirstToFinish.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void reassignmentWaitsForCompletionThenRejectsDeliveredParcel() throws Exception {
        Fixture fixture = createFixture("prc_complete_reassign_race", "in_transit", "cou_before");
        CountDownLatch firstPolicyEntered = new CountDownLatch(1);
        CountDownLatch allowFirstToFinish = new CountDownLatch(1);
        CountDownLatch secondParcelLockAttempted = new CountDownLatch(1);
        AtomicInteger lockCalls = new AtomicInteger();
        installParcelLockProbe(lockCalls, secondParcelLockAttempted, fixture.parcelId());
        doAnswer(invocation -> {
            firstPolicyEntered.countDown();
            if (!allowFirstToFinish.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test coordination timed out");
            }
            return LocalDateTime.ofInstant(invocation.getArgument(2), ZoneOffset.UTC);
        }).when(completionPolicy).validateProofsAndMapDeliveredAt(any(), any(), any(), any(), any());

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<DeliveryCompletionResult> completion = workers.submit(() -> completionService.complete(
                    fixture.parcelId(), fixture.challengeId(), fixture.photoProofId(), "cou_before"));
            assertTrue(firstPolicyEntered.await(5, TimeUnit.SECONDS));
            Future<?> reassignment = workers.submit(() -> assignmentService.assign(
                    fixture.parcelId(), "cou_after", null, "concurrency test"));
            assertTrue(secondParcelLockAttempted.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> reassignment.get(250, TimeUnit.MILLISECONDS));
            allowFirstToFinish.countDown();
            completion.get(10, TimeUnit.SECONDS);
            ExecutionException blockedReassignment = assertThrows(ExecutionException.class,
                    () -> reassignment.get(10, TimeUnit.SECONDS));
            assertInstanceOf(ParcelStatusConflictException.class, blockedReassignment.getCause());
            assertEquals("cou_before", assignmentRepository
                    .findByParcelIdAndUnassignedAtIsNull(fixture.parcelId()).orElseThrow().getCourierId());
            assertEquals("delivered", parcelRepository.findById(fixture.parcelId()).orElseThrow().getStatus());
            assertEquals(1, historyRepository.findByParcelIdOrderByChangedAtAscIdAsc(fixture.parcelId()).size());
        } finally {
            allowFirstToFinish.countDown();
            workers.shutdownNow();
        }
    }

    private Fixture createFixture(String parcelId, String status, String courierId) {
        parcelRepository.saveAndFlush(new Parcel(parcelId, courierId,
                "SWF-" + parcelId, status, null, "Recipient", "0700000000", "Address"));
        ParcelAssignment assignment = assignmentRepository.saveAndFlush(new ParcelAssignment(
                parcelId, courierId, Instant.now().minusSeconds(60), null, null));
        Instant now = Instant.now();
        UUID challengeId = UUID.randomUUID();
        ParcelDeliveryOtpChallenge challenge = new ParcelDeliveryOtpChallenge(challengeId, parcelId,
                assignment.getId(), courierId, new byte[32], now.minusSeconds(30), now.plusSeconds(300));
        challenge.verifyAndConsumeOtp(now.minusSeconds(1));
        otpRepository.saveAndFlush(challenge);
        UUID photoProofId = UUID.randomUUID();
        ParcelDeliveryPhotoProof photoProof = new ParcelDeliveryPhotoProof(photoProofId, parcelId,
                assignment.getId(), courierId, "asset-" + photoProofId, "proof/" + photoProofId,
                "png", "image/png", png().length, null, now.minusSeconds(2));
        photoRepository.saveAndFlush(photoProof);
        return new Fixture(parcelId, status, challengeId, photoProofId);
    }

    private void assertUnchanged(Fixture fixture) {
        assertEquals(fixture.status(), parcelRepository.findById(fixture.parcelId()).orElseThrow().getStatus());
        assertNull(otpRepository.findById(fixture.challengeId()).orElseThrow().getProofConsumedAt());
        assertNull(photoRepository.findById(fixture.photoProofId()).orElseThrow().getConsumedAt());
        assertTrue(historyRepository.findByParcelIdOrderByChangedAtAscIdAsc(fixture.parcelId()).isEmpty());
    }

    private void assertWithinOneMicrosecond(Instant expected, Instant actual) {
        assertTrue(Math.abs(Duration.between(expected, actual).toNanos()) <= 1_000,
                "Expected " + expected + " to be within one microsecond of " + actual);
    }

    private void assertWithinOneMicrosecond(LocalDateTime expected, LocalDateTime actual) {
        assertTrue(Math.abs(Duration.between(expected, actual).toNanos()) <= 1_000,
                "Expected " + expected + " to be within one microsecond of " + actual);
    }

    private void installParcelLockProbe(AtomicInteger calls, CountDownLatch secondAttempt, String parcelId) {
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) {
                secondAttempt.countDown();
            }
            return entityManager.createQuery("select parcel from Parcel parcel where parcel.id = :parcelId", Parcel.class)
                    .setParameter("parcelId", invocation.getArgument(0))
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getResultStream()
                    .findFirst();
        }).when(parcelRepositorySpy).findByIdForUpdate(parcelId);
    }

    private byte[] png() {
        try {
            BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void dropFailureConstraint() {
        jdbcTemplate.execute("ALTER TABLE public.parcel_status_history DROP CONSTRAINT IF EXISTS "
                + FAILURE_CONSTRAINT);
    }

    private record Fixture(String parcelId, String status, UUID challengeId, UUID photoProofId) {
    }
}
