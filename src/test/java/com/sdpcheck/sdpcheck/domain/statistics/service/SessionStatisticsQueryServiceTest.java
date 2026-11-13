package com.sdpcheck.sdpcheck.domain.statistics.service;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository.OperatingSemester;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.SessionAttendanceStatisticsItem;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.SessionAttendanceStatisticsResponse;
import com.sdpcheck.sdpcheck.domain.statistics.repository.SessionStatisticsQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionStatisticsQueryServiceTest {
    private static final UUID SEMESTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PERIOD_CODE = "2026-2H";
    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-10-08T19:00:00+09:00");

    private final SessionStatisticsQueryRepository repository = mock(SessionStatisticsQueryRepository.class);
    private final AttendanceQueryRepository attendanceRepository = mock(AttendanceQueryRepository.class);
    private final SessionStatisticsQueryService service = new SessionStatisticsQueryService(repository, attendanceRepository);

    @Test
    void queriesTheConfiguredSemesterAndPreservesSessionOrderAndNullableRate() {
        stubSemester(PERIOD_CODE);
        var attended = new SessionAttendanceStatisticsItem(SESSION_ID, "정규 세션", STARTS_AT, 3, 1, 1, 1, 67);
        var withoutTargets = new SessionAttendanceStatisticsItem(
                UUID.randomUUID(), "대상 없는 세션", STARTS_AT.plusDays(7), 0, 0, 0, 0, null);
        when(repository.findSessionStatistics(SEMESTER_ID)).thenReturn(List.of(attended, withoutTargets));

        SessionAttendanceStatisticsResponse response = service.getSessionAttendanceStatistics();

        assertEquals(PERIOD_CODE, response.periodCode());
        assertEquals(List.of(attended, withoutTargets), response.sessions());
        assertNull(response.sessions().get(1).attendanceRate());
        verify(repository).findSessionStatistics(SEMESTER_ID);
    }

    @Test
    void returnsAnEmptyListWhenTheOperatingSemesterHasNoEligibleSessions() {
        stubSemester(PERIOD_CODE);
        when(repository.findSessionStatistics(SEMESTER_ID)).thenReturn(List.of());

        SessionAttendanceStatisticsResponse response = service.getSessionAttendanceStatistics();

        assertEquals(PERIOD_CODE, response.periodCode());
        assertEquals(List.of(), response.sessions());
        verify(repository).findSessionStatistics(SEMESTER_ID);
    }

    @Test
    void missingOperatingSemesterStopsBeforeQueryingSessionStatistics() {
        when(attendanceRepository.findCurrentSemester()).thenReturn(Optional.empty());

        assertMissingSemester();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingPeriodCodeIsAnOperatingConfigurationError(String periodCode) {
        stubSemester(periodCode);

        assertMissingSemester();
    }

    @Test
    void repositoryFailureIsNotReplacedWithAnEmptySuccessfulResponse() {
        stubSemester(PERIOD_CODE);
        var failure = new IllegalStateException("storage failure");
        when(repository.findSessionStatistics(SEMESTER_ID)).thenThrow(failure);

        IllegalStateException actual = assertThrows(IllegalStateException.class, service::getSessionAttendanceStatistics);

        assertSame(failure, actual);
    }

    private void assertMissingSemester() {
        BusinessException exception = assertThrows(BusinessException.class, service::getSessionAttendanceStatistics);

        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, exception.getErrorCode());
        verifyNoInteractions(repository);
    }

    private void stubSemester(String periodCode) {
        when(attendanceRepository.findCurrentSemester())
                .thenReturn(Optional.of(new OperatingSemester(SEMESTER_ID, periodCode)));
    }
}
