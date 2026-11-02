package com.sdpcheck.sdpcheck.domain.file.exception;

import com.sdpcheck.sdpcheck.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FileErrorCode implements ErrorCode {
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "첨부 파일을 찾을 수 없습니다."),
    FILE_NOT_READY(HttpStatus.CONFLICT, "첨부 파일을 연결할 수 없습니다."),
    INVALID_IMAGE(HttpStatus.UNPROCESSABLE_CONTENT, "유효하지 않은 이미지입니다.");

    private final HttpStatus status;
    private final String message;

    FileErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override public HttpStatus getHttpStatus() { return status; }
    @Override public String getCode() { return name(); }
    @Override public String getMessage() { return message; }
}
