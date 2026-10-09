package com.sdpcheck.sdpcheck.domain.schedule.service;

import com.sdpcheck.sdpcheck.domain.schedule.dto.request.CreateScheduleRequest;
import com.sdpcheck.sdpcheck.domain.schedule.repository.ScheduleRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScheduleServiceTest {
    private final ScheduleRepository repository = mock(ScheduleRepository.class);
    private final ScheduleService service = new ScheduleService(repository);

    @Test
    void monthBoundariesUseSeoulMidnightAndCountMergedItems() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 11, 1);
        OffsetDateTime start = OffsetDateTime.parse("2026-10-01T00:00:00+09:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-11-01T00:00:00+09:00");
        when(repository.count(start, end, null)).thenReturn(21L);
        when(repository.find(start, end, null, 2, 20)).thenReturn(List.of());

        var result = service.list(from, to, null, 2, 20);

        assertEquals(21, result.page().totalElements());
        assertEquals(2, result.page().totalPages());
        assertEquals(start.toInstant(), OffsetDateTime.parse("2026-09-30T15:00:00Z").toInstant());
        verify(repository).find(start, end, null, 2, 20);
    }

    @Test
    void omittedRangeQueriesAllPeriods() {
        when(repository.count(null, null, "SESSION")).thenReturn(0L);
        when(repository.find(null, null, "SESSION", 1, 20)).thenReturn(List.of());

        var result = service.list(null, null, "SESSION", 1, 20);

        assertEquals(0, result.page().totalPages());
        verify(repository).find(null, null, "SESSION", 1, 20);
    }

    @Test
    void invalidFiltersAreRejectedBeforeDatabaseAccess() {
        assertThrows(BusinessException.class,
                () -> service.list(LocalDate.of(2026, 10, 1), null, null, 1, 20));
        assertThrows(BusinessException.class,
                () -> service.list(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 10, 3), null, 1, 20));
        assertThrows(BusinessException.class,
                () -> service.list(null, null, "NOTICE", 1, 20));
        assertThrows(BusinessException.class,
                () -> service.list(null, null, null, 0, 20));
        assertThrows(BusinessException.class,
                () -> service.list(null, null, null, 1, 101));
        verifyNoInteractions(repository);
    }

    @Test
    void creationUsesAuthenticatedMemberAndTrimsTitle() {
        var request = new CreateScheduleRequest("  운영진 회의  ",
                OffsetDateTime.parse("2026-10-02T19:00:00+09:00"), null);
        service.create(request, 7L);

        ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
        verify(repository).create(id.capture(), eq("운영진 회의"), eq(request.startsAt()),
                eq(null), eq(7L));
        assertNotNull(id.getValue());
    }
}
