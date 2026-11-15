package com.swiftdrop.parcel.postgres;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.entity.ParcelDeliveryOtpChallenge;
import com.swiftdrop.parcel.entity.ParcelDeliveryPhotoProof;
import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import com.swiftdrop.parcel.otp.OtpChallengeService;
import com.swiftdrop.parcel.otp.OtpCodeCryptography;
import com.swiftdrop.parcel.otp.OtpVerificationOutcome;
import com.swiftdrop.parcel.photo.DeliveryPhotoProofService;
import com.swiftdrop.parcel.photo.DeliveryPhotoProofConflictException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryOtpChallengeRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryPhotoProofRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import com.swiftdrop.parcel.service.DeliveryCompletionPolicy;
import com.swiftdrop.parcel.service.DeliveryCompletionPolicyNotApprovedException;
import com.swiftdrop.parcel.service.DeliveryCompletionService;
import com.swiftdrop.parcel.service.ParcelAssignmentService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import com.swiftdrop.parcel.service.ParcelStatusTransitionValidator;
import com.swiftdrop.parcel.service.UnapprovedDeliveryCompletionPolicy;
import com.swiftdrop.parcel.storage.DeliveryPhotoStorage;
import com.swiftdrop.parcel.storage.StoredDeliveryPhoto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Opt-in PostgreSQL tests. Only a loopback parcel_service_test database URL is accepted. */
@SpringBootTest
@ContextConfiguration(initializers = ParcelPostgresIntegrationTest.SeedLegacyParcelSchema.class)
@EnabledIfSystemProperty(named = "parcel.postgres.test.url",
        matches = "jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/parcel_service_test")
class ParcelPostgresIntegrationTest {
    private static final Pattern SAFE_LOCAL_URL = Pattern.compile(
            "jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/parcel_service_test");
    private static final String PHOTO_FAILURE_CONSTRAINT = "ck_pg_force_photo_failure";
    private static final String COMPLETION_FAILURE_CONSTRAINT = "ck_pg_force_completion_failure";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ParcelRepository parcelRepository;
    @Autowired private ParcelAssignmentRepository assignmentRepository;
    @Autowired private ParcelDeliveryOtpChallengeRepository otpRepository;
    @Autowired private ParcelDeliveryPhotoProofRepository photoRepository;
    @Autowired private ParcelStatusHistoryRepository historyRepository;
    @Autowired private ParcelAssignmentService assignmentService;
    @Autowired private ParcelStatusService statusService;
    @Autowired private OtpChallengeService otpService;
    @Autowired private OtpCodeCryptography otpCryptography;
    @Autowired private DeliveryPhotoProofService photoService;
    @Autowired private DeliveryCompletionService completionService;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockBean private DeliveryPhotoStorage photoStorage;
    @MockBean private DeliveryCompletionPolicy completionPolicy;

    @DynamicPropertySource
    static void useIsolatedPostgres(DynamicPropertyRegistry properties) {
        String url = safeUrl();
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        properties.add("spring.flyway.enabled", () -> "true");
        properties.add("spring.flyway.schemas", () -> "public");
        properties.add("spring.flyway.baseline-on-migrate", () -> "true");
        properties.add("spring.flyway.baseline-version", () -> "0");
    }

