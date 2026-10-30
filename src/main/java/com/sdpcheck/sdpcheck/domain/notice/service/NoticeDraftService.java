package com.sdpcheck.sdpcheck.domain.notice.service;

import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeDraftResponse;
import com.sdpcheck.sdpcheck.domain.notice.enums.NoticeDraftMode;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import com.sdpcheck.sdpcheck.global.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NoticeDraftService {
    private static final NoticeDraftResponse MOCK_DRAFT = new NoticeDraftResponse(
            "정기 세션 안내", "10월 2일 금요일 오후 7시에 정기 세션이 진행됩니다.", NoticeDraftMode.MOCK);
    private final NoticeDraftRateLimiter rateLimiter;
    private final boolean enabled;

    public NoticeDraftService(NoticeDraftRateLimiter rateLimiter,
                              @Value("${notice.ai-draft.enabled:true}") boolean enabled) {
        this.rateLimiter = rateLimiter;
        this.enabled = enabled;
    }

    public NoticeDraftResponse generate(long memberId) {
        if (!enabled) {
            throw new BusinessException(CommonErrorCode.FEATURE_UNAVAILABLE);
        }
        NoticeDraftRateLimiter.Permit permit = rateLimiter.acquire(memberId);
        if (!permit.allowed()) {
            throw new RateLimitExceededException(permit.retryAfterSeconds());
        }
        return MOCK_DRAFT;
    }
}
