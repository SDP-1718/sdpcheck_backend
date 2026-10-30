package com.sdpcheck.sdpcheck.domain.statistics;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.StatisticsSummaryResponse;
import com.sdpcheck.sdpcheck.domain.statistics.service.StatisticsQueryService;
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
class StatisticsQueryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-10-08T10:00:00Z");

    @Autowired StatisticsQueryService service;
    @Autowired JdbcTemplate jdbc;

    private long memberA;
    private long memberB;
    private long adminId;
    private long unregisteredMemberId;
    private UUID semesterId;

    @BeforeEach
    void setUp() {
        memberA = member("statistics-a", Role.MEMBER);
        memberB = member("statistics-b", Role.MEMBER);
        adminId = member("statistics-admin", Role.ADMIN);
        unregisteredMemberId = member("statistics-unregistered", Role.MEMBER);
        semesterId = UUID.randomUUID();
        semester(semesterId, "2026-2H", true);
    }

    @Test
    void includesAdminsAndNoEligibleSessionMembersOnlyInTheRosterTotal() {
        roster(memberA);
        roster(memberB);
        roster(adminId);
        endedSession(uuid(1), semesterId);
        endedSession(uuid(2), semesterId);
        target(uuid(1), memberA);
        target(uuid(1), adminId);
        target(uuid(2), memberA);
        checkIn(uuid(1), memberA, "PRESENT");
        checkIn(uuid(1), adminId, "LATE");
        long attendanceBefore = jdbc.queryForObject("SELECT count(*) FROM attendances", Long.class);

        StatisticsSummaryResponse response = service.getAdminStatistics();

        assertEquals("2026-2H", response.periodCode());
        assertEquals(75, response.averageAttendanceRate());
        assertEquals(2L, response.totalSessionCount());
        assertEquals(1L, response.activeMemberCount());
        assertEquals(3L, response.totalMemberCount());
        assertEquals(attendanceBefore, jdbc.queryForObject("SELECT count(*) FROM attendances", Long.class));
    }

    @Test
    void usesTheArithmeticMeanOfIndividualRatesRatherThanTheWeightedOverallRate() {
        roster(memberA);
        roster(memberB);
        for (int suffix = 1; suffix <= 10; suffix++) {
            endedSession(uuid(suffix), semesterId);
            target(uuid(suffix), suffix == 1 ? memberA : memberB);
        }
        checkIn(uuid(1), memberA, "PRESENT");

        StatisticsSummaryResponse response = service.getAdminStatistics();

        assertEquals(50, response.averageAttendanceRate());
        assertEquals(10L, response.totalSessionCount());
        assertEquals(1L, response.activeMemberCount());
        assertEquals(2L, response.totalMemberCount());
    }

    @Test
    void roundsOnlyAfterAveragingRawIndividualRates() {
        roster(memberA);
        roster(memberB);
        for (int suffix = 1; suffix <= 13; suffix++) {
            endedSession(uuid(suffix), semesterId);
            target(uuid(suffix), suffix <= 6 ? memberA : memberB);
        }
        checkIn(uuid(1), memberA, "PRESENT");
        checkIn(uuid(7), memberB, "PRESENT");

        StatisticsSummaryResponse response = service.getAdminStatistics();

        assertEquals(15, response.averageAttendanceRate());
        assertEquals(13L, response.totalSessionCount());
        assertEquals(0L, response.activeMemberCount());
        assertEquals(2L, response.totalMemberCount());
    }

    @Test
    void evaluatesTheActiveThresholdFromRawRatesAtThe695And70PercentBoundary() {
        roster(memberA);
        roster(memberB);
        for (int suffix = 1; suffix <= 210; suffix++) {
            endedSession(uuid(suffix), semesterId);
            target(uuid(suffix), suffix <= 200 ? memberA : memberB);
            if (suffix <= 139 || suffix >= 201 && suffix <= 207) {
                checkIn(uuid(suffix), suffix <= 200 ? memberA : memberB, "PRESENT");
            }
        }

        StatisticsSummaryResponse response = service.getAdminStatistics();

        assertEquals(70, response.averageAttendanceRate());
        assertEquals(210L, response.totalSessionCount());
        assertEquals(1L, response.activeMemberCount());
        assertEquals(2L, response.totalMemberCount());
    }

    @Test
    void excludesUnregisteredTargetsAndDoesNotDuplicateGlobalSessionCountAcrossTargetJoins() {
        roster(memberA);
        roster(adminId);
        endedSession(uuid(1), semesterId);
        target(uuid(1), memberA);
        target(uuid(1), adminId);
        target(uuid(1), unregisteredMemberId);
        checkIn(uuid(1), memberA, "PRESENT");

        StatisticsSummaryResponse response = service.getAdminStatistics();

        assertEquals(50, response.averageAttendanceRate());
        assertEquals(1L, response.totalSessionCount());
        assertEquals(1L, response.activeMemberCount());
        assertEquals(2L, response.totalMemberCount());
    }

    @Test
    void excludesIneligibleSessionsButKeepsHiddenCompletedSessionsInStatistics() {
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
        for (int suffix = 2; suffix <= 7; suffix++) {
            target(uuid(suffix), memberA);
        }
        jdbc.update("""
                INSERT INTO events (id, title, starts_at, created_by)
                VALUES (?, '일반 일정', ?, ?)
                """, uuid(8), STARTS_AT, memberA);

        StatisticsSummaryResponse response = service.getAdminStatistics();

        assertEquals(100, response.averageAttendanceRate());
        assertEquals(1L, response.totalSessionCount());
        assertEquals(1L, response.activeMemberCount());
        assertEquals(1L, response.totalMemberCount());
    }

    @Test
    void completedSessionsWithoutTargetsStillCountGloballyButNotInMemberRates() {
        roster(memberA);
        endedSession(uuid(1), semesterId);

        StatisticsSummaryResponse response = service.getAdminStatistics();

        assertNull(response.averageAttendanceRate());
        assertEquals(1L, response.totalSessionCount());
        assertEquals(0L, response.activeMemberCount());
        assertEquals(1L, response.totalMemberCount());
    }

    @Test
    void emptyRosterCanStillHaveCompletedSessionsWithoutAverageOrActiveMembers() {
        endedSession(uuid(1), semesterId);
        endedSession(uuid(2), semesterId);
        target(uuid(2), unregisteredMemberId);

        StatisticsSummaryResponse response = service.getAdminStatistics();

        assertEquals("2026-2H", response.periodCode());
        assertNull(response.averageAttendanceRate());
        assertEquals(2L, response.totalSessionCount());
        assertEquals(0L, response.activeMemberCount());
        assertEquals(0L, response.totalMemberCount());
    }

    @Test
    void rosteredMembersWithoutFinishedEligibleSessionsRemainInTheTotalWithoutRates() {
        roster(memberA);
        roster(memberB);
        scheduledSession(uuid(1), semesterId);
        inProgressSession(uuid(2), semesterId);
        target(uuid(1), memberA);
        target(uuid(2), memberB);

        StatisticsSummaryResponse response = service.getAdminStatistics();

        assertNull(response.averageAttendanceRate());
        assertEquals(0L, response.totalSessionCount());
        assertEquals(0L, response.activeMemberCount());
        assertEquals(2L, response.totalMemberCount());
    }

    @Test
    void missingCurrentSemesterIsAnOperatingConfigurationError() {
        jdbc.update("UPDATE semesters SET is_current = false");

        BusinessException exception = assertThrows(BusinessException.class, service::getAdminStatistics);

        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, exception.getErrorCode());
    }

    @Test
    void currentSemesterWithoutAPeriodCodeIsAnOperatingConfigurationError() {
        jdbc.update("UPDATE semesters SET period_code = null WHERE id = ?", semesterId);

        BusinessException exception = assertThrows(BusinessException.class, service::getAdminStatistics);

        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, exception.getErrorCode());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serviceCanStartItsOwnReadOnlyRepeatableReadTransaction() {
        try {
            StatisticsSummaryResponse response = service.getAdminStatistics();
            assertEquals("2026-2H", response.periodCode());
            assertNull(response.averageAttendanceRate());
            assertEquals(0L, response.totalSessionCount());
            assertEquals(0L, response.activeMemberCount());
            assertEquals(0L, response.totalMemberCount());
        } finally {
            jdbc.update("DELETE FROM semester_members WHERE semester_id = ?", semesterId);
            jdbc.update("DELETE FROM semesters WHERE id = ?", semesterId);
            jdbc.update("DELETE FROM members WHERE id IN (?, ?, ?, ?)",
                    memberA, memberB, adminId, unregisteredMemberId);
        }
    }

    private long member(String loginId, Role role) {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES (?, ?, 'test-password-hash', '회원', 17)
                RETURNING id
                """, Long.class, role.name(), loginId);
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
