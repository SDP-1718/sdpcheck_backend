package com.sdpcheck.sdpcheck.domain.attendance;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers
class AttendanceMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    @Test
    void migratesLegacySchemaWithoutChangingLegacyRowsOrCreatingAttendanceData() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(dataSource);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("5")
                .load()
                .migrate();

        long memberId = jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('ADMIN', 'legacy-admin', 'legacy-password-hash', '기존 관리자', 17)
                RETURNING id
                """, Long.class);
        UUID noticeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID eventId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID sessionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        jdbc.update("""
                INSERT INTO notices (id, title, content, is_pinned, author_id)
                VALUES (?, '기존 공지', '기존 공지 본문', false, ?)
                """, noticeId, memberId);
        jdbc.update("""
                INSERT INTO events (id, title, starts_at, location, created_by)
                VALUES (?, '기존 일반 일정', '2026-10-09T10:00:00Z'::timestamptz, '학생회관', ?)
                """, eventId, memberId);
        jdbc.update("""
                INSERT INTO study_sessions (id, title, starts_at, location)
                VALUES (?, '기존 정기 세션', '2026-10-10T10:00:00Z'::timestamptz, '세미나실')
                """, sessionId);

        Flyway latest = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        latest.migrate();
        latest.validate();

        assertEquals("legacy-admin", jdbc.queryForObject(
                "SELECT login_id FROM members WHERE id = ?", String.class, memberId));
        assertEquals("기존 공지", jdbc.queryForObject(
                "SELECT title FROM notices WHERE id = ?", String.class, noticeId));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT is_published FROM notices WHERE id = ?", Boolean.class, noticeId));
        assertEquals("기존 일반 일정", jdbc.queryForObject(
                "SELECT title FROM events WHERE id = ?", String.class, eventId));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT is_published FROM events WHERE id = ?", Boolean.class, eventId));
        assertNull(jdbc.queryForObject(
                "SELECT cancelled_at FROM events WHERE id = ?", Object.class, eventId));

        assertEquals("기존 정기 세션", jdbc.queryForObject(
                "SELECT title FROM study_sessions WHERE id = ?", String.class, sessionId));
        assertNull(jdbc.queryForObject(
                "SELECT semester_id FROM study_sessions WHERE id = ?", Object.class, sessionId));
        assertNull(jdbc.queryForObject(
                "SELECT started_at FROM study_sessions WHERE id = ?", Object.class, sessionId));
        assertNull(jdbc.queryForObject(
                "SELECT ended_at FROM study_sessions WHERE id = ?", Object.class, sessionId));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT is_regular FROM study_sessions WHERE id = ?", Boolean.class, sessionId));
        assertNull(jdbc.queryForObject(
                "SELECT cancelled_at FROM study_sessions WHERE id = ?", Object.class, sessionId));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT is_published FROM study_sessions WHERE id = ?", Boolean.class, sessionId));

        assertEquals("EVENT", jdbc.queryForObject(
                "SELECT type FROM schedule_items WHERE id = ?", String.class, eventId));
        assertEquals("SESSION", jdbc.queryForObject(
                "SELECT type FROM schedule_items WHERE id = ?", String.class, sessionId));
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM schedule_items", Long.class));

        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM semesters", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM session_attendance_targets", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM attendances", Long.class));
    }
}
