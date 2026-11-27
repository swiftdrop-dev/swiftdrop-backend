package com.swiftdrop.dispatch.db;

import com.swiftdrop.dispatch.entity.CourierStatusCache;
import com.swiftdrop.dispatch.entity.DispatchAssignment;
import com.swiftdrop.dispatch.entity.DispatchJob;
import com.swiftdrop.dispatch.entity.DispatchZone;
import com.swiftdrop.dispatch.entity.OutboxEvent;
import org.flywaydb.core.Flyway;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchFlywayMigrationTest {

    private static final UUID EXISTING_COURIER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID KEPT_JOB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OPEN_JOB = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Test
    void upgradesExistingUuidDataAndAllowsNullableFeeAndCoordinates() throws Exception {
        migrate(DispatchPostgres.UPGRADE_URL, "1");
        insertV1Data();
        assertThat(count("dispatch_jobs")).isEqualTo(2);
        assertThat(count("dispatch_assignments")).isEqualTo(1);
        assertThat(count("courier_status_cache")).isEqualTo(1);

        migrate(DispatchPostgres.UPGRADE_URL, null);

        assertThat(count("dispatch_jobs")).isEqualTo(2);
        assertThat(count("dispatch_assignments")).isEqualTo(1);
        assertThat(count("courier_status_cache")).isEqualTo(1);
        assertThat(text("SELECT courier_id FROM dispatch_jobs WHERE id = ?", KEPT_JOB)).isEqualTo(EXISTING_COURIER.toString());
        assertThat(text("SELECT courier_id FROM dispatch_assignments WHERE dispatch_job_id = ?", KEPT_JOB))
                .isEqualTo(EXISTING_COURIER.toString());
        assertThat(text("SELECT courier_id FROM courier_status_cache WHERE courier_name = 'Kept Courier'", null))
                .isEqualTo(EXISTING_COURIER.toString());
        assertThat(text("SELECT tracking_number FROM dispatch_jobs WHERE id = ?", KEPT_JOB)).isEqualTo("SWF-KEPT");
        assertThat(text("SELECT status FROM dispatch_jobs WHERE id = ?", KEPT_JOB)).isEqualTo("ACCEPTED");
        assertThat(text("SELECT pickup_street_address FROM dispatch_jobs WHERE id = ?", OPEN_JOB)).isEqualTo("9 Open Street");
        assertThat(column("dispatch_jobs", "courier_id")).isEqualTo("varchar|64|YES");
        assertThat(column("dispatch_assignments", "courier_id")).isEqualTo("varchar|64|NO");
        assertThat(column("courier_status_cache", "courier_id")).isEqualTo("varchar|64|NO");
        assertThat(column("dispatch_jobs", "total_fee")).isEqualTo("numeric|12|YES");
        assertThat(column("dispatch_jobs", "pickup_latitude")).endsWith("|YES");
        assertThat(column("dispatch_jobs", "pickup_longitude")).endsWith("|YES");
        assertThat(column("dispatch_jobs", "delivery_latitude")).endsWith("|YES");
        assertThat(column("dispatch_jobs", "delivery_longitude")).endsWith("|YES");

        execute("UPDATE dispatch_jobs SET total_fee = 1250.50 WHERE id = ?", KEPT_JOB);
        assertThat(text("SELECT total_fee::text FROM dispatch_jobs WHERE id = ?", KEPT_JOB)).isEqualTo("1250.50");
        assertThat(text("SELECT total_fee::text FROM dispatch_jobs WHERE id = ?", OPEN_JOB)).isNull();
        execute("UPDATE dispatch_jobs SET pickup_latitude = NULL, pickup_longitude = NULL, delivery_latitude = NULL, delivery_longitude = NULL WHERE id = ?", OPEN_JOB);
        assertThat(text("SELECT pickup_latitude::text FROM dispatch_jobs WHERE id = ?", OPEN_JOB)).isNull();
        assertThat(text("SELECT pickup_latitude::text FROM dispatch_jobs WHERE id = ?", KEPT_JOB)).isNotNull();
        assertThat(countOn(DispatchPostgres.UPGRADE_URL, "SELECT COUNT(*) FROM dispatch_jobs j LEFT JOIN dispatch_zones z ON j.zone_id = z.id WHERE j.status IN ('PENDING_DISPATCH','DECLINED','EXPIRED') AND (?::varchar IS NULL OR z.zone_code = ?)", "ZONE-KEEP", "ZONE-KEEP"))
                .isEqualTo(1);
        validateSchema(DispatchPostgres.UPGRADE_URL);
        assertJobLockRejectsTheSecondDispatcher();
        assertCourierLockRejectsTheSecondOffer();
    }

    @Test
    void appliesV1AndV2TogetherOnAFreshDatabase() throws Exception {
        migrate(DispatchPostgres.RUNTIME_URL, null);
        assertThat(countOn(DispatchPostgres.RUNTIME_URL, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true AND version IN ('1','2')")).isEqualTo(2);
        UUID freshJob = UUID.randomUUID();
        try (Connection connection = connection(DispatchPostgres.RUNTIME_URL);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO dispatch_jobs (
                         id, parcel_id, tracking_number, merchant_id, courier_id,
                         pickup_street_address, pickup_city, pickup_latitude, pickup_longitude,
                         delivery_street_address, delivery_city, delivery_latitude, delivery_longitude,
                         parcel_weight_kg, total_fee, status
                     ) VALUES (?, ?, 'SWF-FRESH', ?, 'cou_001', '1 Fresh Street', 'Colombo', NULL, NULL, '2 Fresh Street', 'Kandy', NULL, NULL, 1.25, NULL, 'PENDING_DISPATCH')
                     """)) {
            statement.setObject(1, freshJob);
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, UUID.randomUUID());
            statement.executeUpdate();
        }
        assertThat(text("SELECT courier_id FROM dispatch_jobs WHERE id = ?", DispatchPostgres.RUNTIME_URL, freshJob)).isEqualTo("cou_001");
        assertThat(text("SELECT total_fee::text FROM dispatch_jobs WHERE id = ?", DispatchPostgres.RUNTIME_URL, freshJob)).isNull();
        assertThat(text("SELECT pickup_latitude::text FROM dispatch_jobs WHERE id = ?", DispatchPostgres.RUNTIME_URL, freshJob)).isNull();
        validateSchema(DispatchPostgres.RUNTIME_URL);
    }

    private static void assertJobLockRejectsTheSecondDispatcher() throws Exception {
        UUID jobId = insertPendingJob("SWF-LOCK-JOB", "cou_lock_job");
        int[] outcomes = race(jobId, jobId, "cou_lock_job");
        assertThat(outcomes[0]).isEqualTo(1);
        assertThat(outcomes[1]).isEqualTo(1);
        assertThat(text("SELECT status FROM dispatch_jobs WHERE id = ?", jobId)).isEqualTo("ASSIGNED_PENDING_ACCEPTANCE");
    }

    private static void assertCourierLockRejectsTheSecondOffer() throws Exception {
        UUID first = insertPendingJob("SWF-LOCK-A", "cou_shared");
        UUID second = insertPendingJob("SWF-LOCK-B", "cou_shared");
        int[] outcomes = race(first, second, "cou_shared");
        assertThat(outcomes[0]).isEqualTo(1);
        assertThat(outcomes[1]).isEqualTo(1);
        assertThat(countOn(DispatchPostgres.UPGRADE_URL, "SELECT COUNT(*) FROM dispatch_jobs WHERE courier_id = 'cou_shared' AND status = 'ASSIGNED_PENDING_ACCEPTANCE'"))
                .isEqualTo(1);
    }

    private static int[] race(UUID firstJob, UUID secondJob, String courierId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = pool.submit(() -> offer(firstJob, courierId, ready, start));
            Future<Boolean> second = pool.submit(() -> offer(secondJob, courierId, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int assigned = (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            int rejected = 2 - assigned;
            return new int[]{assigned, rejected};
        } finally {
            pool.shutdownNow();
        }
    }

    private static boolean offer(UUID jobId, String courierId, CountDownLatch ready, CountDownLatch start) throws Exception {
        try (Connection connection = connection(DispatchPostgres.UPGRADE_URL)) {
            connection.setAutoCommit(false);
            connection.createStatement().execute("SET lock_timeout = '5s'");
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                connection.rollback();
                return false;
            }
            String status;
            try (PreparedStatement lock = connection.prepareStatement("SELECT status FROM dispatch_jobs WHERE id = ? FOR UPDATE")) {
                lock.setObject(1, jobId);
                try (ResultSet rows = lock.executeQuery()) {
                    rows.next();
                    status = rows.getString(1);
                }
            }
            if (!"PENDING_DISPATCH".equals(status)) {
                connection.rollback();
                return false;
            }
            try (PreparedStatement lock = connection.prepareStatement("SELECT courier_id FROM courier_status_cache WHERE courier_id = ? FOR UPDATE")) {
                lock.setString(1, courierId);
                try (ResultSet rows = lock.executeQuery()) {
                    if (!rows.next()) {
                        connection.rollback();
                        return false;
                    }
                }
            }
            long active;
            try (PreparedStatement exists = connection.prepareStatement("""
                    SELECT COUNT(*) FROM dispatch_jobs
                    WHERE courier_id = ? AND status IN ('ASSIGNED_PENDING_ACCEPTANCE','ACCEPTED','IN_PROGRESS') AND id <> ?
                    """)) {
                exists.setString(1, courierId);
                exists.setObject(2, jobId);
                try (ResultSet rows = exists.executeQuery()) {
                    rows.next();
                    active = rows.getLong(1);
                }
            }
            if (active > 0) {
                connection.rollback();
                return false;
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE dispatch_jobs SET status = 'ASSIGNED_PENDING_ACCEPTANCE', courier_id = ? WHERE id = ?")) {
                update.setString(1, courierId);
                update.setObject(2, jobId);
                update.executeUpdate();
            }
            connection.commit();
            return true;
        }
    }

    private static UUID insertPendingJob(String trackingNumber, String courierId) throws Exception {
        UUID jobId = UUID.randomUUID();
        try (Connection connection = connection(DispatchPostgres.UPGRADE_URL);
             PreparedStatement cache = connection.prepareStatement("""
                     INSERT INTO courier_status_cache (courier_id, courier_name, courier_phone, is_online, is_available)
                     VALUES (?, ?, '+94770000000', true, true)
                     ON CONFLICT (courier_id) DO NOTHING
                     """);
             PreparedStatement job = connection.prepareStatement("""
                     INSERT INTO dispatch_jobs (
                         id, parcel_id, tracking_number, merchant_id,
                         pickup_street_address, pickup_city, pickup_latitude, pickup_longitude,
                         delivery_street_address, delivery_city, delivery_latitude, delivery_longitude,
                         parcel_weight_kg, status
                     ) VALUES (?, ?, ?, ?, '1 Lock Street', 'Colombo', 6.9271, 79.8612, '2 Lock Street', 'Kandy', 7.2906, 80.6337, 1.00, 'PENDING_DISPATCH')
                     """)) {
            cache.setString(1, courierId);
            cache.setString(2, courierId);
            cache.executeUpdate();
            job.setObject(1, jobId);
            job.setObject(2, UUID.randomUUID());
            job.setString(3, trackingNumber);
            job.setObject(4, UUID.randomUUID());
            job.executeUpdate();
        }
        return jobId;
    }

    private static void insertV1Data() throws Exception {
        UUID zoneId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        try (Connection connection = connection(DispatchPostgres.UPGRADE_URL)) {
            try (PreparedStatement zone = connection.prepareStatement("""
                    INSERT INTO dispatch_zones (id, zone_code, zone_name, center_latitude, center_longitude)
                    VALUES (?, 'ZONE-KEEP', 'Kept Zone', 6.9271000, 79.8612000)
                    """)) {
                zone.setObject(1, zoneId);
                zone.executeUpdate();
            }
            try (PreparedStatement job = connection.prepareStatement("""
                    INSERT INTO dispatch_jobs (
                        id, parcel_id, tracking_number, merchant_id, zone_id, courier_id,
                        pickup_street_address, pickup_city, pickup_latitude, pickup_longitude,
                        delivery_street_address, delivery_city, delivery_latitude, delivery_longitude,
                        parcel_weight_kg, status
                    ) VALUES (?, ?, 'SWF-KEPT', ?, ?, ?, '4 Kept Street', 'Colombo', 6.9271000, 79.8612000, '8 Kept Street', 'Kandy', 7.2906000, 80.6337000, 2.50, 'ACCEPTED')
                    """)) {
                job.setObject(1, KEPT_JOB);
                job.setObject(2, parcelId);
                job.setObject(3, merchantId);
                job.setObject(4, zoneId);
                job.setObject(5, EXISTING_COURIER);
                job.executeUpdate();
            }
            try (PreparedStatement open = connection.prepareStatement("""
                    INSERT INTO dispatch_jobs (
                        id, parcel_id, tracking_number, merchant_id, zone_id,
                        pickup_street_address, pickup_city, pickup_latitude, pickup_longitude,
                        delivery_street_address, delivery_city, delivery_latitude, delivery_longitude,
                        parcel_weight_kg, status
                    ) VALUES (?, ?, 'SWF-OPEN', ?, ?, '9 Open Street', 'Colombo', 6.9100000, 79.8600000, '3 Open Street', 'Galle', 6.0535000, 80.2210000, 1.00, 'PENDING_DISPATCH')
                    """)) {
                open.setObject(1, OPEN_JOB);
                open.setObject(2, UUID.randomUUID());
                open.setObject(3, merchantId);
                open.setObject(4, zoneId);
                open.executeUpdate();
            }
            try (PreparedStatement assignment = connection.prepareStatement("""
                    INSERT INTO dispatch_assignments (dispatch_job_id, courier_id, attempt_number, decision)
                    VALUES (?, ?, 1, 'ACCEPTED')
                    """)) {
                assignment.setObject(1, KEPT_JOB);
                assignment.setObject(2, EXISTING_COURIER);
                assignment.executeUpdate();
            }
            try (PreparedStatement cache = connection.prepareStatement("""
                    INSERT INTO courier_status_cache (
                        courier_id, courier_name, courier_phone, is_online, is_available, current_latitude, current_longitude
                    ) VALUES (?, 'Kept Courier', '+94771111111', true, true, 6.9271000, 79.8612000)
                    """)) {
                cache.setObject(1, EXISTING_COURIER);
                cache.executeUpdate();
            }
        }
    }

    private static void migrate(String jdbcUrl, String target) {
        var configuration = Flyway.configure()
                .dataSource(jdbcUrl, DispatchPostgres.username(), DispatchPostgres.password())
                .locations("classpath:db/migration")
                .cleanDisabled(true);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static void validateSchema(String jdbcUrl) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.JAKARTA_JDBC_URL, jdbcUrl)
                .applySetting(AvailableSettings.JAKARTA_JDBC_USER, DispatchPostgres.username())
                .applySetting(AvailableSettings.JAKARTA_JDBC_PASSWORD, DispatchPostgres.password())
                .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "validate")
                .build();
        try (var sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(DispatchZone.class)
                .addAnnotatedClass(DispatchJob.class)
                .addAnnotatedClass(DispatchAssignment.class)
                .addAnnotatedClass(CourierStatusCache.class)
                .addAnnotatedClass(OutboxEvent.class)
                .buildMetadata()
                .buildSessionFactory()) {
            assertThat(sessionFactory).isNotNull();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static Connection connection(String jdbcUrl) throws Exception {
        return DriverManager.getConnection(jdbcUrl, DispatchPostgres.username(), DispatchPostgres.password());
    }

    private static Connection connection() throws Exception {
        return connection(DispatchPostgres.UPGRADE_URL);
    }

    private static int count(String table) throws Exception {
        return countOn(DispatchPostgres.UPGRADE_URL, "SELECT COUNT(*) FROM " + table);
    }

    private static int countOn(String jdbcUrl, String sql, Object... parameters) throws Exception {
        try (Connection connection = connection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static String text(String sql, Object parameter) throws Exception {
        return text(sql, DispatchPostgres.UPGRADE_URL, parameter);
    }

    private static String text(String sql, String jdbcUrl, Object parameter) throws Exception {
        try (Connection connection = connection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) {
                statement.setObject(1, parameter);
            }
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                String value = rows.getString(1);
                return rows.wasNull() ? null : value;
            }
        }
    }

    private static void execute(String sql, Object parameter) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            statement.executeUpdate();
        }
    }

    private static String column(String table, String column) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT data_type, COALESCE(character_maximum_length, numeric_precision, 0), is_nullable
                     FROM information_schema.columns
                     WHERE table_name = ? AND column_name = ?
                     """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                String type = "character varying".equals(rows.getString(1)) ? "varchar" : rows.getString(1);
                return type + "|" + rows.getInt(2) + "|" + rows.getString(3);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws Exception {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }
}
