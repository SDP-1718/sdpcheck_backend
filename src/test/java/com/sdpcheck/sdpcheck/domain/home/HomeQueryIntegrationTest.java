package com.sdpcheck.sdpcheck.domain.home;

import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceSummaryResponse;
import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.service.AttendanceQueryService;
import com.sdpcheck.sdpcheck.domain.home.dto.response.HomeResponse;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeAttendanceStatus;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeCheckInStatus;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeSessionStatus;
import com.sdpcheck.sdpcheck.domain.home.service.HomeQueryService;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.schedule.enums.ScheduleType;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.config.import=")
@Testcontainers
@ActiveProfiles("test")
@Transactional
class HomeQueryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-09T10:00:00+09:00");

    @Autowired HomeQueryService service;
    @Autowired AttendanceQueryService attendanceService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean(name = "homeClock") Clock homeClock;

    private long memberId;
    private long otherMemberId;
    private UUID semesterId;

    @BeforeEach
    void setUp() {
        setServerTime(NOW);
        memberId = member("home-member", "홈 회원", Role.MEMBER);
        otherMemberId = member("other-home-member", "다른 회원", Role.MEMBER);
        semesterId = UUID.randomUUID();
        semester(semesterId, "2026-2H", true);
    }

    @Test
    void returnsMemberServerTimeAndAnEmptyCurrentSemesterSummary() {
        HomeResponse response = service.getHome(memberId);

        assertEquals(NOW, response.serverTime());
        assertEquals("홈 회원", response.member().name());
        assertEquals(17, response.member().generation());
        assertEquals(Role.MEMBER, response.member().role());
        assertNull(response.session());
        assertEquals("2026-2H", response.statistics().periodCode());
        assertNull(response.statistics().attendanceRate());
        assertEquals(0L, response.statistics().attendedSessionCount());
        assertEquals(0L, response.statistics().totalSessionCount());
        assertTrue(response.notices().isEmpty());
        assertTrue(response.upcomingSchedules().isEmpty());
    }

    @Test
    void missingPeriodCodeIsReportedAsTheOperatingSemesterConfigurationError() {
        jdbc.update("UPDATE semesters SET period_code = null WHERE id = ?", semesterId);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getHome(memberId));

        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, exception.getErrorCode());
    }

    @Test
    void statisticsUseTheExistingCurrentSemesterSummaryWithoutChangingItsValues() {
        endedSession(uuid(1), NOW.minusDays(3), NOW.minusDays(3).plusHours(1));
        endedSession(uuid(2), NOW.minusDays(2), NOW.minusDays(2).plusHours(1));
        endedSession(uuid(3), NOW.minusDays(1), NOW.minusDays(1).plusHours(1));
        target(uuid(1), memberId);
        target(uuid(2), memberId);
        target(uuid(3), memberId);
        checkIn(uuid(1), memberId, "PRESENT", NOW.minusDays(3).plusMinutes(1));
        checkIn(uuid(2), memberId, "LATE", NOW.minusDays(2).plusMinutes(10));

        AttendanceSummaryResponse summary = attendanceService.getMyAttendanceSummary(memberId);
        HomeResponse response = service.getHome(memberId);

        assertEquals(summary.periodCode(), response.statistics().periodCode());
        assertEquals(summary.attendanceRate(), response.statistics().attendanceRate());
        assertEquals(summary.presentCount() + summary.lateCount(), response.statistics().attendedSessionCount());
        assertEquals(summary.totalSessionCount(), response.statistics().totalSessionCount());
    }

    @Test
    void targetMembershipIsIsolatedAndReadingDoesNotCreateAnAbsentAttendanceRow() {
        UUID mine = uuid(1);
        UUID onlyTheirs = uuid(2);
        inProgressSession(mine, NOW.minusMinutes(10));
        inProgressSession(onlyTheirs, NOW.minusMinutes(1));
        target(mine, memberId);
        target(mine, otherMemberId);
        target(onlyTheirs, otherMemberId);
        checkIn(mine, otherMemberId, "PRESENT", NOW.minusMinutes(8));
        long before = jdbc.queryForObject("SELECT count(*) FROM attendances", Long.class);

        HomeResponse response = service.getHome(memberId);

        assertNotNull(response.session());
        assertEquals(mine, response.session().id());
        assertEquals(HomeAttendanceStatus.NOT_CHECKED_IN, response.session().attendanceStatus());
        assertEquals(HomeCheckInStatus.LATE_AVAILABLE, response.session().checkInStatus());
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM attendances", Long.class));
        AttendanceSummaryResponse summary = attendanceService.getMyAttendanceSummary(memberId);
        assertEquals(0L, summary.presentCount() + summary.lateCount());
    }

    @Test
    void noticesIgnorePinningAndUseLatestPublishedNonDeletedRowsWithIdTieBreak() {
        OffsetDateTime sameCreatedAt = NOW.minusHours(1);
        notice(uuid(1), "고정이지만 오래된 공지", NOW.minusDays(1), true, true, null);
        notice(uuid(2), "비공개 공지", NOW.minusMinutes(1), false, false, null);
        notice(uuid(3), "삭제 공지", NOW.minusMinutes(1), false, true, NOW.minusMinutes(1));
        notice(uuid(4), "동률 작은 ID", sameCreatedAt, false, true, null);
        notice(uuid(5), "동률 큰 ID", sameCreatedAt, false, true, null);
        notice(uuid(6), "가장 최신 공지", NOW.minusMinutes(30), false, true, null);

        HomeResponse response = service.getHome(memberId);

        assertEquals(List.of(uuid(6), uuid(5)), response.notices().stream()
                .map(HomeResponse.NoticePreview::id).toList());
        assertEquals(List.of("가장 최신 공지", "동률 큰 ID"), response.notices().stream()
                .map(HomeResponse.NoticePreview::title).toList());
    }

    @Test
    void upcomingSchedulesMergePublicSessionsAndGeneralSchedulesBeforeOrderingAndLimit() {
        scheduledSession(uuid(1), NOW.plusHours(1));
        event(uuid(2), "같은 시각 일반 일정", NOW.plusHours(1), true, null, null);
        event(uuid(3), "가장 이른 일반 일정", NOW.plusMinutes(30), true, null, null);
        scheduledSession(uuid(4), NOW.plusHours(2));

        HomeResponse response = service.getHome(memberId);

        assertEquals(List.of(uuid(3), uuid(1)), response.upcomingSchedules().stream()
                .map(schedule -> schedule.id()).toList());
        assertEquals(List.of(ScheduleType.GENERAL, ScheduleType.SESSION), response.upcomingSchedules().stream()
                .map(schedule -> schedule.type()).toList());
        assertEquals(NOW.plusMinutes(30), response.upcomingSchedules().getFirst().startsAt());
    }

    @Test
    void upcomingSchedulesExcludeExactNowAndAllUnavailableSessionAndGeneralRows() {
        scheduledSession(uuid(1), NOW.plusHours(1));
        scheduledSession(uuid(2), NOW);
        inProgressSession(uuid(3), NOW.minusMinutes(1));
        endedSession(uuid(4), NOW.minusHours(2), NOW.minusHours(1));
        scheduledSession(uuid(5), NOW.plusMinutes(2));
        jdbc.update("UPDATE study_sessions SET is_published = false WHERE id = ?", uuid(5));
        scheduledSession(uuid(6), NOW.plusMinutes(3));
        jdbc.update("UPDATE study_sessions SET cancelled_at = ? WHERE id = ?", NOW, uuid(6));
        scheduledSession(uuid(7), NOW.plusMinutes(4));
        jdbc.update("UPDATE study_sessions SET deleted_at = ? WHERE id = ?", NOW, uuid(7));
        event(uuid(8), "현재 일반 일정", NOW, true, null, null);
        event(uuid(9), "비공개 일반 일정", NOW.plusMinutes(5), false, null, null);
        event(uuid(10), "취소 일반 일정", NOW.plusMinutes(6), true, NOW, null);
        event(uuid(11), "삭제 일반 일정", NOW.plusMinutes(7), true, null, NOW);

        HomeResponse response = service.getHome(memberId);

        assertEquals(List.of(uuid(1)), response.upcomingSchedules().stream()
                .map(schedule -> schedule.id()).toList());
    }

    @Test
    void ongoingSessionHasPriorityAndUsesTheLatestActualStartTime() {
        endedSession(uuid(1), NOW.minusHours(2), NOW.minusMinutes(10));
        inProgressSession(uuid(2), NOW.minusMinutes(30));
        inProgressSession(uuid(3), NOW.minusMinutes(1));
        scheduledSession(uuid(4), NOW.plusMinutes(1));
        target(uuid(1), memberId);
        target(uuid(2), memberId);
        target(uuid(3), memberId);
        target(uuid(4), memberId);

        HomeResponse response = service.getHome(memberId);

        assertNotNull(response.session());
        assertEquals(uuid(3), response.session().id());
        assertEquals(HomeSessionStatus.IN_PROGRESS, response.session().status());
    }

    @Test
    void equallyRecentOngoingSessionsUseAscendingIdAsTheFinalTieBreak() {
        inProgressSession(uuid(2), NOW.minusMinutes(1));
        inProgressSession(uuid(1), NOW.minusMinutes(1));
        target(uuid(1), memberId);
        target(uuid(2), memberId);

        HomeResponse response = service.getHome(memberId);

        assertEquals(uuid(1), response.session().id());
    }

    @Test
    void latestEndedTodaySessionHasPriorityOverFutureScheduledSessions() {
        endedSession(uuid(1), NOW.minusHours(4), NOW.minusHours(3));
        endedSession(uuid(2), NOW.minusHours(2), NOW.minusMinutes(15));
        scheduledSession(uuid(3), NOW.plusMinutes(1));
        target(uuid(1), memberId);
        target(uuid(2), memberId);
        target(uuid(3), memberId);

        HomeResponse response = service.getHome(memberId);

        assertNotNull(response.session());
        assertEquals(uuid(2), response.session().id());
        assertEquals(HomeSessionStatus.ENDED, response.session().status());
        assertEquals(HomeAttendanceStatus.ABSENT, response.session().attendanceStatus());
        assertEquals(HomeCheckInStatus.UNAVAILABLE, response.session().checkInStatus());
        assertNull(response.session().checkedInAt());
    }

    @Test
    void endedSessionIsVisibleOnlyUntilTheNextKoreanMidnight() {
        OffsetDateTime lateNight = OffsetDateTime.parse("2026-10-09T23:59:59+09:00");
        endedSession(uuid(1), lateNight.minusHours(2), lateNight.minusHours(1));
        target(uuid(1), memberId);
        setServerTime(lateNight);

        assertEquals(uuid(1), service.getHome(memberId).session().id());

        setServerTime(OffsetDateTime.parse("2026-10-10T00:00:00+09:00"));
        assertNull(service.getHome(memberId).session());
    }

    @Test
    void scheduledSessionUsesScheduledStartThenAscendingIdAndIncludesTheExactCurrentTime() {
        OffsetDateTime utcEquivalentOfNow = OffsetDateTime.parse("2026-10-09T01:00:00Z");
        scheduledSession(uuid(2), utcEquivalentOfNow);
        scheduledSession(uuid(1), utcEquivalentOfNow);
        scheduledSession(uuid(3), NOW.plusMinutes(1));
        target(uuid(1), memberId);
        target(uuid(2), memberId);
        target(uuid(3), memberId);

        HomeResponse response = service.getHome(memberId);

        assertNotNull(response.session());
        assertEquals(uuid(1), response.session().id());
        assertEquals(NOW, response.session().scheduledStartAt());
        assertEquals(HomeSessionStatus.SCHEDULED, response.session().status());
        assertEquals(HomeAttendanceStatus.NOT_CHECKED_IN, response.session().attendanceStatus());
        assertEquals(HomeCheckInStatus.UNAVAILABLE, response.session().checkInStatus());
    }

    @Test
    void sessionSelectionExcludesPrivateCancelledDeletedNonRegularAndNonTargetSessions() {
        scheduledSession(uuid(1), NOW.plusMinutes(1));
        scheduledSession(uuid(2), NOW.plusMinutes(2));
        scheduledSession(uuid(3), NOW.plusMinutes(3));
        scheduledSession(uuid(4), NOW.plusMinutes(4));
        scheduledSession(uuid(5), NOW.plusMinutes(5));
        scheduledSession(uuid(6), NOW.plusMinutes(6));
        inProgressSession(uuid(7), NOW.plusMinutes(7));
        target(uuid(1), otherMemberId);
        target(uuid(2), memberId);
        target(uuid(3), memberId);
        target(uuid(4), memberId);
        target(uuid(5), memberId);
        target(uuid(6), memberId);
        target(uuid(7), memberId);
        jdbc.update("UPDATE study_sessions SET is_published = false WHERE id = ?", uuid(2));
        jdbc.update("UPDATE study_sessions SET cancelled_at = ? WHERE id = ?", NOW, uuid(3));
        jdbc.update("UPDATE study_sessions SET deleted_at = ? WHERE id = ?", NOW, uuid(4));
        jdbc.update("UPDATE study_sessions SET is_regular = false WHERE id = ?", uuid(5));

        HomeResponse response = service.getHome(memberId);

        assertNotNull(response.session());
        assertEquals(uuid(6), response.session().id());
    }

    @Test
    void anUncheckedInProgressSessionIsAvailableAtExactlyFiveMinutesAndLateAfterward() {
        inProgressSession(uuid(1), NOW.minusMinutes(5));
        target(uuid(1), memberId);

        HomeResponse atBoundary = service.getHome(memberId);
        assertEquals(HomeAttendanceStatus.NOT_CHECKED_IN, atBoundary.session().attendanceStatus());
        assertEquals(HomeCheckInStatus.AVAILABLE, atBoundary.session().checkInStatus());

        setServerTime(NOW.plusNanos(1));
        HomeResponse afterBoundary = service.getHome(memberId);
        assertEquals(HomeCheckInStatus.LATE_AVAILABLE, afterBoundary.session().checkInStatus());
    }

    @Test
    void confirmedPresentAndLateAttendanceAreReturnedWithoutMakingCheckInAvailable() {
        inProgressSession(uuid(1), NOW.minusMinutes(2));
        target(uuid(1), memberId);
        OffsetDateTime checkedInAt = NOW.minusMinutes(1);
        checkIn(uuid(1), memberId, "PRESENT", checkedInAt);

        HomeResponse present = service.getHome(memberId);

        assertEquals(HomeAttendanceStatus.PRESENT, present.session().attendanceStatus());
        assertEquals(HomeCheckInStatus.UNAVAILABLE, present.session().checkInStatus());
        assertEquals(checkedInAt, present.session().checkedInAt());

        jdbc.update("UPDATE attendances SET status = 'LATE' WHERE session_id = ? AND member_id = ?",
                uuid(1), memberId);
        HomeResponse late = service.getHome(memberId);

        assertEquals(HomeAttendanceStatus.LATE, late.session().attendanceStatus());
        assertEquals(HomeCheckInStatus.UNAVAILABLE, late.session().checkInStatus());
        assertEquals(checkedInAt, late.session().checkedInAt());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serviceCanStartItsOwnReadOnlyRepeatableReadTransaction() {
        try {
            HomeResponse response = service.getHome(memberId);
            assertEquals("홈 회원", response.member().name());
            assertEquals("2026-2H", response.statistics().periodCode());
            assertNull(response.session());
        } finally {
            jdbc.update("DELETE FROM semesters WHERE id = ?", semesterId);
            jdbc.update("DELETE FROM members WHERE id IN (?, ?)", memberId, otherMemberId);
        }
    }

    private void setServerTime(OffsetDateTime serverTime) {
        when(homeClock.instant()).thenReturn(serverTime.toInstant());
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

    private void scheduledSession(UUID id, OffsetDateTime startsAt) {
        studySession(id, "예정 세션", startsAt, null, null);
    }

    private void inProgressSession(UUID id, OffsetDateTime startedAt) {
        studySession(id, "진행 세션", startedAt.minusMinutes(10), startedAt, null);
    }

    private void endedSession(UUID id, OffsetDateTime startedAt, OffsetDateTime endedAt) {
        studySession(id, "종료 세션", startedAt.minusMinutes(10), startedAt, endedAt);
    }

    private void studySession(UUID id, String title, OffsetDateTime startsAt,
                              OffsetDateTime startedAt, OffsetDateTime endedAt) {
        jdbc.update("""
                INSERT INTO study_sessions (
                    id, title, starts_at, location, semester_id, started_at, ended_at,
                    is_published, is_regular, cancelled_at, deleted_at
                ) VALUES (?, ?, ?, NULL, ?, ?, ?, true, true, NULL, NULL)
                """, id, title, startsAt, semesterId, startedAt, endedAt);
    }

    private void target(UUID sessionId, long targetMemberId) {
        jdbc.update("INSERT INTO session_attendance_targets (session_id, member_id) VALUES (?, ?)",
                sessionId, targetMemberId);
    }

    private void checkIn(UUID sessionId, long targetMemberId, String status, OffsetDateTime checkedInAt) {
        jdbc.update("INSERT INTO attendances (session_id, member_id, status, checked_in_at) VALUES (?, ?, ?, ?)",
                sessionId, targetMemberId, status, checkedInAt);
    }

    private void notice(UUID id, String title, OffsetDateTime createdAt, boolean pinned,
                        boolean published, OffsetDateTime deletedAt) {
        jdbc.update("""
                INSERT INTO notices (
                    id, title, content, is_pinned, author_id, created_at, updated_at, is_published, deleted_at
                ) VALUES (?, ?, '공지 본문', ?, ?, ?, ?, ?, ?)
                """, id, title, pinned, memberId, createdAt, createdAt, published, deletedAt);
    }

    private void event(UUID id, String title, OffsetDateTime startsAt, boolean published,
                       OffsetDateTime cancelledAt, OffsetDateTime deletedAt) {
        jdbc.update("""
                INSERT INTO events (
                    id, title, starts_at, location, created_by, is_published, cancelled_at, deleted_at
                ) VALUES (?, ?, ?, NULL, ?, ?, ?, ?)
                """, id, title, startsAt, memberId, published, cancelledAt, deletedAt);
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }
}