    @Test
    void migrationsV1ThroughV4ApplyToNonemptyLegacySchemaAndEnforceConstraints() {
        assertEquals(List.of("1", "2", "3", "4"), jdbc.queryForList(
                "select version from public.flyway_schema_history "
                        + "where success = true and version <> '0' order by installed_rank", String.class));
        assertEquals(4, jdbc.queryForObject(
                "select count(*) from public.flyway_schema_history where success = true and version <> '0'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from public.parcels where id = 'pg_legacy_seed'", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from public.parcel_assignments where parcel_id = 'pg_legacy_seed'", Integer.class));

        assertIndexContains("uq_parcel_assignments_one_active_per_parcel", "where (unassigned_at is null)");
        assertIndexContains("uq_parcel_otp_one_usable_per_parcel", "attempt_count < 5");
        assertIndexContains("uq_parcel_photo_proof_one_active_per_assignment", "invalidated_at is null");
        for (String constraint : List.of(
                "fk_parcel_assignments_parcel", "chk_parcel_assignments_time_range",
                "fk_parcel_status_history_parcel", "fk_parcel_otp_parcel", "fk_parcel_otp_assignment",
                "chk_parcel_otp_hash_length", "chk_parcel_otp_attempt_count", "chk_parcel_otp_expiry",
                "chk_parcel_otp_verified_consumed_together", "uq_parcel_assignments_id_parcel_courier",
                "fk_parcel_photo_proof_parcel", "fk_parcel_photo_proof_assignment_tuple",
                "uq_parcel_photo_proof_asset", "chk_parcel_photo_proof_size",
                "chk_parcel_photo_proof_format", "chk_parcel_photo_proof_digest_length",
                "chk_parcel_photo_proof_consumed_verified", "chk_parcel_photo_proof_not_consumed_and_invalidated",
                "chk_parcel_photo_proof_lifecycle_timestamps")) {
            assertConstraintExists(constraint);
        }

        String parcelA = id();
        String parcelB = id();
        ParcelAssignment assignmentA = seedAssigned(parcelA, "cou_pg_fk_a", "pending");
        ParcelAssignment assignmentB = seedAssigned(parcelB, "cou_pg_fk_b", "pending");
        assertThrows(DataIntegrityViolationException.class, () -> photoRepository.saveAndFlush(
                photoProof(parcelA, assignmentB, "asset-" + UUID.randomUUID(), Instant.now())));
        assertNotNull(assignmentA.getId());
    }

    @Test
    void concurrentAssignmentsLeaveOneActiveAndDeliveredParcelsCannotBeReassigned() throws Exception {
        String parcelId = id();
        parcelRepository.saveAndFlush(parcel(parcelId, null, "pending"));
        runTogether(() -> assignmentService.assign(parcelId, "cou_pg_a", null, "A"),
                () -> assignmentService.assign(parcelId, "cou_pg_b", null, "B"));

        List<ParcelAssignment> rows = assignmentsFor(parcelId);
        assertEquals(2, rows.size());
        List<ParcelAssignment> active = rows.stream().filter(row -> row.getUnassignedAt() == null).toList();
        assertEquals(1, active.size());
        assertEquals(parcelRepository.findById(parcelId).orElseThrow().getCourierId(), active.getFirst().getCourierId());
        rows.stream().filter(row -> row.getUnassignedAt() != null)
                .forEach(row -> assertFalse(row.getUnassignedAt().isBefore(row.getAssignedAt())));
        assertThrows(DataIntegrityViolationException.class, () -> assignmentRepository.saveAndFlush(
                new ParcelAssignment(parcelId, "cou_pg_duplicate", Instant.now(), null, "direct duplicate")));

        String deliveredId = id();
        seedAssigned(deliveredId, "cou_pg_delivered", "delivered");
        assertThrows(ParcelStatusConflictException.class,
                () -> assignmentService.assign(deliveredId, "cou_pg_other", null, "should fail"));
        assertEquals(1, assignmentsFor(deliveredId).stream().filter(row -> row.getUnassignedAt() == null).count());
    }

    @Test
    void concurrentStatusChangesCannotBypassValidationAndHistoryMatchesFinalState() throws Exception {
        String parcelId = id();
        seedAssigned(parcelId, "cou_pg_status", "pending");
        List<Object> outcomes = runCapturing(
                () -> statusService.updateStatus(parcelId, "cou_pg_status", "in_transit", null),
                () -> statusService.updateStatus(parcelId, "cou_pg_status", "in_transit", null));
        assertEquals(1, outcomes.stream().filter(value -> !(value instanceof ParcelStatusConflictException)).count());
        assertEquals(1, outcomes.stream().filter(ParcelStatusConflictException.class::isInstance).count());
        String finalStatus = parcelRepository.findById(parcelId).orElseThrow().getStatus();
        assertEquals("in_transit", finalStatus);
        var history = historyRepository.findByParcelIdOrderByChangedAtAscIdAsc(parcelId);
        assertEquals(1, history.size());
        assertEquals(finalStatus, history.getFirst().getNewStatus());
    }

    @Test
    void concurrentOtpVerificationAttemptsReassignmentAndExpiryAreSerialized() throws Exception {
        String parcelId = id();
        ParcelAssignment assignment = seedAssigned(parcelId, "cou_pg_otp", "in_transit");
        UUID challengeId = saveChallenge(parcelId, assignment, "cou_pg_otp", "004207", Instant.now());
        assertThrows(DataIntegrityViolationException.class, () -> saveChallenge(
                parcelId, assignment, "cou_pg_otp", "004208", Instant.now()));
        List<OtpVerificationOutcome> outcomes = runTogether(
                () -> otpService.verify(parcelId, challengeId, "cou_pg_otp", "004207".toCharArray()).outcome(),
                () -> otpService.verify(parcelId, challengeId, "cou_pg_otp", "004207".toCharArray()).outcome());
        assertEquals(1, outcomes.stream().filter(value -> value == OtpVerificationOutcome.VERIFIED).count());
        assertEquals(1, outcomes.stream().filter(value -> value == OtpVerificationOutcome.INVALID).count());
        assertNotNull(otpRepository.findById(challengeId).orElseThrow().getOtpConsumedAt());

        String attemptParcel = id();
        ParcelAssignment attemptAssignment = seedAssigned(attemptParcel, "cou_pg_attempts", "in_transit");
        UUID attemptId = saveChallenge(attemptParcel, attemptAssignment, "cou_pg_attempts", "123456", Instant.now());
        runFiveTogether(() -> otpService.verify(attemptParcel, attemptId, "cou_pg_attempts", "999999".toCharArray()));
        var exhausted = otpRepository.findById(attemptId).orElseThrow();
        assertEquals(5, exhausted.getAttemptCount());
        assertNotNull(exhausted.getInvalidatedAt());

        String reassignedParcel = id();
        ParcelAssignment oldAssignment = seedAssigned(reassignedParcel, "cou_pg_old", "in_transit");
        UUID reassignedId = saveChallenge(reassignedParcel, oldAssignment, "cou_pg_old", "867530", Instant.now());
        assignmentService.assign(reassignedParcel, "cou_pg_new", null, "reassignment");
        assertNotNull(otpRepository.findById(reassignedId).orElseThrow().getInvalidatedAt());

        String expiredParcel = id();
        ParcelAssignment expiredAssignment = seedAssigned(expiredParcel, "cou_pg_expired", "in_transit");
        UUID expiredId = saveChallenge(expiredParcel, expiredAssignment, "cou_pg_expired", "101010",
                Instant.now().minusSeconds(600));
        assertEquals(OtpVerificationOutcome.EXPIRED, otpService.verify(expiredParcel, expiredId,
                "cou_pg_expired", "101010".toCharArray()).outcome());
        assertNull(otpRepository.findById(expiredId).orElseThrow().getOtpConsumedAt());
    }

    @Test
    void concurrentPhotoUploadsEnforceOneActiveProofAndReassignmentInvalidatesProof() throws Exception {
        String parcelId = id();
        ParcelAssignment assignment = seedAssigned(parcelId, "cou_pg_photo", "in_transit");
        byte[] image = png();
        AtomicInteger uploads = new AtomicInteger();
        when(photoStorage.upload(any())).thenAnswer(invocation -> {
            String suffix = UUID.randomUUID().toString();
            uploads.incrementAndGet();
            return new StoredDeliveryPhoto("asset-" + suffix, "proof/" + suffix, "png",
                    ((byte[]) invocation.getArgument(0)).length, "image");
        });
        List<Object> results = runCapturing(
                () -> photoService.upload(parcelId, assignment.getId(), "cou_pg_photo", "image/png", image),
                () -> photoService.upload(parcelId, assignment.getId(), "cou_pg_photo", "image/png", image));
        assertEquals(1, results.stream().filter(DeliveryPhotoProofConflictException.class::isInstance).count());
        assertEquals(1, results.stream().filter(value -> !(value instanceof DeliveryPhotoProofConflictException)).count());
        assertEquals(1, uploads.get());
        assertEquals(1, jdbc.queryForObject("select count(*) from public.parcel_delivery_photo_proofs "
                + "where assignment_id = ? and consumed_at is null and invalidated_at is null",
                Integer.class, assignment.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> photoRepository.saveAndFlush(
                photoProof(parcelId, assignment, "asset-duplicate-" + UUID.randomUUID(), Instant.now())));

        UUID proofId = jdbc.queryForObject("select id from public.parcel_delivery_photo_proofs "
                + "where assignment_id = ? and consumed_at is null and invalidated_at is null",
                UUID.class, assignment.getId());
        assignmentService.assign(parcelId, "cou_pg_photo_new", null, "reassignment");
        assertNotNull(photoRepository.findById(proofId).orElseThrow().getInvalidatedAt());
    }

    @Test
    void photoPersistenceFailureRollsBackProofWithoutDeletingCloudinaryAsset() throws Exception {
        String parcelId = id();
        ParcelAssignment assignment = seedAssigned(parcelId, "cou_pg_photo_failure", "in_transit");
        jdbc.execute("ALTER TABLE public.parcel_delivery_photo_proofs ADD CONSTRAINT " + PHOTO_FAILURE_CONSTRAINT
                + " CHECK (cloudinary_public_id <> 'force-failure')");
        when(photoStorage.upload(any())).thenAnswer(invocation -> new StoredDeliveryPhoto(
                "asset-" + UUID.randomUUID(), "force-failure", "png",
                ((byte[]) invocation.getArgument(0)).length, "image"));
        try {
            assertThrows(DataIntegrityViolationException.class, () -> photoService.upload(
                    parcelId, assignment.getId(), "cou_pg_photo_failure", "image/png", png()));
            assertEquals(0, jdbc.queryForObject("select count(*) from public.parcel_delivery_photo_proofs "
                    + "where assignment_id = ?", Integer.class, assignment.getId()));
            verify(photoStorage, never()).deleteByAssetId(any());
        } finally {
            jdbc.execute("ALTER TABLE public.parcel_delivery_photo_proofs DROP CONSTRAINT IF EXISTS "
                    + PHOTO_FAILURE_CONSTRAINT);
        }
    }

    @Test
    void completionPolicyFailsClosedAndPostgresRollbackRestoresProofs() {
        CompletionFixture disabled = completionFixture("cou_pg_disabled");
        DeliveryCompletionService failClosedService = new DeliveryCompletionService(parcelRepository,
                assignmentRepository, otpRepository, photoRepository, historyRepository,
                new ParcelStatusTransitionValidator(), new UnapprovedDeliveryCompletionPolicy(),
                java.time.Clock.systemUTC());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(DeliveryCompletionPolicyNotApprovedException.class, () -> transaction.execute(status ->
                failClosedService.complete(disabled.parcelId(), disabled.challengeId(), disabled.proofId(),
                        "cou_pg_disabled")));
        assertCompletionUnchanged(disabled);

        CompletionFixture rollback = completionFixture("cou_pg_rollback");
        when(completionPolicy.validateProofsAndMapDeliveredAt(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> LocalDateTime.ofInstant(invocation.getArgument(4), ZoneOffset.UTC));
        jdbc.execute("ALTER TABLE public.parcel_status_history ADD CONSTRAINT " + COMPLETION_FAILURE_CONSTRAINT
                + " CHECK (new_status <> 'delivered')");
        try {
            assertThrows(DataIntegrityViolationException.class, () -> completionService.complete(
                    rollback.parcelId(), rollback.challengeId(), rollback.proofId(), "cou_pg_rollback"));
            assertCompletionUnchanged(rollback);
        } finally {
            jdbc.execute("ALTER TABLE public.parcel_status_history DROP CONSTRAINT IF EXISTS "
                    + COMPLETION_FAILURE_CONSTRAINT);
        }
    }

    private void assertIndexContains(String name, String fragment) {
        String definition = jdbc.queryForObject("select indexdef from pg_indexes "
                + "where schemaname = 'public' and indexname = ?", String.class, name);
        assertTrue(definition.toLowerCase().contains(fragment.toLowerCase()), definition);
    }

    private void assertConstraintExists(String name) {
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from pg_constraint where conname = ?", Integer.class, name), name);
    }

    private String id() { return "pg_" + UUID.randomUUID().toString().replace("-", ""); }

    private Parcel parcel(String parcelId, String courierId, String status) {
        return new Parcel(parcelId, courierId, "SWF-" + parcelId, status,
                null, "Test recipient", "+94770000000", "Test address");
    }

    private ParcelAssignment seedAssigned(String parcelId, String courierId, String status) {
        parcelRepository.saveAndFlush(parcel(parcelId, courierId, status));
        return assignmentRepository.saveAndFlush(new ParcelAssignment(parcelId, courierId,
                Instant.now().minusSeconds(30), null, "test fixture"));
    }

    private List<ParcelAssignment> assignmentsFor(String parcelId) {
        return assignmentRepository.findAll().stream().filter(row -> row.getParcelId().equals(parcelId)).toList();
    }

    private UUID saveChallenge(String parcelId, ParcelAssignment assignment, String courierId,
                               String code, Instant createdAt) {
        UUID challengeId = UUID.randomUUID();
        char[] otp = code.toCharArray();
        byte[] hash = otpCryptography.hash(challengeId, parcelId, assignment.getId(), courierId, otp);
        try {
            otpRepository.saveAndFlush(new ParcelDeliveryOtpChallenge(challengeId, parcelId,
                    assignment.getId(), courierId, hash, createdAt, createdAt.plusSeconds(300)));
        } finally {
            java.util.Arrays.fill(otp, '\0');
            java.util.Arrays.fill(hash, (byte) 0);
        }
        return challengeId;
    }

    private ParcelDeliveryPhotoProof photoProof(String parcelId, ParcelAssignment assignment,
                                                 String assetId, Instant uploadedAt) {
        return new ParcelDeliveryPhotoProof(UUID.randomUUID(), parcelId, assignment.getId(), assignment.getCourierId(),
                assetId, "proof/" + UUID.randomUUID(), "png", "image/png", 64, null, uploadedAt);
    }

    private CompletionFixture completionFixture(String courierId) {
        String parcelId = id();
        ParcelAssignment assignment = seedAssigned(parcelId, courierId, "in_transit");
        Instant now = Instant.now();
        UUID challengeId = UUID.randomUUID();
        ParcelDeliveryOtpChallenge challenge = new ParcelDeliveryOtpChallenge(challengeId, parcelId,
                assignment.getId(), courierId, new byte[32], now.minusSeconds(20), now.plusSeconds(280));
        challenge.verifyAndConsumeOtp(now.minusSeconds(10));
        otpRepository.saveAndFlush(challenge);
        ParcelDeliveryPhotoProof proof = photoRepository.saveAndFlush(photoProof(parcelId, assignment,
                "asset-" + UUID.randomUUID(), now.minusSeconds(5)));
        return new CompletionFixture(parcelId, challengeId, proof.getId());
    }

    private void assertCompletionUnchanged(CompletionFixture fixture) {
        assertEquals("in_transit", parcelRepository.findById(fixture.parcelId()).orElseThrow().getStatus());
        assertNull(otpRepository.findById(fixture.challengeId()).orElseThrow().getProofConsumedAt());
        assertNull(photoRepository.findById(fixture.proofId()).orElseThrow().getConsumedAt());
        assertTrue(historyRepository.findByParcelIdOrderByChangedAtAscIdAsc(fixture.parcelId()).isEmpty());
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private <T> List<T> runTogether(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<T> a = workers.submit(() -> { ready.countDown(); start.await(); return first.call(); });
            Future<T> b = workers.submit(() -> { ready.countDown(); start.await(); return second.call(); });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    private List<Object> runCapturing(Callable<?> first, Callable<?> second) throws Exception {
        List<Object> results = new ArrayList<>();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Object> a = workers.submit(() -> callCapturing(first, ready, start));
            Future<Object> b = workers.submit(() -> callCapturing(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            results.add(a.get(20, TimeUnit.SECONDS));
            results.add(b.get(20, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    private Object callCapturing(Callable<?> action, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            return action.call();
        } catch (Throwable failure) {
            return failure;
        }
    }

    private void runFiveTogether(Callable<?> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(5);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(5);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 5; index++) {
                futures.add(workers.submit((Callable<Object>) () -> {
                    ready.countDown();
                    start.await();
                    return action.call();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    private static String safeUrl() {
        String url = System.getProperty("parcel.postgres.test.url", "");
        if (!SAFE_LOCAL_URL.matcher(url).matches()) {
            throw new IllegalStateException("Only a loopback parcel_service_test PostgreSQL URL is permitted");
        }
        return url;
    }

    private record CompletionFixture(String parcelId, UUID challengeId, UUID proofId) { }

    public static final class SeedLegacyParcelSchema
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            String url = safeUrl();
            try (Connection connection = DriverManager.getConnection(url, "postgres", "");
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS public");
                statement.execute("CREATE TABLE IF NOT EXISTS public.parcels ("
                        + "id VARCHAR(64) PRIMARY KEY, tracking_number VARCHAR(64) NOT NULL UNIQUE, "
                        + "status VARCHAR(32) NOT NULL, courier_id VARCHAR(64), "
                        + "delivered_at TIMESTAMP WITHOUT TIME ZONE, estimated_delivery_date VARCHAR(64), "
                        + "recipient_name VARCHAR(255), recipient_phone VARCHAR(32), recipient_address VARCHAR(500))");
                statement.execute("INSERT INTO public.parcels (id, tracking_number, status, courier_id) "
                        + "VALUES ('pg_legacy_seed', 'SWF-PG-LEGACY', 'pending', 'cou_legacy') "
                        + "ON CONFLICT (id) DO NOTHING");
            } catch (Exception failure) {
                throw new IllegalStateException("Could not prepare isolated PostgreSQL test schema", failure);
            }
        }
    }
}
