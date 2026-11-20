package com.swiftdrop.parcel.otp;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.entity.ParcelDeliveryOtpChallenge;
import com.swiftdrop.parcel.exception.CourierAssignmentMismatchException;
import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryOtpChallengeRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.service.ParcelAssignmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:parcel-otp-test;DB_CLOSE_DELAY=-1")
@Import(OtpChallengeTransactionTest.TestClockConfiguration.class)
class OtpChallengeTransactionTest {
    private static final Instant NOW = Instant.parse("2026-11-14T06:30:00Z");
    private static final String ASSIGNMENT_FAILURE_CONSTRAINT = "ck_test_reject_assignment_for_otp";

    @Autowired private OtpChallengeWriter challengeWriter;
    @Autowired private OtpChallengeService challengeService;
    @Autowired private OtpCodeCryptography cryptography;
    @Autowired private MutableClock clock;
    @Autowired private ParcelRepository parcelRepository;
    @Autowired private ParcelAssignmentRepository assignmentRepository;
    @Autowired private ParcelDeliveryOtpChallengeRepository challengeRepository;
    @Autowired private ParcelAssignmentService assignmentService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanRows() {
        dropFailureConstraint();
        challengeRepository.deleteAll();
        assignmentRepository.deleteAll();
        parcelRepository.deleteAll();
        clock.setInstant(NOW);
    }

    @AfterEach
    void cleanupRows() {
        dropFailureConstraint();
        challengeRepository.deleteAll();
        assignmentRepository.deleteAll();
        parcelRepository.deleteAll();
    }

    @Test
    void successfulVerificationConsumesOtpButDoesNotDeliverParcelAndRejectsReplay() {
        Fixture fixture = createChallenge("prc_otp_success", "cou_001", "in_transit", "001234");

        OtpVerificationResult verified = challengeService.verify(
                fixture.parcelId(), fixture.challengeId(), "cou_001", "001234".toCharArray());

        assertEquals(OtpVerificationOutcome.VERIFIED, verified.outcome());
        ParcelDeliveryOtpChallenge saved = challengeRepository.findById(fixture.challengeId()).orElseThrow();
        assertEquals(NOW, saved.getVerifiedAt());
        assertEquals(NOW, saved.getOtpConsumedAt());
        assertNull(saved.getProofConsumedAt());
        assertEquals("in_transit", parcelRepository.findById(fixture.parcelId()).orElseThrow().getStatus());
        assertEquals(OtpVerificationOutcome.INVALID, challengeService.verify(
                fixture.parcelId(), fixture.challengeId(), "cou_001", "001234".toCharArray()).outcome());
    }

    @Test
    void incorrectAttemptsCommitAndFifthAttemptExhaustsChallenge() {
        Fixture fixture = createChallenge("prc_otp_attempts", "cou_001", "in_transit", "000001");
        for (int attempt = 1; attempt <= 5; attempt++) {
            OtpVerificationResult result = challengeService.verify(fixture.parcelId(), fixture.challengeId(),
                    "cou_001", "999999".toCharArray());
            assertEquals(attempt == 5 ? OtpVerificationOutcome.EXHAUSTED : OtpVerificationOutcome.INVALID,
                    result.outcome());
            assertEquals(attempt, challengeRepository.findById(fixture.challengeId()).orElseThrow()
                    .getAttemptCount());
        }
        assertNotNull(challengeRepository.findById(fixture.challengeId()).orElseThrow().getInvalidatedAt());
    }

    @Test
    void expiryAtExactBoundaryInvalidatesChallenge() {
        Fixture fixture = createChallenge("prc_otp_expiry", "cou_001", "in_transit", "123456");
        clock.setInstant(NOW.plusSeconds(300));

        OtpVerificationResult result = challengeService.verify(fixture.parcelId(), fixture.challengeId(),
                "cou_001", "123456".toCharArray());

        assertEquals(OtpVerificationOutcome.EXPIRED, result.outcome());
        assertNotNull(challengeRepository.findById(fixture.challengeId()).orElseThrow().getInvalidatedAt());
        assertEquals(0, challengeRepository.findById(fixture.challengeId()).orElseThrow().getAttemptCount());
    }

    @Test
    void verificationRequiresCurrentAssignedCourierAndActiveAssignment() {
        Fixture fixture = createChallenge("prc_otp_owner", "cou_001", "in_transit", "123456");

        assertThrows(CourierAssignmentMismatchException.class, () -> challengeService.verify(
                fixture.parcelId(), fixture.challengeId(), "cou_other", "123456".toCharArray()));
        assertEquals(0, challengeRepository.findById(fixture.challengeId()).orElseThrow().getAttemptCount());

        ParcelAssignment assignment = assignmentRepository.findByParcelIdAndUnassignedAtIsNull(
                fixture.parcelId()).orElseThrow();
        assignment.closeAt(NOW.plusSeconds(1));
        assignmentRepository.saveAndFlush(assignment);
        assertThrows(ParcelStatusConflictException.class, () -> challengeService.verify(
                fixture.parcelId(), fixture.challengeId(), "cou_001", "123456".toCharArray()));
    }

