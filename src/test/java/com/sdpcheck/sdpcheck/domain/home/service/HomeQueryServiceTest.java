package com.sdpcheck.sdpcheck.domain.home.service;

import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceSummaryResponse;
import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.service.AttendanceQueryService;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.home.dto.response.HomeResponse;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeAttendanceStatus;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeCheckInStatus;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeSessionStatus;
import com.sdpcheck.sdpcheck.domain.home.repository.HomeQueryRepository;
import com.sdpcheck.sdpcheck.domain.home.repository.HomeQueryRepository.SessionCandidate;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleSummary;
import com.sdpcheck.sdpcheck.domain.schedule.enums.ScheduleType;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class HomeQueryServiceTest {
    private static final long MEMBER_ID = 1L;
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NOTICE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SCHEDULE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final OffsetDateTime SERVER_TIME = OffsetDateTime.parse("2026-10-09T19:00:00+09:00");
    private static final HomeResponse.MemberInfo MEMBER = new HomeResponse.MemberInfo("상우", 17, Role.MEMBER);
    private static final AttendanceSummaryResponse SUMMARY = new AttendanceSummaryResponse(
            "2026-2H", 67, 7, 1, 4, 12);
    private static final List<HomeResponse.NoticePreview> NOTICES = List.of(
            new HomeResponse.NoticePreview(NOTICE_ID, "최신 공지", SERVER_TIME.minusDays(1)));
    private static final List<ScheduleSummary> UPCOMING_SCHEDULES = List.of(
            new ScheduleSummary(SCHEDULE_ID, ScheduleType.SESSION, "다음 세션", SERVER_TIME.plusDays(1), "강의실"));

    private final HomeQueryRepository repository = mock(HomeQueryRepository.class);
    private final AttendanceQueryService attendanceService = mock(AttendanceQueryService.class);
    private final Clock clock = mock(Clock.class);
    private final HomeQueryService service = new HomeQueryService(repository, attendanceService, clock);

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(SERVER_TIME.toInstant());
        when(repository.findMember(MEMBER_ID)).thenReturn(Optional.of(MEMBER));
        when(attendanceService.getMyAttendanceSummary(MEMBER_ID)).thenReturn(SUMMARY);
        when(repository.findSession(MEMBER_ID, SERVER_TIME)).thenReturn(Optional.empty());
        when(repository.findLatestNotices()).thenReturn(NOTICES);
        when(repository.findUpcomingSchedules(SERVER_TIME)).thenReturn(UPCOMING_SCHEDULES);
    }

    @Test
    void returnsTheCompleteHomeResponseFromOneSharedClockInstant() {
        HomeResponse response = service.getHome(MEMBER_ID);

        assertEquals(SERVER_TIME, response.serverTime());
        assertEquals(MEMBER, response.member());
        assertNull(response.session());
        assertEquals(new HomeResponse.Statistics("2026-2H", 67, 8, 12), response.statistics());
        assertSame(NOTICES, response.notices());
        assertSame(UPCOMING_SCHEDULES, response.upcomingSchedules());
        verify(clock, times(1)).instant();
        var order = inOrder(repository, attendanceService);
        order.verify(repository).findMember(MEMBER_ID);
        order.verify(attendanceService).getMyAttendanceSummary(MEMBER_ID);
        order.verify(repository).findSession(MEMBER_ID, SERVER_TIME);
        order.verify(repository).findLatestNotices();
        order.verify(repository).findUpcomingSchedules(SERVER_TIME);
        verifyNoMoreInteractions(repository, attendanceService);
    }

    @Test
    void scheduledSessionCannotBeCheckedIn() {
        when(repository.findSession(MEMBER_ID, SERVER_TIME)).thenReturn(Optional.of(candidate(
                SERVER_TIME.plusMinutes(30), null, null, null, null)));

        HomeResponse.SessionInfo session = service.getHome(MEMBER_ID).session();

        assertEquals(HomeSessionStatus.SCHEDULED, session.status());
        assertEquals(HomeAttendanceStatus.NOT_CHECKED_IN, session.attendanceStatus());
        assertEquals(HomeCheckInStatus.UNAVAILABLE, session.checkInStatus());
        assertNull(session.checkedInAt());
    }

    @Test
    void actualStartDeterminesTheNormalCheckInWindowNotTheScheduledStart() {
        when(repository.findSession(MEMBER_ID, SERVER_TIME)).thenReturn(Optional.of(candidate(
                SERVER_TIME.minusHours(2), SERVER_TIME.minusMinutes(4), null, null, null)));

        HomeResponse.SessionInfo session = service.getHome(MEMBER_ID).session();

        assertEquals(HomeSessionStatus.IN_PROGRESS, session.status());
        assertEquals(HomeAttendanceStatus.NOT_CHECKED_IN, session.attendanceStatus());
        assertEquals(HomeCheckInStatus.AVAILABLE, session.checkInStatus());
        assertEquals(SERVER_TIME.minusHours(2), session.scheduledStartAt());
    }

    @Test
    void exactlyFiveMinutesAfterActualStartIsStillNormallyAvailable() {
        when(repository.findSession(MEMBER_ID, SERVER_TIME)).thenReturn(Optional.of(candidate(
                SERVER_TIME.minusHours(1), SERVER_TIME.minusMinutes(5), null, null, null)));

        HomeResponse.SessionInfo session = service.getHome(MEMBER_ID).session();

        assertEquals(HomeAttendanceStatus.NOT_CHECKED_IN, session.attendanceStatus());
        assertEquals(HomeCheckInStatus.AVAILABLE, session.checkInStatus());
    }

    @Test
    void afterFiveMinutesWithoutARecordRemainsNotCheckedInAndBecomesLateAvailable() {
        when(repository.findSession(MEMBER_ID, SERVER_TIME)).thenReturn(Optional.of(candidate(
                SERVER_TIME.minusHours(1), SERVER_TIME.minusMinutes(5).minusNanos(1), null, null, null)));

        HomeResponse.SessionInfo session = service.getHome(MEMBER_ID).session();

        assertEquals(HomeSessionStatus.IN_PROGRESS, session.status());
        assertEquals(HomeAttendanceStatus.NOT_CHECKED_IN, session.attendanceStatus());
        assertEquals(HomeCheckInStatus.LATE_AVAILABLE, session.checkInStatus());
    }

    @Test
    void presentAndLateRecordsArePreservedAndCannotBeCheckedInAgain() {
        OffsetDateTime checkedInAt = SERVER_TIME.minusMinutes(2);
        for (AttendanceStatus status : List.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE)) {
            when(repository.findSession(MEMBER_ID, SERVER_TIME)).thenReturn(Optional.of(candidate(
                    SERVER_TIME.minusHours(1), SERVER_TIME.minusMinutes(1), null, status, checkedInAt)));

            HomeResponse.SessionInfo session = service.getHome(MEMBER_ID).session();

            assertEquals(HomeAttendanceStatus.valueOf(status.name()), session.attendanceStatus());
            assertEquals(HomeCheckInStatus.UNAVAILABLE, session.checkInStatus());
            assertEquals(checkedInAt, session.checkedInAt());
        }
    }

    @Test
    void completedSessionWithoutARecordIsAbsentAndCannotBeCheckedIn() {
        when(repository.findSession(MEMBER_ID, SERVER_TIME)).thenReturn(Optional.of(candidate(
                SERVER_TIME.minusHours(2), SERVER_TIME.minusHours(1), SERVER_TIME.minusMinutes(1), null, null)));

        HomeResponse.SessionInfo session = service.getHome(MEMBER_ID).session();

        assertEquals(HomeSessionStatus.ENDED, session.status());
        assertEquals(HomeAttendanceStatus.ABSENT, session.attendanceStatus());
        assertEquals(HomeCheckInStatus.UNAVAILABLE, session.checkInStatus());
        assertNull(session.checkedInAt());
    }

    @Test
    void missingMemberStopsBeforeSummaryAndOtherQueries() {
        when(repository.findMember(MEMBER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.getHome(MEMBER_ID));

        assertEquals(AuthErrorCode.AUTHENTICATION_REQUIRED, exception.getErrorCode());
        verify(repository).findMember(MEMBER_ID);
        verify(repository, never()).findSession(MEMBER_ID, SERVER_TIME);
        verify(repository, never()).findLatestNotices();
        verify(repository, never()).findUpcomingSchedules(SERVER_TIME);
        verifyNoInteractions(attendanceService);
    }

    @Test
    void operatingSemesterConfigurationErrorIsPropagatedBeforeHomeQueries() {
        when(attendanceService.getMyAttendanceSummary(MEMBER_ID))
                .thenThrow(new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.getHome(MEMBER_ID));

        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, exception.getErrorCode());
        verify(repository).findMember(MEMBER_ID);
        verify(repository, never()).findSession(MEMBER_ID, SERVER_TIME);
        verify(repository, never()).findLatestNotices();
        verify(repository, never()).findUpcomingSchedules(SERVER_TIME);
    }

    @Test
    void repositoryFailureIsNotConvertedIntoAFalseSuccessfulHomeResponse() {
        IllegalStateException failure = new IllegalStateException("storage failure");
        when(repository.findLatestNotices()).thenThrow(failure);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.getHome(MEMBER_ID));

        assertSame(failure, exception);
        verify(repository).findMember(MEMBER_ID);
        verify(attendanceService).getMyAttendanceSummary(MEMBER_ID);
        verify(repository).findSession(MEMBER_ID, SERVER_TIME);
        verify(repository).findLatestNotices();
        verify(repository, never()).findUpcomingSchedules(SERVER_TIME);
    }

    private SessionCandidate candidate(
            OffsetDateTime scheduledStartAt,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            AttendanceStatus attendanceStatus,
            OffsetDateTime checkedInAt
    ) {
        return new SessionCandidate(SESSION_ID, "정규 세션", scheduledStartAt, startedAt, endedAt,
                attendanceStatus, checkedInAt);
    }
}
