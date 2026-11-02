package com.sdpcheck.sdpcheck.domain.schedule.service;

import com.sdpcheck.sdpcheck.domain.schedule.dto.response.SchedulePageResponse;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleSummary;
import com.sdpcheck.sdpcheck.domain.schedule.enums.ScheduleType;
import com.sdpcheck.sdpcheck.domain.schedule.repository.ScheduleQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScheduleQueryServiceTest {

    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-10-08T19:00:00+09:00");

    private ScheduleQueryRepository repository;
    private ScheduleQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(ScheduleQueryRepository.class);
        service = new ScheduleQueryService(repository);
    }

    @Test
    void returnsEmptyPageMetadata() {
        when(repository.countVisible()).thenReturn(0L);
        when(repository.findVisiblePage(0, 10)).thenReturn(List.of());

        SchedulePageResponse result = service.getSchedules(0, 10);

        assertAll(
                () -> assertEquals(List.of(), result.items()),
                () -> assertEquals(0, result.page()),
                () -> assertEquals(10, result.size()),
                () -> assertEquals(0L, result.totalElements()),
                () -> assertEquals(0, result.totalPages()),
                () -> assertFalse(result.hasNext())
        );
    }

    @Test
    void firstPagePassesThroughItemsAndHasNextPage() {
        ScheduleSummary session = summary(ScheduleType.SESSION, "정기 세션");
        ScheduleSummary general = summary(ScheduleType.GENERAL, "행사");
        when(repository.countVisible()).thenReturn(21L);
        when(repository.findVisiblePage(0, 10)).thenReturn(List.of(session, general));

        SchedulePageResponse result = service.getSchedules(0, 10);

        assertAll(
                () -> assertEquals(List.of(session, general), result.items()),
                () -> assertSame(session, result.items().get(0)),
                () -> assertEquals(0, result.page()),
                () -> assertEquals(10, result.size()),
                () -> assertEquals(21L, result.totalElements()),
                () -> assertEquals(3, result.totalPages()),
                () -> assertTrue(result.hasNext())
        );
    }

    @Test
    void lastPartialPageHasNoNextPage() {
        ScheduleSummary last = summary(ScheduleType.SESSION, "마지막 일정");
        when(repository.countVisible()).thenReturn(21L);
        when(repository.findVisiblePage(2, 10)).thenReturn(List.of(last));

        SchedulePageResponse result = service.getSchedules(2, 10);

        assertAll(
                () -> assertEquals(List.of(last), result.items()),
                () -> assertEquals(2, result.page()),
                () -> assertEquals(3, result.totalPages()),
                () -> assertFalse(result.hasNext())
        );
    }

    @Test
    void outOfRangePageHasNoNextPage() {
        when(repository.countVisible()).thenReturn(21L);
        when(repository.findVisiblePage(3, 10)).thenReturn(List.of());

        SchedulePageResponse result = service.getSchedules(3, 10);

        assertAll(
                () -> assertEquals(List.of(), result.items()),
                () -> assertEquals(3, result.page()),
                () -> assertEquals(21L, result.totalElements()),
                () -> assertEquals(3, result.totalPages()),
                () -> assertFalse(result.hasNext())
        );
    }

    @Test
    void maximumIntegerPageDoesNotOverflowHasNextCalculation() {
        when(repository.countVisible()).thenReturn(1L);
        when(repository.findVisiblePage(Integer.MAX_VALUE, 10)).thenReturn(List.of());

        SchedulePageResponse result = service.getSchedules(Integer.MAX_VALUE, 10);

        assertAll(
                () -> assertEquals(Integer.MAX_VALUE, result.page()),
                () -> assertEquals(1, result.totalPages()),
                () -> assertFalse(result.hasNext())
        );
    }

    @Test
    void invalidPageOrSizeDoesNotQueryRepository() {
        assertValidationError(() -> service.getSchedules(-1, 10));
        assertValidationError(() -> service.getSchedules(0, 0));
        assertValidationError(() -> service.getSchedules(0, 101));

        verifyNoInteractions(repository);
    }

    private ScheduleSummary summary(ScheduleType type, String title) {
        return new ScheduleSummary(UUID.randomUUID(), type, title, STARTS_AT, "신촌 캠퍼스");
    }

    private void assertValidationError(Executable executable) {
        BusinessException exception = assertThrows(BusinessException.class, executable);
        assertSame(CommonErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }
}
