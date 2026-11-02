package com.sdpcheck.sdpcheck.domain.attendance.service;

import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceHistoryItem;
import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AttendanceQueryServiceTest {
    private static final long MEMBER_ID = 42L;
    private static final UUID SEMESTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final AttendanceQueryRepository repository = mock(AttendanceQueryRepository.class);
    private final AttendanceQueryService service = new AttendanceQueryService(repository);

    @Test
    void returnsAnEmptyFirstPageForTheCurrentSemester() {
        stubCurrentSemester();
        when(repository.countHistory(MEMBER_ID, SEMESTER_ID)).thenReturn(0L);
        when(repository.findHistory(MEMBER_ID, SEMESTER_ID, 0, 10)).thenReturn(List.of());

        var result = service.getMyAttendances(MEMBER_ID, 0, 10);

        assertEquals(List.of(), result.items());
        assertEquals(0, result.page());
        assertEquals(10, result.size());
        assertEquals(0, result.totalElements());
        assertEquals(0, result.totalPages());
        assertFalse(result.hasNext());
    }

    @Test
    void calculatesFirstAndLastPageMetadata() {
        stubCurrentSemester();
        when(repository.countHistory(MEMBER_ID, SEMESTER_ID)).thenReturn(21L);
        when(repository.findHistory(MEMBER_ID, SEMESTER_ID, 0, 10))
                .thenReturn(List.of(item(1, AttendanceStatus.PRESENT)));
        when(repository.findHistory(MEMBER_ID, SEMESTER_ID, 2, 10))
                .thenReturn(List.of(item(2, AttendanceStatus.LATE)));

        var first = service.getMyAttendances(MEMBER_ID, 0, 10);
        var last = service.getMyAttendances(MEMBER_ID, 2, 10);

        assertEquals(3, first.totalPages());
        assertTrue(first.hasNext());
        assertEquals(3, last.totalPages());
        assertFalse(last.hasNext());
    }

    @Test
    void outOfRangePageIsEmptyButRetainsTheTotals() {
        stubCurrentSemester();
        when(repository.countHistory(MEMBER_ID, SEMESTER_ID)).thenReturn(1L);
        when(repository.findHistory(MEMBER_ID, SEMESTER_ID, 1, 10)).thenReturn(List.of());

        var result = service.getMyAttendances(MEMBER_ID, 1, 10);

        assertEquals(List.of(), result.items());
        assertEquals(1, result.page());
        assertEquals(1, result.totalElements());
        assertEquals(1, result.totalPages());
        assertFalse(result.hasNext());
    }

    @Test
    void maximumPageNumberDoesNotOverflowHasNext() {
        stubCurrentSemester();
        when(repository.countHistory(MEMBER_ID, SEMESTER_ID)).thenReturn(1L);
        when(repository.findHistory(MEMBER_ID, SEMESTER_ID, Integer.MAX_VALUE, 100))
                .thenReturn(List.of());

        var result = service.getMyAttendances(MEMBER_ID, Integer.MAX_VALUE, 100);

        assertEquals(Integer.MAX_VALUE, result.page());
        assertEquals(1, result.totalPages());
        assertFalse(result.hasNext());
        verify(repository).findHistory(MEMBER_ID, SEMESTER_ID, Integer.MAX_VALUE, 100);
    }

    @Test
    void invalidPagingIsRejectedBeforeAnyDatabaseAccess() {
        assertValidationError(-1, 10);
        assertValidationError(0, 0);
        assertValidationError(0, 101);

        verifyNoInteractions(repository);
    }

    @Test
    void missingCurrentSemesterStopsBeforeTheHistoryQueries() {
        when(repository.findCurrentSemesterId()).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getMyAttendances(MEMBER_ID, 0, 10));

        assertEquals(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED, exception.getErrorCode());
        verify(repository).findCurrentSemesterId();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void forwardsTheAuthenticatedMemberAndResolvedSemesterToTheRepository() {
        AttendanceHistoryItem absent = item(1, AttendanceStatus.ABSENT);
        stubCurrentSemester();
        when(repository.countHistory(MEMBER_ID, SEMESTER_ID)).thenReturn(1L);
        when(repository.findHistory(MEMBER_ID, SEMESTER_ID, 0, 10)).thenReturn(List.of(absent));

        var result = service.getMyAttendances(MEMBER_ID, 0, 10);

        assertEquals(List.of(absent), result.items());
        InOrder order = inOrder(repository);
        order.verify(repository).findCurrentSemesterId();
        order.verify(repository).countHistory(MEMBER_ID, SEMESTER_ID);
        order.verify(repository).findHistory(MEMBER_ID, SEMESTER_ID, 0, 10);
    }

    @Test
    void passesThroughAllAttendanceStatesAndTimestamps() {
        List<AttendanceHistoryItem> history = List.of(
                item(1, AttendanceStatus.PRESENT),
                item(2, AttendanceStatus.LATE),
                item(3, AttendanceStatus.ABSENT));
        stubCurrentSemester();
        when(repository.countHistory(MEMBER_ID, SEMESTER_ID)).thenReturn(3L);
        when(repository.findHistory(MEMBER_ID, SEMESTER_ID, 0, 10)).thenReturn(history);

        var result = service.getMyAttendances(MEMBER_ID, 0, 10);

        assertEquals(history, result.items());
        assertEquals(AttendanceStatus.ABSENT, result.items().get(2).attendanceStatus());
        assertNull(result.items().get(2).checkedInAt());
    }

    private void stubCurrentSemester() {
        when(repository.findCurrentSemesterId()).thenReturn(Optional.of(SEMESTER_ID));
    }

    private void assertValidationError(int page, int size) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getMyAttendances(MEMBER_ID, page, size));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    private AttendanceHistoryItem item(int suffix, AttendanceStatus status) {
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-10-%02dT19:00:00+09:00".formatted(suffix));
        OffsetDateTime checkedInAt = status == AttendanceStatus.ABSENT ? null : startsAt.minusMinutes(3);
        return new AttendanceHistoryItem(
                UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix)),
                "정기 세션 " + suffix, startsAt, status, checkedInAt);
    }
}
