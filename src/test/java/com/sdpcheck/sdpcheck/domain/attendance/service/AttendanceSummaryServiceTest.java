package com.sdpcheck.sdpcheck.domain.attendance.service;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository.AttendanceCounts;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository.OperatingSemester;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AttendanceSummaryServiceTest {
    private static final long MEMBER_ID = 42L;
    private static final UUID SEMESTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PERIOD_CODE = "2026-2H";

    private final AttendanceQueryRepository repository = mock(AttendanceQueryRepository.class);
    private final AttendanceQueryService service = new AttendanceQueryService(repository);

    @ParameterizedTest
    @CsvSource({
            "12,7,1,4,67",
            "8,1,0,7,13",
            "8,0,1,7,13",
            "3,1,0,2,33",
            "3,1,1,1,67",
            "3,0,3,0,100",
            "3,3,0,0,100",
            "3,0,0,3,0",
            "2147483650,1073741825,0,1073741825,50",
            "10000000000000000,4949999999999999,0,5050000000000001,49"
    })
    void returnsCountsAndRoundsTheCombinedPresentAndLateRate(
            long total, long present, long late, long absent, int expectedRate) {
        stubSemester();
        when(repository.countAttendance(MEMBER_ID, SEMESTER_ID))
                .thenReturn(new AttendanceCounts(total, present, late, absent));

        var response = service.getMyAttendanceSummary(MEMBER_ID);

        assertEquals(PERIOD_CODE, response.periodCode());
        assertEquals(total, response.totalSessionCount());
        assertEquals(present, response.presentCount());
        assertEquals(late, response.lateCount());
        assertEquals(absent, response.absentCount());
        assertEquals(expectedRate, response.attendanceRate());
        assertEquals(response.totalSessionCount(),
                response.presentCount() + response.lateCount() + response.absentCount());
    }

    @Test
    void noEligibleSessionsReturnsThePeriodAndZeroCountsWithANullRate() {
        stubSemester();
        when(repository.countAttendance(MEMBER_ID, SEMESTER_ID))
                .thenReturn(new AttendanceCounts(0, 0, 0, 0));

        var response = service.getMyAttendanceSummary(MEMBER_ID);

        assertEquals(PERIOD_CODE, response.periodCode());
        assertNull(response.attendanceRate());
        assertEquals(0, response.totalSessionCount());
        assertEquals(0, response.presentCount());
        assertEquals(0, response.lateCount());
        assertEquals(0, response.absentCount());
    }

    @Test
    void usesOnlyTheAuthenticatedMemberAndTheConfiguredSemesterWithoutLoadingHistory() {
        stubSemester();
        when(repository.countAttendance(MEMBER_ID, SEMESTER_ID))
                .thenReturn(new AttendanceCounts(12, 7, 1, 4));

        service.getMyAttendanceSummary(MEMBER_ID);

        var order = inOrder(repository);
        order.verify(repository).findCurrentSemester();
        order.verify(repository).countAttendance(MEMBER_ID, SEMESTER_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void missingCurrentSemesterStopsBeforeAggregation() {
        when(repository.findCurrentSemester()).thenReturn(Optional.empty());

        assertMissingSemester();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingPeriodCodeIsAConfigurationErrorInsteadOfFabricatingACode(String periodCode) {
        when(repository.findCurrentSemester()).thenReturn(Optional.of(new OperatingSemester(SEMESTER_ID, periodCode)));

        assertMissingSemester();
    }

    private void assertMissingSemester() {
        var exception = assertThrows(BusinessException.class, () -> service.getMyAttendanceSummary(MEMBER_ID));

        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, exception.getErrorCode());
        verify(repository).findCurrentSemester();
        verifyNoMoreInteractions(repository);
    }

    private void stubSemester() {
        when(repository.findCurrentSemester()).thenReturn(Optional.of(new OperatingSemester(SEMESTER_ID, PERIOD_CODE)));
    }
}
