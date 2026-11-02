package com.sdpcheck.sdpcheck.global.exception;

public class RateLimitExceededException extends BusinessException {
    private final int retryAfterSeconds;

    public RateLimitExceededException(int retryAfterSeconds) {
        super(CommonErrorCode.RATE_LIMITED);
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("retryAfterSeconds must be positive");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
