package com.sdpcheck.sdpcheck.domain.attendance.exception;

import com.sdpcheck.sdpcheck.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AttendanceErrorCode implements ErrorCode {
    OPERATING_SEMESTER_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "운영 학기가 설정되지 않았습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
