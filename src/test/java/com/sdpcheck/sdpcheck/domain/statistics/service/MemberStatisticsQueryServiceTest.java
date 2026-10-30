package com.sdpcheck.sdpcheck.domain.statistics.service;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository.OperatingSemester;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.MemberAttendanceStatisticsItem;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.MemberAttendanceStatisticsResponse;
import com.sdpcheck.sdpcheck.domain.statistics.enums.MemberStatisticsSort;
import com.sdpcheck.sdpcheck.domain.statistics.repository.MemberStatisticsQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
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

class MemberStatisticsQueryServiceTest {
    private static final UUID SEMESTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PERIOD_CODE = "2026-2H";

    private final MemberStatisticsQueryRepository repository = mock(MemberStatisticsQueryRepository.class);
    private final AttendanceQueryRepository attendanceRepository = mock(AttendanceQueryRepository.class);
    private final MemberStatisticsQueryService service = new MemberStatisticsQueryService(repository, attendanceRepository);

    @Test
    void mapsTheOperatingSemesterAndMemberSnapshotWithoutChangingNullableFields() {
        MemberAttendanceStatisticsItem first = item(1L, "가나다", 17, 5, 1, 2, 8, 75, false);
        MemberAttendanceStatisticsItem second = item(2L, "라마바", 18, 0, 0, 0, 0, null, null);
        stubOperatingSemester(PERIOD_CODE);
        when(repository.findMembers(SEMESTER_ID, MemberStatisticsSort.NAME_ASC, 4, 6))
                .thenReturn(List.of(first, second));

        MemberAttendanceStatisticsResponse response = service.getMemberAttendanceStatistics(
                2, 3, MemberStatisticsSort.NAME_ASC);

        assertEquals(PERIOD_CODE, response.periodCode());
        assertEquals(List.of(first, second), response.members());
        assertEquals(2, response.page());
        assertEquals(3, response.size());
        assertEquals(false, response.hasNext());
        assertNull(response.members().get(1).attendanceRate());
        assertNull(response.members().get(1).isAtRisk());
        var order = inOrder(attendanceRepository, repository);
        order.verify(attendanceRepository).findCurrentSemester();
        order.verify(repository).findMembers(SEMESTER_ID, MemberStatisticsSort.NAME_ASC, 4, 6);
        verifyNoMoreInteractions(attendanceRepository, repository);
    }

    @Test
    void trimsOnlyTheExtraSizePlusOneRowAndSetsHasNext() {
        List<MemberAttendanceStatisticsItem> snapshot = new ArrayList<>(List.of(
                item(1L, "가", 17, 1, 0, 0, 1, 100, false),
                item(2L, "나", 17, 0, 1, 0, 1, 100, false),
                item(3L, "다", 17, 0, 0, 1, 1, 0, true)));
        stubOperatingSemester(PERIOD_CODE);
        when(repository.findMembers(SEMESTER_ID, MemberStatisticsSort.ATTENDANCE_RATE_ASC, 3, 0))
                .thenReturn(snapshot);

        MemberAttendanceStatisticsResponse response = service.getMemberAttendanceStatistics(
                0, 2, MemberStatisticsSort.ATTENDANCE_RATE_ASC);

        assertEquals(List.of(snapshot.get(0), snapshot.get(1)), response.members());
        assertEquals(true, response.hasNext());
        assertEquals(3, snapshot.size());
        verify(repository).findMembers(SEMESTER_ID, MemberStatisticsSort.ATTENDANCE_RATE_ASC, 3, 0);
    }

    @Test
    void anExactPageSizeDoesNotSetHasNext() {
        stubOperatingSemester(PERIOD_CODE);
        when(repository.findMembers(SEMESTER_ID, MemberStatisticsSort.ATTENDANCE_RATE_DESC, 3, 2))
                .thenReturn(List.of(
                        item(1L, "가", 17, 1, 0, 0, 1, 100, false),
                        item(2L, "나", 17, 0, 0, 1, 1, 0, true)));

        MemberAttendanceStatisticsResponse response = service.getMemberAttendanceStatistics(
                1, 2, MemberStatisticsSort.ATTENDANCE_RATE_DESC);

        assertEquals(2, response.members().size());
        assertEquals(false, response.hasNext());
    }

