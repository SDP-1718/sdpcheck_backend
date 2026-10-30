package com.sdpcheck.sdpcheck.domain.statistics;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.MemberAttendanceStatisticsItem;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.MemberAttendanceStatisticsResponse;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.StatisticsSummaryResponse;
import com.sdpcheck.sdpcheck.domain.statistics.enums.MemberStatisticsSort;
import com.sdpcheck.sdpcheck.domain.statistics.service.MemberStatisticsQueryService;
import com.sdpcheck.sdpcheck.domain.statistics.service.StatisticsQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
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
class MemberStatisticsQueryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-10-08T10:00:00Z");

    @Autowired MemberStatisticsQueryService service;
    @Autowired StatisticsQueryService summaryService;
    @Autowired JdbcTemplate jdbc;

    private long memberA;
    private long memberB;
    private long adminId;
    private long unregisteredMemberId;
    private UUID semesterId;

    @BeforeEach
    void setUp() {
        memberA = member("member-statistics-a", "zeta", Role.MEMBER);
        memberB = member("member-statistics-b", "alpha", Role.MEMBER);
        adminId = member("member-statistics-admin", "middle", Role.ADMIN);
        unregisteredMemberId = member("member-statistics-outside", "outside", Role.MEMBER);
        semesterId = UUID.randomUUID();
        semester(semesterId, "2026-2H", true);
    }

    @Test
    void returnsRosterMembersIncludingAdminsWithPresentLateAbsentCountsAndSummaryParity() {
        roster(memberA);
        roster(memberB);
        roster(adminId);
        endedSession(uuid(1), semesterId);
        endedSession(uuid(2), semesterId);
        target(uuid(1), memberA);
        target(uuid(1), adminId);
        target(uuid(1), unregisteredMemberId);
        target(uuid(2), memberA);
        checkIn(uuid(1), memberA, "PRESENT");
        checkIn(uuid(1), adminId, "LATE");
        long attendanceBefore = jdbc.queryForObject("SELECT count(*) FROM attendances", Long.class);

        MemberAttendanceStatisticsResponse response = service.getMemberAttendanceStatistics(
                0, 20, MemberStatisticsSort.ATTENDANCE_RATE_DESC);
        MemberAttendanceStatisticsItem member = item(response, memberA);
        MemberAttendanceStatisticsItem admin = item(response, adminId);
        MemberAttendanceStatisticsItem idle = item(response, memberB);
        StatisticsSummaryResponse summary = summaryService.getAdminStatistics();

        assertEquals("2026-2H", response.periodCode());
        assertEquals(0, response.page());
        assertEquals(20, response.size());
        assertFalse(response.hasNext());
        assertEquals("zeta", member.name());
        assertEquals(17, member.generation());
        assertEquals(1L, member.presentCount());
        assertEquals(0L, member.lateCount());
        assertEquals(1L, member.absentCount());
        assertEquals(2L, member.totalSessionCount());
        assertEquals(50, member.attendanceRate());
        assertEquals(Boolean.TRUE, member.isAtRisk());
        assertEquals(0L, admin.presentCount());
        assertEquals(1L, admin.lateCount());
        assertEquals(0L, admin.absentCount());
        assertEquals(1L, admin.totalSessionCount());
        assertEquals(100, admin.attendanceRate());
        assertEquals(Boolean.FALSE, admin.isAtRisk());
        assertZeroTargetItem(idle);
        assertEquals(3L, response.members().size());
        assertEquals(summary.totalMemberCount(), response.members().size());
        assertEquals(summary.activeMemberCount(), response.members().stream()
                .filter(item -> Boolean.FALSE.equals(item.isAtRisk())).count());
        assertEquals(75, summary.averageAttendanceRate());
        assertEquals(attendanceBefore, jdbc.queryForObject("SELECT count(*) FROM attendances", Long.class));
    }

    @Test
    void rateSortUsesRaw695And70PercentValuesBeforeRoundedDisplayAndKeepsNullLast() {
        roster(memberA);
        roster(memberB);
        roster(adminId);
        for (int suffix = 1; suffix <= 210; suffix++) {
            endedSession(uuid(suffix), semesterId);
            target(uuid(suffix), suffix <= 200 ? memberA : memberB);
            if (suffix <= 139 || suffix >= 201 && suffix <= 207) {
                checkIn(uuid(suffix), suffix <= 200 ? memberA : memberB, "PRESENT");
            }
        }

        MemberAttendanceStatisticsResponse descending = service.getMemberAttendanceStatistics(
                0, 20, MemberStatisticsSort.ATTENDANCE_RATE_DESC);
        MemberAttendanceStatisticsResponse ascending = service.getMemberAttendanceStatistics(
                0, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC);

        assertEquals(List.of(memberB, memberA, adminId), ids(descending));
        assertEquals(List.of(memberA, memberB, adminId), ids(ascending));
        assertEquals(70, item(descending, memberA).attendanceRate());
        assertEquals(Boolean.TRUE, item(descending, memberA).isAtRisk());
        assertEquals(70, item(descending, memberB).attendanceRate());
        assertEquals(Boolean.FALSE, item(descending, memberB).isAtRisk());
        assertZeroTargetItem(item(descending, adminId));
    }

    @Test
    void usesNameAndIdForEqualRawRateOrderingAcrossSizePlusOnePages() {
        long sameNameFirst = member("same-name-first", "same", Role.MEMBER);
        long sameNameSecond = member("same-name-second", "same", Role.MEMBER);
        roster(sameNameFirst);
        roster(sameNameSecond);
        for (int suffix = 1; suffix <= 6; suffix++) {
            endedSession(uuid(suffix), semesterId);
            target(uuid(suffix), suffix <= 2 ? sameNameFirst : sameNameSecond);
        }
        checkIn(uuid(1), sameNameFirst, "PRESENT");
        checkIn(uuid(3), sameNameSecond, "LATE");
        checkIn(uuid(4), sameNameSecond, "PRESENT");

        MemberAttendanceStatisticsResponse first = service.getMemberAttendanceStatistics(
                0, 1, MemberStatisticsSort.ATTENDANCE_RATE_DESC);
        MemberAttendanceStatisticsResponse second = service.getMemberAttendanceStatistics(
                1, 1, MemberStatisticsSort.ATTENDANCE_RATE_DESC);
        MemberAttendanceStatisticsResponse beyond = service.getMemberAttendanceStatistics(
                2, 1, MemberStatisticsSort.ATTENDANCE_RATE_DESC);
        MemberAttendanceStatisticsResponse maximumPage = service.getMemberAttendanceStatistics(
                Integer.MAX_VALUE, 50, MemberStatisticsSort.ATTENDANCE_RATE_DESC);

        assertEquals(List.of(sameNameFirst), ids(first));
        assertTrue(first.hasNext());
        assertEquals(List.of(sameNameSecond), ids(second));
        assertFalse(second.hasNext());
        assertTrue(beyond.members().isEmpty());
        assertFalse(beyond.hasNext());
        assertTrue(maximumPage.members().isEmpty());
        assertFalse(maximumPage.hasNext());
    }

    @Test
    void nameSortIgnoresRateAndNullRateStatusButStillUsesIdAsTieBreak() {
        roster(memberA);
        roster(memberB);
        roster(adminId);
        endedSession(uuid(1), semesterId);
        endedSession(uuid(2), semesterId);
        target(uuid(1), memberA);
        target(uuid(2), adminId);
        checkIn(uuid(1), memberA, "PRESENT");

        MemberAttendanceStatisticsResponse response = service.getMemberAttendanceStatistics(
                0, 20, MemberStatisticsSort.NAME_ASC);

        assertEquals(List.of(memberB, adminId, memberA), ids(response));
        assertNull(item(response, memberB).attendanceRate());
        assertEquals(0, item(response, adminId).attendanceRate());
        assertEquals(100, item(response, memberA).attendanceRate());
    }

    @Test
    void excludesOtherTermUnregisteredAndIneligibleTargetsWhileKeepingPrivateCompletedSessions() {
        UUID otherSemesterId = UUID.randomUUID();
        semester(otherSemesterId, "2026-1H", false);
        roster(memberA);
        endedSession(uuid(1), semesterId);
        target(uuid(1), memberA);
        checkIn(uuid(1), memberA, "PRESENT");
        jdbc.update("UPDATE study_sessions SET is_published = false WHERE id = ?", uuid(1));

        scheduledSession(uuid(2), semesterId);
        inProgressSession(uuid(3), semesterId);
        endedSession(uuid(4), semesterId);
        jdbc.update("UPDATE study_sessions SET cancelled_at = ? WHERE id = ?", STARTS_AT, uuid(4));
        endedSession(uuid(5), semesterId);
        jdbc.update("UPDATE study_sessions SET deleted_at = ? WHERE id = ?", STARTS_AT, uuid(5));
        endedSession(uuid(6), semesterId);
        jdbc.update("UPDATE study_sessions SET is_regular = false WHERE id = ?", uuid(6));
        endedSession(uuid(7), otherSemesterId);
        endedSession(uuid(8), semesterId);
        for (int suffix = 2; suffix <= 7; suffix++) {
            target(uuid(suffix), memberA);
        }
        target(uuid(8), unregisteredMemberId);

        MemberAttendanceStatisticsResponse response = service.getMemberAttendanceStatistics(
                0, 20, MemberStatisticsSort.ATTENDANCE_RATE_DESC);
        MemberAttendanceStatisticsItem member = item(response, memberA);

        assertEquals(1L, member.presentCount());
        assertEquals(0L, member.lateCount());
        assertEquals(0L, member.absentCount());
        assertEquals(1L, member.totalSessionCount());
        assertEquals(100, member.attendanceRate());
        assertEquals(Boolean.FALSE, member.isAtRisk());
        assertEquals(List.of(memberA), ids(response));
    }

    @Test
    void zeroTargetRosterMembersAreReturnedWithNullRateAndRiskAndNoRowsAreWritten() {
        roster(memberA);
        roster(memberB);
        endedSession(uuid(1), semesterId);
        target(uuid(1), memberA);
        long attendanceBefore = jdbc.queryForObject("SELECT count(*) FROM attendances", Long.class);

        MemberAttendanceStatisticsResponse first = service.getMemberAttendanceStatistics(
                0, 20, MemberStatisticsSort.ATTENDANCE_RATE_DESC);
        MemberAttendanceStatisticsResponse second = service.getMemberAttendanceStatistics(
                0, 20, MemberStatisticsSort.ATTENDANCE_RATE_DESC);

        assertEquals(first, second);
        assertEquals(0, item(first, memberA).attendanceRate());
        assertEquals(Boolean.TRUE, item(first, memberA).isAtRisk());
        assertZeroTargetItem(item(first, memberB));
        assertEquals(attendanceBefore, jdbc.queryForObject("SELECT count(*) FROM attendances", Long.class));
    }

    @Test
    void validatesPageAndSizeAndSupportsTheMaximumAllowedSize() {
        MemberAttendanceStatisticsResponse maximum = service.getMemberAttendanceStatistics(
                0, 50, MemberStatisticsSort.NAME_ASC);

        assertEquals(50, maximum.size());
        for (int[] invalid : new int[][] {{-1, 20}, {0, 0}, {0, 51}}) {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.getMemberAttendanceStatistics(
                            invalid[0], invalid[1], MemberStatisticsSort.NAME_ASC));
            assertEquals(CommonErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        }
        BusinessException nullSort = assertThrows(BusinessException.class,
                () -> service.getMemberAttendanceStatistics(0, 20, null));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, nullSort.getErrorCode());
    }

    @Test
    void missingCurrentSemesterOrPeriodCodeIsAnOperatingConfigurationError() {
        jdbc.update("UPDATE semesters SET is_current = false WHERE id = ?", semesterId);

        BusinessException missingCurrent = assertThrows(BusinessException.class,
                () -> service.getMemberAttendanceStatistics(0, 20, MemberStatisticsSort.NAME_ASC));
        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, missingCurrent.getErrorCode());

        jdbc.update("UPDATE semesters SET is_current = true, period_code = null WHERE id = ?", semesterId);
        BusinessException missingPeriod = assertThrows(BusinessException.class,
                () -> service.getMemberAttendanceStatistics(0, 20, MemberStatisticsSort.NAME_ASC));
        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, missingPeriod.getErrorCode());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serviceCanStartItsOwnReadOnlyRepeatableReadTransaction() {
        try {
            MemberAttendanceStatisticsResponse response = service.getMemberAttendanceStatistics(
                    0, 20, MemberStatisticsSort.NAME_ASC);
            assertEquals("2026-2H", response.periodCode());
            assertTrue(response.members().isEmpty());
            assertFalse(response.hasNext());
        } finally {
            jdbc.update("DELETE FROM semester_members WHERE semester_id = ?", semesterId);
            jdbc.update("DELETE FROM semesters WHERE id = ?", semesterId);
            jdbc.update("DELETE FROM members WHERE id IN (?, ?, ?, ?)",
                    memberA, memberB, adminId, unregisteredMemberId);
        }
    }

    private MemberAttendanceStatisticsItem item(MemberAttendanceStatisticsResponse response, long memberId) {
        return response.members().stream().filter(item -> item.memberId() == memberId).findFirst().orElseThrow();
    }

    private void assertZeroTargetItem(MemberAttendanceStatisticsItem item) {
        assertEquals(0L, item.presentCount());
        assertEquals(0L, item.lateCount());
        assertEquals(0L, item.absentCount());
        assertEquals(0L, item.totalSessionCount());
        assertNull(item.attendanceRate());
        assertNull(item.isAtRisk());
    }

    private List<Long> ids(MemberAttendanceStatisticsResponse response) {
        return response.members().stream().map(MemberAttendanceStatisticsItem::memberId).toList();
    }

    private long member(String loginId, String name, Role role) {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES (?, ?, 'test-password-hash', ?, 17)
                RETURNING id
                """, Long.class, role.name(), loginId, name);
    }

    private void semester(UUID id, String periodCode, boolean current) {
        jdbc.update("""
                INSERT INTO semesters (id, name, period_code, starts_on, ends_on, is_current)
                VALUES (?, '운영 학기', ?, DATE '2026-07-01', DATE '2026-12-31', ?)
                """, id, periodCode, current);
    }

    private void roster(long memberId) {
        jdbc.update("INSERT INTO semester_members (semester_id, member_id) VALUES (?, ?)", semesterId, memberId);
    }

    private void endedSession(UUID id, UUID termId) {
        session(id, termId, STARTS_AT, STARTS_AT.plusMinutes(5), STARTS_AT.plusHours(1));
    }

    private void scheduledSession(UUID id, UUID termId) {
        session(id, termId, STARTS_AT, null, null);
    }

    private void inProgressSession(UUID id, UUID termId) {
        session(id, termId, STARTS_AT, STARTS_AT.plusMinutes(5), null);
    }

    private void session(UUID id, UUID termId, OffsetDateTime startsAt,
                         OffsetDateTime startedAt, OffsetDateTime endedAt) {
        jdbc.update("""
                INSERT INTO study_sessions (id, title, starts_at, semester_id, started_at, ended_at)
                VALUES (?, '정규 세션', ?, ?, ?, ?)
                """, id, startsAt, termId, startedAt, endedAt);
    }

    private void target(UUID sessionId, long memberId) {
        jdbc.update("INSERT INTO session_attendance_targets (session_id, member_id) VALUES (?, ?)",
                sessionId, memberId);
    }

    private void checkIn(UUID sessionId, long memberId, String status) {
        jdbc.update("""
                INSERT INTO attendances (session_id, member_id, status, checked_in_at)
                VALUES (?, ?, ?, ?)
                """, sessionId, memberId, status, STARTS_AT.plusMinutes(10));
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }
}
