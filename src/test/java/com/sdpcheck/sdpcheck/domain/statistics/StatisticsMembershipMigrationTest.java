package com.sdpcheck.sdpcheck.domain.statistics;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class StatisticsMembershipMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    @Test
    void preservesV12DataWithoutBackfillingTargetsAndEnforcesSemesterMembershipConstraints() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("12").load().migrate();

        long legacyMemberId = member(jdbc, "legacy-target-member");
        long anotherMemberId = member(jdbc, "another-member");
        UUID currentSemesterId = UUID.randomUUID();
        UUID otherSemesterId = UUID.randomUUID();
        semester(jdbc, currentSemesterId, "2026-2H", true);
        semester(jdbc, otherSemesterId, "2027-1H", false);
        UUID legacySessionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO study_sessions (id, title, starts_at, semester_id, started_at, ended_at)
                VALUES (?, '기존 종료 세션', '2026-10-08T10:00:00Z'::timestamptz, ?,
                        '2026-10-08T10:05:00Z'::timestamptz, '2026-10-08T11:00:00Z'::timestamptz)
                """, legacySessionId, currentSemesterId);
        jdbc.update("INSERT INTO session_attendance_targets (session_id, member_id) VALUES (?, ?)",
                legacySessionId, legacyMemberId);

        Flyway latest = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        latest.migrate();
        latest.validate();

        assertEquals("2026-2H", jdbc.queryForObject(
                "SELECT period_code FROM semesters WHERE id = ?", String.class, currentSemesterId));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM session_attendance_targets", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM semester_members", Long.class));

        jdbc.update("INSERT INTO semester_members (semester_id, member_id) VALUES (?, ?)",
                currentSemesterId, legacyMemberId);
        assertNotNull(jdbc.queryForObject("""
                SELECT created_at FROM semester_members WHERE semester_id = ? AND member_id = ?
                """, Object.class, currentSemesterId, legacyMemberId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO semester_members (semester_id, member_id) VALUES (?, ?)",
                currentSemesterId, legacyMemberId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO semester_members (semester_id, member_id) VALUES (?, ?)",
                UUID.randomUUID(), legacyMemberId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO semester_members (semester_id, member_id) VALUES (?, ?)",
                currentSemesterId, Long.MAX_VALUE));

        jdbc.update("INSERT INTO semester_members (semester_id, member_id) VALUES (?, ?)",
                otherSemesterId, legacyMemberId);
        jdbc.update("INSERT INTO semester_members (semester_id, member_id) VALUES (?, ?)",
                currentSemesterId, anotherMemberId);
        assertEquals(3L, jdbc.queryForObject("SELECT count(*) FROM semester_members", Long.class));
        assertEquals(2L, jdbc.queryForObject(
                "SELECT count(*) FROM semester_members WHERE member_id = ?", Long.class, legacyMemberId));
        assertEquals(2L, jdbc.queryForObject(
                "SELECT count(*) FROM semester_members WHERE semester_id = ?", Long.class, currentSemesterId));
    }

    private static long member(JdbcTemplate jdbc, String loginId) {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('MEMBER', ?, 'test-password-hash', '회원', 17)
                RETURNING id
                """, Long.class, loginId);
    }

    private static void semester(JdbcTemplate jdbc, UUID id, String periodCode, boolean current) {
        jdbc.update("""
                INSERT INTO semesters (id, name, period_code, starts_on, ends_on, is_current)
                VALUES (?, '운영 학기', ?, DATE '2026-07-01', DATE '2026-12-31', ?)
                """, id, periodCode, current);
    }
}
