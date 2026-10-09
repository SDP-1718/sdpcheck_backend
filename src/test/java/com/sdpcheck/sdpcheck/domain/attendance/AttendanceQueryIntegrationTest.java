package com.sdpcheck.sdpcheck.domain.attendance;

import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceHistoryItem;
import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.service.AttendanceQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.config.import=")
@Testcontainers
@ActiveProfiles("test")
@Transactional
class AttendanceQueryIntegrationTest {
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
        memberId = member("history-reader");
        otherMemberId = member("other-reader");
        semesterId = UUID.randomUUID();
        semester(semesterId, true);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serviceCanStartItsOwnReadOnlyRepeatableReadTransaction() {
        try {
            assertTrue(service.getMyAttendances(memberId, 0, 10).items().isEmpty());
        } finally {
            jdbc.update("DELETE FROM semesters WHERE id = ?", semesterId);
            jdbc.update("DELETE FROM members WHERE id IN (?, ?)", memberId, otherMemberId);
        }
    }

    @Test
    void noTargetSessionsReturnsAnEmptyPage() {
        session(uuid(1), semesterId, STARTS_AT, true);

        var page = service.getMyAttendances(memberId, 0, 10);

        assertTrue(page.items().isEmpty());
        assertEquals(0, page.totalElements());
        assertEquals(0, page.totalPages());
        assertFalse(page.hasNext());
    }

