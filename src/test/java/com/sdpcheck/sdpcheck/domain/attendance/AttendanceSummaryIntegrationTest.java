package com.sdpcheck.sdpcheck.domain.attendance;

import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceSummaryResponse;
import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.service.AttendanceQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.config.import=")
@Testcontainers
@ActiveProfiles("test")
@Transactional
class AttendanceSummaryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-10-08T10:00:00Z");

    @Autowired AttendanceQueryService service;
    @Autowired JdbcTemplate jdbc;

    private long memberId;
    private long otherMemberId;
    private UUID semesterId;

    @BeforeEach
    void setUp() {
        memberId = member("summary-reader");
        otherMemberId = member("other-summary-reader");
        semesterId = UUID.randomUUID();
        semester(semesterId, "2026-2H", true);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serviceCanStartItsOwnReadOnlyRepeatableReadTransaction() {
        try {
            assertEmpty(service.getMyAttendanceSummary(memberId));
        } finally {
            jdbc.update("DELETE FROM semesters WHERE id = ?", semesterId);
            jdbc.update("DELETE FROM members WHERE id IN (?, ?)", memberId, otherMemberId);
        }
    }

    @Test
    void countsOnlyCurrentSemesterTargetsForTheRequestedMemberAndEligibleFinishedSessions() {
        UUID oldSemesterId = UUID.randomUUID();
        semester(oldSemesterId, "2026-1H", false);
        session(uuid(1), semesterId, STARTS_AT, true);
        session(uuid(2), semesterId, STARTS_AT.plusDays(1), true);
        session(uuid(3), semesterId, STARTS_AT.plusDays(2), true);
        session(uuid(4), oldSemesterId, STARTS_AT, true);
        session(uuid(5), semesterId, STARTS_AT, false);
        session(uuid(6), semesterId, STARTS_AT, false);
        session(uuid(7), semesterId, STARTS_AT, true);
        session(uuid(8), semesterId, STARTS_AT, true);
        session(uuid(9), semesterId, STARTS_AT, true);
        session(uuid(10), semesterId, STARTS_AT, true);
        session(uuid(11), null, STARTS_AT, true);

        for (int suffix = 1; suffix <= 9; suffix++) {
            target(uuid(suffix), memberId);
        }
        target(uuid(10), otherMemberId);
        target(uuid(11), memberId);
        checkIn(uuid(1), memberId, "PRESENT", STARTS_AT.plusMinutes(1));
        checkIn(uuid(2), memberId, "LATE", STARTS_AT.plusMinutes(10));
        checkIn(uuid(4), memberId, "PRESENT", STARTS_AT.plusMinutes(1));
        checkIn(uuid(10), otherMemberId, "PRESENT", STARTS_AT.plusMinutes(1));
        jdbc.update("UPDATE study_sessions SET started_at = ? WHERE id = ?", STARTS_AT, uuid(6));
        jdbc.update("UPDATE study_sessions SET cancelled_at = now() WHERE id = ?", uuid(7));
        jdbc.update("UPDATE study_sessions SET deleted_at = now() WHERE id = ?", uuid(8));
        jdbc.update("UPDATE study_sessions SET is_regular = false WHERE id = ?", uuid(9));
        jdbc.update("""
                INSERT INTO events (id, title, starts_at, created_by)
                VALUES (?, '일반 일정', ?, ?)
                """, UUID.randomUUID(), STARTS_AT, memberId);

        AttendanceSummaryResponse summary = service.getMyAttendanceSummary(memberId);

        assertEquals(3L, summary.totalSessionCount());
        assertEquals("2026-2H", summary.periodCode());
        assertEquals(1L, summary.presentCount());
        assertEquals(1L, summary.lateCount());
        assertEquals(1L, summary.absentCount());
        assertEquals(67, summary.attendanceRate());
        assertEquals(service.getMyAttendances(memberId, 0, 1).totalElements(), summary.totalSessionCount());
    }

    @Test
    void summarizesAllEligibleSessionsRatherThanOnlyTheFirstHistoryPage() {
        for (int suffix = 1; suffix <= 25; suffix++) {
            session(uuid(suffix), semesterId, STARTS_AT.plusDays(suffix), true);
            target(uuid(suffix), memberId);
            if (suffix <= 7) {
                checkIn(uuid(suffix), memberId, "PRESENT", STARTS_AT.plusDays(suffix).plusMinutes(1));
            } else if (suffix == 8) {
                checkIn(uuid(suffix), memberId, "LATE", STARTS_AT.plusDays(suffix).plusMinutes(10));
            }
        }

        var summary = service.getMyAttendanceSummary(memberId);

        assertEquals(25L, summary.totalSessionCount());
        assertEquals(7L, summary.presentCount());
        assertEquals(1L, summary.lateCount());
        assertEquals(17L, summary.absentCount());
        assertEquals(32, summary.attendanceRate());
        assertEquals(10, service.getMyAttendances(memberId, 0, 10).items().size());
    }

    @Test
    void anotherMembersCheckInDoesNotLeakIntoMySummary() {
        session(uuid(1), semesterId, STARTS_AT, true);
        target(uuid(1), memberId);
        target(uuid(1), otherMemberId);
        checkIn(uuid(1), otherMemberId, "LATE", STARTS_AT.plusMinutes(10));

        AttendanceSummaryResponse mine = service.getMyAttendanceSummary(memberId);
        AttendanceSummaryResponse theirs = service.getMyAttendanceSummary(otherMemberId);

        assertEquals(1L, mine.totalSessionCount());
        assertEquals("2026-2H", mine.periodCode());
        assertEquals(0L, mine.presentCount());
        assertEquals(0L, mine.lateCount());
        assertEquals(1L, mine.absentCount());
        assertEquals(0, mine.attendanceRate());
        assertEquals(1L, theirs.totalSessionCount());
        assertEquals("2026-2H", theirs.periodCode());
        assertEquals(0L, theirs.presentCount());
        assertEquals(1L, theirs.lateCount());
        assertEquals(0L, theirs.absentCount());
        assertEquals(100, theirs.attendanceRate());
    }

    @Test
    void completedSessionIsIncludedOnlyAfterActualEnd() {
        session(uuid(1), semesterId, STARTS_AT, false);
        target(uuid(1), memberId);
        jdbc.update("UPDATE study_sessions SET started_at = ? WHERE id = ?", STARTS_AT, uuid(1));
        checkIn(uuid(1), memberId, "PRESENT", STARTS_AT.plusMinutes(1));

        assertEmpty(service.getMyAttendanceSummary(memberId));

        jdbc.update("UPDATE study_sessions SET ended_at = ? WHERE id = ?", STARTS_AT.plusHours(1), uuid(1));

        AttendanceSummaryResponse summary = service.getMyAttendanceSummary(memberId);
        assertEquals(1L, summary.totalSessionCount());
        assertEquals("2026-2H", summary.periodCode());
        assertEquals(1L, summary.presentCount());
        assertEquals(0L, summary.lateCount());
        assertEquals(0L, summary.absentCount());
        assertEquals(100, summary.attendanceRate());
    }

    @Test
    void hidingACompletedSessionDoesNotEraseAttendanceSummary() {
        session(uuid(1), semesterId, STARTS_AT, true);
        target(uuid(1), memberId);
        jdbc.update("UPDATE study_sessions SET is_published = false WHERE id = ?", uuid(1));

        AttendanceSummaryResponse summary = service.getMyAttendanceSummary(memberId);

        assertEquals(1L, summary.totalSessionCount());
        assertEquals("2026-2H", summary.periodCode());
        assertEquals(0L, summary.presentCount());
        assertEquals(0L, summary.lateCount());
        assertEquals(1L, summary.absentCount());
        assertEquals(0, summary.attendanceRate());
    }

    @Test
    void emptySummaryHasZeroCountsAndNullRate() {
        assertEmpty(service.getMyAttendanceSummary(memberId));
    }

    @Test
    void absentSessionsAreDerivedWithoutChangingStoredAttendanceData() {
        session(uuid(1), semesterId, STARTS_AT, true);
        session(uuid(2), semesterId, STARTS_AT.plusDays(1), true);
        target(uuid(1), memberId);
        target(uuid(2), memberId);

        AttendanceSummaryResponse first = service.getMyAttendanceSummary(memberId);
        AttendanceSummaryResponse second = service.getMyAttendanceSummary(memberId);

        assertEquals(first, second);
        assertEquals(2L, first.totalSessionCount());
        assertEquals("2026-2H", first.periodCode());
        assertEquals(0L, first.presentCount());
        assertEquals(0L, first.lateCount());
        assertEquals(2L, first.absentCount());
        assertEquals(0, first.attendanceRate());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM attendances", Long.class));
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM session_attendance_targets", Long.class));
    }

    @Test
    void lateCheckInsCountAsAttendance() {
        session(uuid(1), semesterId, STARTS_AT, true);
        session(uuid(2), semesterId, STARTS_AT.plusDays(1), true);
        target(uuid(1), memberId);
        target(uuid(2), memberId);
        checkIn(uuid(1), memberId, "LATE", STARTS_AT.plusMinutes(10));
        checkIn(uuid(2), memberId, "LATE", STARTS_AT.plusDays(1).plusMinutes(10));

        AttendanceSummaryResponse summary = service.getMyAttendanceSummary(memberId);

        assertEquals(2L, summary.totalSessionCount());
        assertEquals("2026-2H", summary.periodCode());
        assertEquals(0L, summary.presentCount());
        assertEquals(2L, summary.lateCount());
        assertEquals(0L, summary.absentCount());
        assertEquals(100, summary.attendanceRate());
    }

    @Test
    void attendanceRateRoundsHalfUpToAnInteger() {
        for (int suffix = 1; suffix <= 8; suffix++) {
            session(uuid(suffix), semesterId, STARTS_AT.plusDays(suffix), true);
            target(uuid(suffix), memberId);
        }
        checkIn(uuid(1), memberId, "PRESENT", STARTS_AT.plusDays(1).plusMinutes(1));

        AttendanceSummaryResponse summary = service.getMyAttendanceSummary(memberId);

        assertEquals(8L, summary.totalSessionCount());
        assertEquals("2026-2H", summary.periodCode());
        assertEquals(1L, summary.presentCount());
        assertEquals(0L, summary.lateCount());
        assertEquals(7L, summary.absentCount());
        assertEquals(13, summary.attendanceRate());
    }

    @Test
    void switchingTheCurrentSemesterChangesTheSummaryScope() {
        UUID nextSemesterId = UUID.randomUUID();
        semester(nextSemesterId, "2027-1H", false);
        session(uuid(1), semesterId, STARTS_AT, true);
        session(uuid(2), nextSemesterId, STARTS_AT, true);
        target(uuid(1), memberId);
        target(uuid(2), memberId);
        checkIn(uuid(1), memberId, "PRESENT", STARTS_AT.plusMinutes(1));

        assertEquals(1L, service.getMyAttendanceSummary(memberId).presentCount());

        jdbc.update("UPDATE semesters SET is_current = false WHERE id = ?", semesterId);
        jdbc.update("UPDATE semesters SET is_current = true WHERE id = ?", nextSemesterId);

        AttendanceSummaryResponse summary = service.getMyAttendanceSummary(memberId);
        assertEquals(1L, summary.totalSessionCount());
        assertEquals("2027-1H", summary.periodCode());
        assertEquals(0L, summary.presentCount());
        assertEquals(0L, summary.lateCount());
        assertEquals(1L, summary.absentCount());
        assertEquals(0, summary.attendanceRate());
    }

    @Test
    void missingCurrentSemesterIsReportedAsAnOperatingConfigurationError() {
        jdbc.update("UPDATE semesters SET is_current = false");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getMyAttendanceSummary(memberId));

        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, exception.getErrorCode());
    }

    @Test
    void currentSemesterWithoutPeriodCodeIsReportedAsAnOperatingConfigurationError() {
        jdbc.update("UPDATE semesters SET period_code = null WHERE id = ?", semesterId);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getMyAttendanceSummary(memberId));

        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, exception.getErrorCode());
    }

    private void assertEmpty(AttendanceSummaryResponse summary) {
        assertEquals(0L, summary.totalSessionCount());
        assertEquals("2026-2H", summary.periodCode());
        assertEquals(0L, summary.presentCount());
        assertEquals(0L, summary.lateCount());
        assertEquals(0L, summary.absentCount());
        assertNull(summary.attendanceRate());
    }

    private long member(String loginId) {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('MEMBER', ?, 'test-password-hash', '회원', 17)
                RETURNING id
                """, Long.class, loginId);
    }

    private void semester(UUID id, String periodCode, boolean current) {
        jdbc.update("""
                INSERT INTO semesters (id, name, period_code, starts_on, ends_on, is_current)
                VALUES (?, '운영 학기', ?, DATE '2026-07-01', DATE '2026-12-31', ?)
                """, id, periodCode, current);
    }

    private void session(UUID id, UUID termId, OffsetDateTime startsAt, boolean ended) {
        jdbc.update("""
                INSERT INTO study_sessions (id, title, starts_at, semester_id, started_at, ended_at)
                VALUES (?, '정규 세션', ?, ?, ?, ?)
                """, id, startsAt, termId, ended ? startsAt.plusMinutes(5) : null,
                ended ? startsAt.plusHours(1) : null);
    }

    private void target(UUID sessionId, long targetMemberId) {
        jdbc.update("INSERT INTO session_attendance_targets (session_id, member_id) VALUES (?, ?)",
                sessionId, targetMemberId);
    }

    private void checkIn(UUID sessionId, long targetMemberId, String status, OffsetDateTime checkedInAt) {
        jdbc.update("INSERT INTO attendances (session_id, member_id, status, checked_in_at) VALUES (?, ?, ?, ?)",
                sessionId, targetMemberId, status, checkedInAt);
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }
}
