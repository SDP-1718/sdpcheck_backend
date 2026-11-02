package com.sdpcheck.sdpcheck.domain.schedule.exception;

import com.sdpcheck.sdpcheck.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode implements ErrorCode {
    VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 사용자가 수정했습니다. 다시 조회해주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() { return name(); }
}
