package com.sdpcheck.sdpcheck.domain.notice.service;

import com.sdpcheck.sdpcheck.domain.notice.enums.NoticeDraftMode;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NoticeDraftServiceTest {
    private final NoticeDraftRateLimiter limiter = mock(NoticeDraftRateLimiter.class);

    @Test
    void disabledFeatureDoesNotConsumeQuota() {
        var service = new NoticeDraftService(limiter, false);
        assertEquals("FEATURE_UNAVAILABLE", assertThrows(BusinessException.class,
                () -> service.generate(7L)).getErrorCode().getCode());
        verifyNoInteractions(limiter);
    }

    @Test
    void allowedCallsAlwaysReturnSameImmutableMock() {
        when(limiter.acquire(7L)).thenReturn(new NoticeDraftRateLimiter.Permit(true, 0));
        var service = new NoticeDraftService(limiter, true);
        var first = service.generate(7L);
        assertEquals(first, service.generate(7L));
        assertEquals("정기 세션 안내", first.title());
        assertEquals("10월 2일 금요일 오후 7시에 정기 세션이 진행됩니다.", first.content());
        assertEquals(NoticeDraftMode.MOCK, first.mode());
    }

    @Test
    void quotaDenialPreservesRetryAfter() {
        when(limiter.acquire(7L)).thenReturn(new NoticeDraftRateLimiter.Permit(false, 23));
        var service = new NoticeDraftService(limiter, true);
        var exception = assertThrows(RateLimitExceededException.class, () -> service.generate(7L));
        assertEquals("RATE_LIMITED", exception.getErrorCode().getCode());
        assertEquals(23, exception.retryAfterSeconds());
    }
}
