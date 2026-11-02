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
    private final OpenAiNoticeDraftClient ai;
    private final NoticeDraftRateLimiter rateLimiter;
    private final boolean enabled;

    public NoticeDraftService(NoticeDraftRateLimiter rateLimiter,
                              @Value("${notice.ai-draft.enabled:true}") boolean enabled, OpenAiNoticeDraftClient ai) {
        this.rateLimiter = rateLimiter;
        this.enabled = enabled;
        this.ai = ai;
    }

    public NoticeDraftResponse generate(long memberId, String memo) {
        if (!enabled || !ai.available()) {
            throw new BusinessException(CommonErrorCode.FEATURE_UNAVAILABLE);
        }
        NoticeDraftRateLimiter.Permit permit = rateLimiter.acquire(memberId);
        if (!permit.allowed()) {
            throw new RateLimitExceededException(permit.retryAfterSeconds());
        }
        return ai.generate(memo);
    }
}
