package com.sdpcheck.sdpcheck.domain.attendance;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class CheckInIdMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    @Test
    void assignsIdsToExistingRecordsWithoutChangingAttendanceAndKeepsTheCompositeKey() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("10").load().migrate();

        long memberId = jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('MEMBER', 'migration-member', 'test-hash', '기존 부원', 18) RETURNING id
                """, Long.class);
        UUID presentSession = UUID.randomUUID();
        UUID absentSession = UUID.randomUUID();
        UUID newSession = UUID.randomUUID();
        OffsetDateTime checkedInAt = OffsetDateTime.parse("2026-10-29T19:00:00+09:00");
        for (UUID id : new UUID[]{presentSession, absentSession, newSession}) {
            jdbc.update("""
                    INSERT INTO study_sessions (id, title, starts_at)
                    VALUES (?, '기존 세션', ?)
                    """, id, checkedInAt);
            jdbc.update("INSERT INTO session_attendance_targets (session_id, member_id) VALUES (?, ?)",
                    id, memberId);
        }
        jdbc.update("""
                INSERT INTO attendances (session_id, member_id, status, checked_in_at)
                VALUES (?, ?, 'PRESENT', ?), (?, ?, 'ABSENT', NULL)
                """, presentSession, memberId, checkedInAt, absentSession, memberId);

        var latest = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        latest.migrate();
        latest.validate();

        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM attendances", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(DISTINCT id) FROM attendances", Integer.class));
        long existingId = jdbc.queryForObject("SELECT id FROM attendances WHERE session_id = ?", Long.class, presentSession);
        assertTrue(existingId > 0);
        assertEquals("PRESENT", jdbc.queryForObject(
                "SELECT status FROM attendances WHERE session_id = ?", String.class, presentSession));
        assertEquals(checkedInAt.toInstant(), jdbc.queryForObject(
                "SELECT checked_in_at FROM attendances WHERE session_id = ?", OffsetDateTime.class, presentSession).toInstant());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM attendances WHERE status = 'ABSENT' AND checked_in_at IS NULL", Integer.class));

        long generatedId = jdbc.queryForObject("""
                INSERT INTO attendances (session_id, member_id, status, checked_in_at)
                VALUES (?, ?, 'LATE', ?) RETURNING id
                """, Long.class, newSession, memberId, checkedInAt);
        assertTrue(generatedId > 0);
        assertNotEquals(existingId, generatedId);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO attendances (session_id, member_id, status, checked_in_at)
                VALUES (?, ?, 'LATE', ?)
                """, presentSession, memberId, checkedInAt));
    }
}
