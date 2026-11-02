package com.sdpcheck.sdpcheck.domain.statistics.service;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository.OperatingSemester;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.AttendanceStatisticsExportFile;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.MemberAttendanceStatisticsItem;
import com.sdpcheck.sdpcheck.domain.statistics.enums.MemberStatisticsSort;
import com.sdpcheck.sdpcheck.domain.statistics.repository.MemberStatisticsQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class StatisticsExportServiceTest {
    private static final UUID SEMESTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PERIOD_CODE = "2026-2H";

    private final MemberStatisticsQueryRepository repository = mock(MemberStatisticsQueryRepository.class);
    private final AttendanceQueryRepository attendanceRepository = mock(AttendanceQueryRepository.class);
    private final AttendanceStatisticsWorkbookWriter writer = mock(AttendanceStatisticsWorkbookWriter.class);
    private final StatisticsExportService service = new StatisticsExportService(repository, attendanceRepository, writer,
            Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC));

    @Test
    void exportsTheCurrentSemesterSnapshotWithAKstDatedFilename() throws Exception {
        List<MemberAttendanceStatisticsItem> snapshot = List.of(
                item(1L, "가나다", 17, 5, 1, 2, 8, 75, false),
                item(2L, "라마바", 18, 0, 0, 0, 0, null, null));
        byte[] content = {80, 75, 3, 4};
        stubOperatingSemester(PERIOD_CODE);
        when(repository.findAllMembers(SEMESTER_ID, MemberStatisticsSort.ATTENDANCE_RATE_ASC)).thenReturn(snapshot);
        when(writer.write(snapshot)).thenReturn(content);

        AttendanceStatisticsExportFile response = service.exportAttendanceStatistics(
                MemberStatisticsSort.ATTENDANCE_RATE_ASC);

        assertEquals("sdpcheck_attendance_2026-2H_20261029.xlsx", response.filename());
        assertArrayEquals(content, response.content());
        var order = inOrder(attendanceRepository, repository, writer);
        order.verify(attendanceRepository).findCurrentSemester();
        order.verify(repository).findAllMembers(SEMESTER_ID, MemberStatisticsSort.ATTENDANCE_RATE_ASC);
        order.verify(writer).write(snapshot);
        verifyNoMoreInteractions(attendanceRepository, repository, writer);
    }

    @ParameterizedTest
    @EnumSource(MemberStatisticsSort.class)
    void forwardsEverySupportedSortToTheFullExportQuery(MemberStatisticsSort sort) throws Exception {
        List<MemberAttendanceStatisticsItem> snapshot = List.of();
        stubOperatingSemester(PERIOD_CODE);
        when(repository.findAllMembers(SEMESTER_ID, sort)).thenReturn(snapshot);
        when(writer.write(snapshot)).thenReturn(new byte[0]);

        AttendanceStatisticsExportFile response = service.exportAttendanceStatistics(sort);

        assertEquals("sdpcheck_attendance_2026-2H_20261029.xlsx", response.filename());
        assertArrayEquals(new byte[0], response.content());
        verify(repository).findAllMembers(SEMESTER_ID, sort);
        verify(writer).write(snapshot);
    }

    @Test
    void replacesEveryUnsafePeriodCodeCharacterBeforeUsingItInTheFilename() throws Exception {
        List<MemberAttendanceStatisticsItem> snapshot = List.of();
        stubOperatingSemester("2026/2 H!");
        when(repository.findAllMembers(SEMESTER_ID, MemberStatisticsSort.NAME_ASC)).thenReturn(snapshot);
        when(writer.write(snapshot)).thenReturn(new byte[] {1});

        AttendanceStatisticsExportFile response = service.exportAttendanceStatistics(MemberStatisticsSort.NAME_ASC);

        assertEquals("sdpcheck_attendance_2026_2_H__20261029.xlsx", response.filename());
    }

    @Test
    void aNullSortIsRejectedBeforeAnyDatabaseOrWorkbookAccess() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.exportAttendanceStatistics(null));

        assertEquals(CommonErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verifyNoInteractions(attendanceRepository, repository, writer);
    }

    @Test
    void aMissingOperatingSemesterStopsBeforeExportQueries() {
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
    void anIoFailureFromWorkbookWritingIsConvertedToTheCommonInternalError() throws Exception {
        List<MemberAttendanceStatisticsItem> snapshot = List.of(item(1L, "가", 17, 0, 0, 1, 1, 0, true));
        IOException failure = new IOException("disk full");
        stubOperatingSemester(PERIOD_CODE);
        when(repository.findAllMembers(SEMESTER_ID, MemberStatisticsSort.ATTENDANCE_RATE_DESC)).thenReturn(snapshot);
        when(writer.write(snapshot)).thenThrow(failure);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.exportAttendanceStatistics(MemberStatisticsSort.ATTENDANCE_RATE_DESC));

        assertEquals(CommonErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertSame(failure, exception.getCause());
        verify(repository).findAllMembers(SEMESTER_ID, MemberStatisticsSort.ATTENDANCE_RATE_DESC);
        verify(writer).write(snapshot);
    }

    private void assertMissingOperatingSemester() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.exportAttendanceStatistics(MemberStatisticsSort.ATTENDANCE_RATE_ASC));

        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, exception.getErrorCode());
        verify(attendanceRepository).findCurrentSemester();
        verifyNoInteractions(repository, writer);
    }

    private void stubOperatingSemester(String periodCode) {
        when(attendanceRepository.findCurrentSemester())
                .thenReturn(Optional.of(new OperatingSemester(SEMESTER_ID, periodCode)));
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
