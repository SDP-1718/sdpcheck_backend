package com.sdpcheck.sdpcheck.domain.statistics.service;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository.OperatingSemester;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.StatisticsSummaryResponse;
import com.sdpcheck.sdpcheck.domain.statistics.repository.StatisticsQueryRepository;
import com.sdpcheck.sdpcheck.domain.statistics.repository.StatisticsQueryRepository.StatisticsAggregate;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class StatisticsQueryServiceTest {
    private static final UUID SEMESTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PERIOD_CODE = "2026-2H";

    private final StatisticsQueryRepository repository = mock(StatisticsQueryRepository.class);
    private final AttendanceQueryRepository attendanceRepository = mock(AttendanceQueryRepository.class);
    private final StatisticsQueryService service = new StatisticsQueryService(repository, attendanceRepository);

    @Test
    void mapsTheConfiguredOperatingSemesterAndAggregateWithoutRecalculation() {
        StatisticsAggregate aggregate = new StatisticsAggregate(68, 12, 8, 10);
        stubOperatingSemester(PERIOD_CODE);
        when(repository.aggregate(SEMESTER_ID)).thenReturn(aggregate);

        StatisticsSummaryResponse response = service.getAdminStatistics();

        assertEquals(PERIOD_CODE, response.periodCode());
        assertEquals(68, response.averageAttendanceRate());
        assertEquals(12L, response.totalSessionCount());
        assertEquals(8L, response.activeMemberCount());
        assertEquals(10L, response.totalMemberCount());
        var order = inOrder(attendanceRepository, repository);
        order.verify(attendanceRepository).findCurrentSemester();
        order.verify(repository).aggregate(SEMESTER_ID);
        verifyNoMoreInteractions(attendanceRepository, repository);
    }

    @Test
    void preservesANullAverageForAnEmptyAggregate() {
        stubOperatingSemester(PERIOD_CODE);
        when(repository.aggregate(SEMESTER_ID)).thenReturn(new StatisticsAggregate(null, 0, 0, 0));

        StatisticsSummaryResponse response = service.getAdminStatistics();

        assertEquals(PERIOD_CODE, response.periodCode());
        assertNull(response.averageAttendanceRate());
        assertEquals(0L, response.totalSessionCount());
        assertEquals(0L, response.activeMemberCount());
        assertEquals(0L, response.totalMemberCount());
    }

    @Test
    void missingOperatingSemesterStopsBeforeAggregation() {
        when(attendanceRepository.findCurrentSemester()).thenReturn(Optional.empty());

        assertMissingOperatingSemester();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void aMissingPeriodCodeIsAnOperatingConfigurationError(String periodCode) {
        when(attendanceRepository.findCurrentSemester())
                .thenReturn(Optional.of(new OperatingSemester(SEMESTER_ID, periodCode)));

        assertMissingOperatingSemester();
    }

    @Test
    void repositoryFailureIsPropagatedWithoutFabricatingStatistics() {
        IllegalStateException failure = new IllegalStateException("storage failure");
        stubOperatingSemester(PERIOD_CODE);
        when(repository.aggregate(SEMESTER_ID)).thenThrow(failure);

        IllegalStateException exception = assertThrows(IllegalStateException.class, service::getAdminStatistics);

        assertSame(failure, exception);
        verify(attendanceRepository).findCurrentSemester();
        verify(repository).aggregate(SEMESTER_ID);
    }

    private void assertMissingOperatingSemester() {
        BusinessException exception = assertThrows(BusinessException.class, service::getAdminStatistics);

        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, exception.getErrorCode());
        verify(attendanceRepository).findCurrentSemester();
        verifyNoInteractions(repository);
    }

    private void stubOperatingSemester(String periodCode) {
        when(attendanceRepository.findCurrentSemester())
                .thenReturn(Optional.of(new OperatingSemester(SEMESTER_ID, periodCode)));
    }
}
