package com.swiftdrop.auth.flyway;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class AuthFlywayMigrationTest {

    @Test
    @DisplayName("Scenario A: Fresh database migration applies V1, V2, and V3 cleanly with OAuth columns")
    void testFreshDatabaseMigration() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:auth_fresh_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        var result = flyway.migrate();
        assertTrue(result.migrationsExecuted >= 3, "Expected at least V1, V2, and V3 to be executed on fresh DB");

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {

            // Verify users table exists and column active exists
            stmt.executeUpdate("INSERT INTO users (id, name, email, password, phone, role, active) " +
                    "VALUES ('usr_fresh_1', 'Fresh User', 'fresh@swiftdrop.lk', 'hashedpw', '0771234567', 'admin', true)");

            try (ResultSet rs = stmt.executeQuery("SELECT id, name, email, role, active, provider, provider_id FROM users WHERE id = 'usr_fresh_1'")) {
                assertTrue(rs.next(), "Inserted user should be found");
                assertEquals("Fresh User", rs.getString("name"));
                assertEquals("fresh@swiftdrop.lk", rs.getString("email"));
                assertEquals("admin", rs.getString("role"));
                assertTrue(rs.getBoolean("active"), "User active flag should be true");
                assertEquals("local", rs.getString("provider"), "Default provider should be 'local'");
                assertNull(rs.getString("provider_id"), "Default provider_id should be null");
            }

            // Verify Google OAuth user insertion with provider and provider_id
            stmt.executeUpdate("INSERT INTO users (id, name, email, password, phone, role, active, provider, provider_id) " +
                    "VALUES ('usr_google_1', 'Google User', 'google@swiftdrop.lk', 'hashedpw', '0779998877', 'customer', true, 'google', 'g_sub_1001')");

            try (ResultSet rs = stmt.executeQuery("SELECT provider, provider_id FROM users WHERE id = 'usr_google_1'")) {
                assertTrue(rs.next());
                assertEquals("google", rs.getString("provider"));
                assertEquals("g_sub_1001", rs.getString("provider_id"));
            }

            // Verify unique constraint: duplicate (provider, provider_id) must be rejected
            assertThrows(SQLException.class, () -> {
                stmt.executeUpdate("INSERT INTO users (id, name, email, password, phone, role, active, provider, provider_id) " +
                        "VALUES ('usr_google_2', 'Duplicate Google User', 'other@swiftdrop.lk', 'hashedpw', '0779998878', 'customer', true, 'google', 'g_sub_1001')");
            }, "Duplicate (provider, provider_id) should violate unique constraint");
        }
    }

    @Test
    @DisplayName("Scenario B: Pre-Flyway database with existing users successfully migrated to V3 and preserves data")
    void testPreFlywayDatabaseMigration() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:auth_legacy_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        // 1. Simulate legacy Hibernate schema without 'active' or 'provider' columns
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE users (" +
                    "id VARCHAR(64) PRIMARY KEY, " +
                    "name VARCHAR(255) NOT NULL, " +
                    "email VARCHAR(255) NOT NULL UNIQUE, " +
                    "password VARCHAR(255) NOT NULL, " +
                    "phone VARCHAR(32), " +
                    "role VARCHAR(32) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Seed legacy users before Flyway is introduced
            stmt.executeUpdate("INSERT INTO users (id, name, email, password, phone, role) " +
                    "VALUES ('usr_legacy_1', 'Alice Perera', 'alice@swiftdrop.lk', 'hash1', '0770000001', 'customer')");
            stmt.executeUpdate("INSERT INTO users (id, name, email, password, phone, role) " +
                    "VALUES ('usr_legacy_2', 'Bob Silva', 'bob@swiftdrop.lk', 'hash2', '0770000002', 'admin')");
        }

        // 2. Execute Flyway migration (baselineOnMigrate=true, baselineVersion=0)
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        var result = flyway.migrate();
        assertTrue(result.success, "Migration on existing database should succeed");

        // 3. Verify schema upgraded, active & provider columns added, and legacy records intact
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT id, name, email, role, active, provider, provider_id FROM users ORDER BY id ASC")) {
                // Verify user 1
                assertTrue(rs.next(), "First legacy user should exist");
                assertEquals("usr_legacy_1", rs.getString("id"));
                assertEquals("Alice Perera", rs.getString("name"));
                assertEquals("alice@swiftdrop.lk", rs.getString("email"));
                assertEquals("customer", rs.getString("role"));
                assertTrue(rs.getBoolean("active"), "Legacy user 1 should have active=true populated by V2");
                assertEquals("local", rs.getString("provider"), "Legacy user 1 should have provider='local' populated by V3");
                assertNull(rs.getString("provider_id"), "Legacy user 1 should have null provider_id");

                // Verify user 2
                assertTrue(rs.next(), "Second legacy user should exist");
                assertEquals("usr_legacy_2", rs.getString("id"));
                assertEquals("Bob Silva", rs.getString("name"));
                assertEquals("bob@swiftdrop.lk", rs.getString("email"));
                assertEquals("admin", rs.getString("role"));
                assertTrue(rs.getBoolean("active"), "Legacy user 2 should have active=true populated by V2");
                assertEquals("local", rs.getString("provider"), "Legacy user 2 should have provider='local' populated by V3");
                assertNull(rs.getString("provider_id"), "Legacy user 2 should have null provider_id");

                assertFalse(rs.next(), "No other users should exist");
            }

            // Verify new inserts work with default active = true, provider = 'local'
            stmt.executeUpdate("INSERT INTO users (id, name, email, password, phone, role) " +
                    "VALUES ('usr_legacy_3', 'Charlie Fernando', 'charlie@swiftdrop.lk', 'hash3', '0770000003', 'courier')");

            try (ResultSet rs = stmt.executeQuery("SELECT active, provider, provider_id FROM users WHERE id = 'usr_legacy_3'")) {
                assertTrue(rs.next());
                assertTrue(rs.getBoolean("active"), "New user in migrated DB should default to active=true");
                assertEquals("local", rs.getString("provider"), "New user in migrated DB should default to provider='local'");
                assertNull(rs.getString("provider_id"));
            }
        }
    }
}