    @Test
    void resendCooldownRejectsBeforeSixtySecondsAndAllowsAtBoundaryWithInvalidation() {
        saveAssignedParcel("prc_otp_resend", "cou_001", "in_transit");
        PreparedOtpDelivery first = challengeWriter.prepare("prc_otp_resend", "cou_001");
        try {
            clock.setInstant(NOW.plusSeconds(59));
            assertThrows(OtpChallengeCooldownException.class,
                    () -> challengeWriter.prepare("prc_otp_resend", "cou_001"));

            clock.setInstant(NOW.plusSeconds(60));
            PreparedOtpDelivery second = challengeWriter.prepare("prc_otp_resend", "cou_001");
            try {
                assertEquals(2, challengeRepository.count());
                assertNotNull(challengeRepository.findById(first.challengeId()).orElseThrow().getInvalidatedAt());
                assertNull(challengeRepository.findById(second.challengeId()).orElseThrow().getInvalidatedAt());
            } finally {
                java.util.Arrays.fill(second.code(), '\0');
            }
        } finally {
            java.util.Arrays.fill(first.code(), '\0');
        }
    }

    @Test
    void reassignmentInvalidatesChallengeButIdempotentAssignmentDoesNot() {
        saveAssignedParcel("prc_otp_reassign", "cou_001", "in_transit");
        PreparedOtpDelivery prepared = challengeWriter.prepare("prc_otp_reassign", "cou_001");
        try {
            assignmentService.assign("prc_otp_reassign", "cou_001", null, "Repeat");
            assertNull(challengeRepository.findById(prepared.challengeId()).orElseThrow().getInvalidatedAt());

            assignmentService.assign("prc_otp_reassign", "cou_002", null, "Reassigned");
            assertNotNull(challengeRepository.findById(prepared.challengeId()).orElseThrow().getInvalidatedAt());
            assertEquals(OtpVerificationOutcome.INVALID, challengeService.verify("prc_otp_reassign",
                    prepared.challengeId(), "cou_002", prepared.code()).outcome());
        } finally {
            java.util.Arrays.fill(prepared.code(), '\0');
        }
    }

    @Test
    void reassignmentFailureRollsBackOtpInvalidation() {
        saveAssignedParcel("prc_otp_rollback", "cou_old", "in_transit");
        PreparedOtpDelivery prepared = challengeWriter.prepare("prc_otp_rollback", "cou_old");
        jdbcTemplate.execute("ALTER TABLE public.parcel_assignments ADD CONSTRAINT "
                + ASSIGNMENT_FAILURE_CONSTRAINT + " CHECK (reason <> 'force-rollback')");
        try {
            assertThrows(RuntimeException.class, () -> assignmentService.assign(
                    "prc_otp_rollback", "cou_new", null, "force-rollback"));
            assertNull(challengeRepository.findById(prepared.challengeId()).orElseThrow().getInvalidatedAt());
            assertEquals("cou_old", assignmentRepository
                    .findByParcelIdAndUnassignedAtIsNull("prc_otp_rollback").orElseThrow().getCourierId());
        } finally {
            java.util.Arrays.fill(prepared.code(), '\0');
            dropFailureConstraint();
        }
    }

    @Test
    void issueFailsWithoutDeliveryAdapterAndDoesNotCreateChallenge() {
        saveAssignedParcel("prc_otp_no_delivery", "cou_001", "in_transit");

        assertThrows(OtpDeliveryUnavailableException.class,
                () -> challengeService.issue("prc_otp_no_delivery", "cou_001"));
        assertEquals(0, challengeRepository.count());
    }

    @Test
    void concurrentWrongAttemptsAreSerializedAndBothAreCounted() throws Exception {
        Fixture fixture = createChallenge("prc_otp_concurrent", "cou_001", "in_transit", "012345");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<OtpVerificationResult> first = workers.submit(() -> verifyAfterBarrier(fixture, ready, start));
            Future<OtpVerificationResult> second = workers.submit(() -> verifyAfterBarrier(fixture, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertEquals(2, challengeRepository.findById(fixture.challengeId()).orElseThrow().getAttemptCount());
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    private OtpVerificationResult verifyAfterBarrier(Fixture fixture, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        start.await();
        return challengeService.verify(fixture.parcelId(), fixture.challengeId(),
                "cou_001", "999999".toCharArray());
    }

    private Fixture createChallenge(String parcelId, String courierId, String status, String code) {
        saveAssignedParcel(parcelId, courierId, status);
        ParcelAssignment assignment = assignmentRepository
                .findByParcelIdAndUnassignedAtIsNull(parcelId).orElseThrow();
        UUID id = UUID.randomUUID();
        char[] otp = code.toCharArray();
        byte[] hash = cryptography.hash(id, parcelId, assignment.getId(), courierId, otp);
        try {
            challengeRepository.saveAndFlush(new ParcelDeliveryOtpChallenge(id, parcelId,
                    assignment.getId(), courierId, hash, NOW, NOW.plusSeconds(300)));
        } finally {
            java.util.Arrays.fill(otp, '\0');
            java.util.Arrays.fill(hash, (byte) 0);
        }
        return new Fixture(parcelId, id);
    }

    private void saveAssignedParcel(String parcelId, String courierId, String status) {
        parcelRepository.saveAndFlush(new Parcel(parcelId, courierId, "SWF-TEST-" + parcelId,
                status, null, "Recipient", "+94770000000", "Test address"));
        assignmentRepository.saveAndFlush(new ParcelAssignment(parcelId, courierId,
                NOW, null, "Test assignment"));
    }

    private void dropFailureConstraint() {
        jdbcTemplate.execute("ALTER TABLE public.parcel_assignments DROP CONSTRAINT IF EXISTS "
                + ASSIGNMENT_FAILURE_CONSTRAINT);
    }

    private record Fixture(String parcelId, UUID challengeId) { }

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        MutableClock otpTestClock() {
            return new MutableClock(NOW);
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant instant;

        MutableClock(Instant instant) { this.instant = instant; }
        void setInstant(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