    @Test
    void absentIsDerivedWithoutInsertingOrUpdatingAttendanceRecords() {
        session(uuid(1), semesterId, STARTS_AT, true);
        target(uuid(1), memberId);

        var first = service.getMyAttendances(memberId, 0, 10);
        var second = service.getMyAttendances(memberId, 0, 10);

        assertEquals(first, second);
        assertEquals(1, first.totalElements());
        assertEquals(AttendanceStatus.ABSENT, first.items().getFirst().attendanceStatus());
        assertNull(first.items().getFirst().checkedInAt());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM attendances", Long.class));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM session_attendance_targets", Long.class));
    }

    @Test
    void ordersByScheduledStartThenSessionIdDescendingBeforePagination() {
        session(uuid(1), semesterId, STARTS_AT.minusDays(1), true);
        session(uuid(2), semesterId, STARTS_AT, true);
        session(uuid(3), semesterId, STARTS_AT, true);
        for (UUID id : List.of(uuid(1), uuid(2), uuid(3))) {
            target(id, memberId);
        }
        checkIn(uuid(1), memberId, "PRESENT", STARTS_AT.minusDays(1).plusMinutes(6));
        checkIn(uuid(2), memberId, "LATE", STARTS_AT.plusMinutes(25));
        jdbc.update("UPDATE study_sessions SET ended_at = ? WHERE id = ?", STARTS_AT.plusDays(2), uuid(1));

        var first = service.getMyAttendances(memberId, 0, 2);
        var second = service.getMyAttendances(memberId, 1, 2);

        assertEquals(List.of(uuid(3), uuid(2)), first.items().stream().map(AttendanceHistoryItem::sessionId).toList());
        assertEquals(List.of(uuid(1)), second.items().stream().map(AttendanceHistoryItem::sessionId).toList());
        assertEquals(List.of(AttendanceStatus.ABSENT, AttendanceStatus.LATE),
                first.items().stream().map(AttendanceHistoryItem::attendanceStatus).toList());
        assertEquals(AttendanceStatus.PRESENT, second.items().getFirst().attendanceStatus());
        assertEquals(3, first.totalElements());
        assertEquals(2, first.totalPages());
        assertTrue(first.hasNext());
        assertFalse(second.hasNext());
    }

    @Test
    void excludesOtherTermsUnassignedUnfinishedCancelledDeletedAndNonRegularSessions() {
        UUID oldSemester = UUID.randomUUID();
        semester(oldSemester, false);
        session(uuid(1), semesterId, STARTS_AT, true);
        session(uuid(2), oldSemester, STARTS_AT, true);
        session(uuid(3), semesterId, STARTS_AT, true);
        session(uuid(4), semesterId, STARTS_AT.minusDays(30), false);
        session(uuid(5), semesterId, STARTS_AT.minusDays(30), false);
        session(uuid(6), semesterId, STARTS_AT, true);
        session(uuid(7), semesterId, STARTS_AT, true);
        session(uuid(8), semesterId, STARTS_AT, true);
        session(uuid(9), null, STARTS_AT, true);
        for (int suffix : List.of(1, 2, 4, 5, 6, 7, 8, 9)) {
            target(uuid(suffix), memberId);
        }
        target(uuid(3), otherMemberId);
        checkIn(uuid(3), otherMemberId, "PRESENT", STARTS_AT.plusMinutes(6));
        jdbc.update("UPDATE study_sessions SET started_at = ? WHERE id = ?", STARTS_AT.minusDays(30), uuid(5));
        checkIn(uuid(5), memberId, "PRESENT", STARTS_AT.minusDays(30).plusMinutes(1));
        jdbc.update("UPDATE study_sessions SET cancelled_at = now() WHERE id = ?", uuid(6));
        jdbc.update("UPDATE study_sessions SET deleted_at = now() WHERE id = ?", uuid(7));
        jdbc.update("UPDATE study_sessions SET is_regular = false WHERE id = ?", uuid(8));
        jdbc.update("""
                INSERT INTO events (id, title, starts_at, created_by)
                VALUES (?, '일반 일정', ?, ?)
                """, UUID.randomUUID(), STARTS_AT, memberId);

        var page = service.getMyAttendances(memberId, 0, 10);

        assertEquals(List.of(uuid(1)), page.items().stream().map(AttendanceHistoryItem::sessionId).toList());
        assertEquals(1, page.totalElements());
    }

    @Test
    void anotherMembersCheckInCannotAppearInMyHistory() {
        session(uuid(1), semesterId, STARTS_AT, true);
        target(uuid(1), memberId);
        target(uuid(1), otherMemberId);
        checkIn(uuid(1), otherMemberId, "LATE", STARTS_AT.plusMinutes(25));

        var mine = service.getMyAttendances(memberId, 0, 10).items().getFirst();
        var theirs = service.getMyAttendances(otherMemberId, 0, 10).items().getFirst();

        assertEquals(AttendanceStatus.ABSENT, mine.attendanceStatus());
        assertNull(mine.checkedInAt());
        assertEquals(AttendanceStatus.LATE, theirs.attendanceStatus());
        assertEquals(STARTS_AT.plusMinutes(25).toInstant(), theirs.checkedInAt().toInstant());
    }

    @Test
    void checkInAppearsOnlyAfterTheSessionIsExplicitlyEnded() {
        session(uuid(1), semesterId, STARTS_AT, false);
        target(uuid(1), memberId);
        jdbc.update("UPDATE study_sessions SET started_at = ? WHERE id = ?", STARTS_AT, uuid(1));
        checkIn(uuid(1), memberId, "PRESENT", STARTS_AT.plusMinutes(1));
        assertTrue(service.getMyAttendances(memberId, 0, 10).items().isEmpty());

        jdbc.update("UPDATE study_sessions SET ended_at = ? WHERE id = ?", STARTS_AT.plusHours(1), uuid(1));

        assertEquals(AttendanceStatus.PRESENT,
                service.getMyAttendances(memberId, 0, 10).items().getFirst().attendanceStatus());
    }

    @Test
    void hidingACompletedScheduleDoesNotEraseAttendanceHistory() {
        session(uuid(1), semesterId, STARTS_AT, true);
        target(uuid(1), memberId);
        jdbc.update("UPDATE study_sessions SET is_published = false WHERE id = ?", uuid(1));

        assertEquals(1, service.getMyAttendances(memberId, 0, 10).totalElements());
    }

    @Test
    void timestampsUseSeoulIncludingTheDateBoundary() {
        var midnight = OffsetDateTime.parse("2026-09-30T15:00:00Z");
        session(uuid(1), semesterId, midnight, true);
        target(uuid(1), memberId);
        checkIn(uuid(1), memberId, "PRESENT", midnight.plusMinutes(6));

        var item = service.getMyAttendances(memberId, 0, 10).items().getFirst();

        assertEquals("정규 세션", item.sessionTitle());
        assertEquals(OffsetDateTime.parse("2026-10-01T00:00:00+09:00"), item.startsAt());
        assertEquals(OffsetDateTime.parse("2026-10-01T00:06:00+09:00"), item.checkedInAt());
    }

    @Test
    void outOfRangeAndMaximumPagePreserveTotalsWithoutOffsetOverflow() {
        session(uuid(1), semesterId, STARTS_AT, true);
        target(uuid(1), memberId);

        for (int pageNumber : List.of(1, Integer.MAX_VALUE)) {
            var page = service.getMyAttendances(memberId, pageNumber, 100);
            assertTrue(page.items().isEmpty());
            assertEquals(pageNumber, page.page());
            assertEquals(1, page.totalElements());
            assertEquals(1, page.totalPages());
            assertFalse(page.hasNext());
        }
    }

    @Test
    void missingCurrentSemesterIsNotReportedAsEmptyHistory() {
        jdbc.update("UPDATE semesters SET is_current = false");

        var exception = assertThrows(BusinessException.class, () -> service.getMyAttendances(memberId, 0, 10));

        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, exception.getErrorCode());
    }

    @Test
    void switchingTheCurrentSemesterChangesTheHistoryScope() {
        UUID nextSemester = UUID.randomUUID();
        semester(nextSemester, false);
        session(uuid(1), semesterId, STARTS_AT, true);
        session(uuid(2), nextSemester, STARTS_AT, true);
        target(uuid(1), memberId);
        target(uuid(2), memberId);
        assertEquals(uuid(1), service.getMyAttendances(memberId, 0, 10).items().getFirst().sessionId());
        jdbc.update("UPDATE semesters SET is_current = false WHERE id = ?", semesterId);
        jdbc.update("UPDATE semesters SET is_current = true WHERE id = ?", nextSemester);

        assertEquals(uuid(2), service.getMyAttendances(memberId, 0, 10).items().getFirst().sessionId());
    }

    @Test
    void databaseAllowsOnlyOneCurrentSemester() {
        assertThrows(DataIntegrityViolationException.class, () -> semester(UUID.randomUUID(), true));
    }

    @Test
    void databaseRejectsAnInvalidSemesterDateRange() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO semesters (id, name, starts_on, ends_on)
                VALUES (?, '잘못된 기간', DATE '2026-12-31', DATE '2026-07-01')
                """, UUID.randomUUID()));
    }

    @Test
    void databaseRejectsAnEndWithoutAnActualStart() {
        session(uuid(1), semesterId, STARTS_AT, false);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE study_sessions SET ended_at = ? WHERE id = ?", STARTS_AT.plusHours(1), uuid(1)));
    }

    @Test
    void databaseRejectsAnEndBeforeTheActualStart() {
        session(uuid(1), semesterId, STARTS_AT, true);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE study_sessions SET ended_at = ? WHERE id = ?", STARTS_AT, uuid(1)));
    }

    @Test
    void databaseRejectsDuplicateTargets() {
        session(uuid(1), semesterId, STARTS_AT, true);
        target(uuid(1), memberId);

        assertThrows(DataIntegrityViolationException.class, () -> target(uuid(1), memberId));
    }

    @Test
    void databaseRejectsAttendanceForANonTargetMember() {
        session(uuid(1), semesterId, STARTS_AT, true);
        target(uuid(1), otherMemberId);

        assertThrows(DataIntegrityViolationException.class,
                () -> checkIn(uuid(1), memberId, "PRESENT", STARTS_AT.plusMinutes(6)));
    }

    @Test
    void databaseRejectsDuplicateAttendanceForTheSameMemberAndSession() {
        session(uuid(1), semesterId, STARTS_AT, true);
        target(uuid(1), memberId);
        checkIn(uuid(1), memberId, "PRESENT", STARTS_AT.plusMinutes(6));

        assertThrows(DataIntegrityViolationException.class,
                () -> checkIn(uuid(1), memberId, "LATE", STARTS_AT.plusMinutes(25)));
    }

    @Test
    void databaseRejectsAnAbsentCheckInRecord() {
        session(uuid(1), semesterId, STARTS_AT, true);
        target(uuid(1), memberId);

        assertThrows(DataIntegrityViolationException.class,
                () -> checkIn(uuid(1), memberId, "ABSENT", STARTS_AT.plusMinutes(6)));
    }

    @Test
    void databaseRejectsACheckInWithoutATimestamp() {
        session(uuid(1), semesterId, STARTS_AT, true);
        target(uuid(1), memberId);

        assertThrows(DataIntegrityViolationException.class, () -> checkIn(uuid(1), memberId, "PRESENT", null));
    }

    private long member(String loginId) {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('MEMBER', ?, 'test-password-hash', '회원', 17)
                RETURNING id
                """, Long.class, loginId);
    }

    private void semester(UUID id, boolean current) {
        jdbc.update("""
                INSERT INTO semesters (id, name, starts_on, ends_on, is_current)
                VALUES (?, '운영 학기', DATE '2026-07-01', DATE '2026-12-31', ?)
                """, id, current);
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
