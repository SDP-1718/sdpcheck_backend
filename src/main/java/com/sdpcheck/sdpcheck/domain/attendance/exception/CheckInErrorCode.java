package com.sdpcheck.sdpcheck.domain.attendance.exception;

import com.sdpcheck.sdpcheck.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CheckInErrorCode implements ErrorCode {
    INVALID_QR_TOKEN(HttpStatus.BAD_REQUEST, "CHECK_IN400_1", "유효하지 않거나 다른 세션의 QR 코드입니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION404_1", "해당 세션을 찾을 수 없습니다."),
    SESSION_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "SESSION409_1", "진행 중인 세션에서만 출석 체크할 수 있습니다."),
    ALREADY_CHECKED_IN(HttpStatus.CONFLICT, "CHECK_IN409_1", "이미 출석 체크된 세션입니다."),
    QR_TOKEN_EXPIRED(HttpStatus.GONE, "CHECK_IN410_1", "QR 코드가 만료되었습니다. 최신 QR 코드를 스캔해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
