package com.sdpcheck.sdpcheck.domain.session.exception;

import com.sdpcheck.sdpcheck.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@RequiredArgsConstructor
public enum SessionErrorCode implements ErrorCode {

	INVALID_SESSION_INPUT(HttpStatus.BAD_REQUEST, "SESSION4001", "세션 정보를 올바르게 입력해주세요."),
	INVALID_SESSION_TIME_RANGE(HttpStatus.BAD_REQUEST, "SESSION4002", "종료 예정 시간은 시작 예정 시간보다 이후여야 합니다."),
	INVALID_SESSION_DATE_TIME(HttpStatus.BAD_REQUEST, "SESSION4003", "세션 날짜 또는 시간이 허용되지 않는 값입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

	@Override
	public HttpStatusCode getHttpStatus() {
		return httpStatus;
	}
}