    @Test
    void anEmptyBeyondRangePageIsSuccessful() {
        stubOperatingSemester(PERIOD_CODE);
        when(repository.findMembers(SEMESTER_ID, MemberStatisticsSort.ATTENDANCE_RATE_ASC, 21, 140))
                .thenReturn(List.of());

        MemberAttendanceStatisticsResponse response = service.getMemberAttendanceStatistics(
                7, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC);

        assertEquals(PERIOD_CODE, response.periodCode());
        assertEquals(List.of(), response.members());
        assertEquals(7, response.page());
        assertEquals(20, response.size());
        assertEquals(false, response.hasNext());
    }

    @ParameterizedTest
    @EnumSource(MemberStatisticsSort.class)
    void forwardsEverySupportedSortToTheRepository(MemberStatisticsSort sort) {
        stubOperatingSemester(PERIOD_CODE);
        when(repository.findMembers(SEMESTER_ID, sort, 2, 0)).thenReturn(List.of());

        MemberAttendanceStatisticsResponse response = service.getMemberAttendanceStatistics(0, 1, sort);

        assertEquals(List.of(), response.members());
        verify(repository).findMembers(SEMESTER_ID, sort, 2, 0);
    }

    @Test
    void hugePagesUseALongOffsetWithoutOverflow() {
        long expectedOffset = (long) Integer.MAX_VALUE * 50;
        stubOperatingSemester(PERIOD_CODE);
        when(repository.findMembers(SEMESTER_ID, MemberStatisticsSort.NAME_ASC, 51, expectedOffset))
                .thenReturn(List.of());

        MemberAttendanceStatisticsResponse response = service.getMemberAttendanceStatistics(
                Integer.MAX_VALUE, 50, MemberStatisticsSort.NAME_ASC);

        assertEquals(Integer.MAX_VALUE, response.page());
        assertEquals(false, response.hasNext());
        verify(repository).findMembers(SEMESTER_ID, MemberStatisticsSort.NAME_ASC, 51, expectedOffset);
    }

    @Test
    void invalidPageSizeOrSortStopsBeforeAnyDatabaseQuery() {
        assertValidationError(-1, 20, MemberStatisticsSort.NAME_ASC);
        assertValidationError(0, 0, MemberStatisticsSort.NAME_ASC);
        assertValidationError(0, 51, MemberStatisticsSort.NAME_ASC);
        assertValidationError(0, 20, null);

        verifyNoInteractions(attendanceRepository, repository);
    }

    @Test
    void missingOperatingSemesterStopsBeforeMemberQuery() {
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
    void repositoryFailureIsPropagatedWithoutFabricatingAPage() {
        IllegalStateException failure = new IllegalStateException("storage failure");
        stubOperatingSemester(PERIOD_CODE);
        when(repository.findMembers(SEMESTER_ID, MemberStatisticsSort.NAME_ASC, 21, 0)).thenThrow(failure);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.getMemberAttendanceStatistics(0, 20, MemberStatisticsSort.NAME_ASC));

        assertSame(failure, exception);
        verify(attendanceRepository).findCurrentSemester();
        verify(repository).findMembers(SEMESTER_ID, MemberStatisticsSort.NAME_ASC, 21, 0);
    }

    private void assertMissingOperatingSemester() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getMemberAttendanceStatistics(0, 20, MemberStatisticsSort.NAME_ASC));

        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, exception.getErrorCode());
        verify(attendanceRepository).findCurrentSemester();
        verifyNoInteractions(repository);
    }

    private void stubOperatingSemester(String periodCode) {
        when(attendanceRepository.findCurrentSemester())
                .thenReturn(Optional.of(new OperatingSemester(SEMESTER_ID, periodCode)));
    }

    private void assertValidationError(int page, int size, MemberStatisticsSort sort) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getMemberAttendanceStatistics(page, size, sort));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    private MemberAttendanceStatisticsItem item(
            long memberId,
            String name,
            int generation,
            long presentCount,
            long lateCount,
            long absentCount,
            long totalSessionCount,
            Integer attendanceRate,
            Boolean isAtRisk
    ) {
        return new MemberAttendanceStatisticsItem(memberId, name, generation, presentCount, lateCount,
                absentCount, totalSessionCount, attendanceRate, isAtRisk);
    }
}
