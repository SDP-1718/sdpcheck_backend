package com.sdpcheck.sdpcheck.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
	NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "지원하지 않는 응답 형식입니다."),
	PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "요청 크기가 허용 범위를 초과했습니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
	FEATURE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String message;

	@Override
	public String getCode() {
		return name();
	}

	static ErrorCode from(HttpStatusCode statusCode) {
		for (CommonErrorCode errorCode : values()) {
			if (errorCode.httpStatus.value() == statusCode.value()) {
				return errorCode;
			}
		}
		return statusCode.isError() ? new HttpErrorCode(statusCode) : INTERNAL_ERROR;
	}

	private record HttpErrorCode(HttpStatusCode httpStatus) implements ErrorCode {

		@Override
		public HttpStatusCode getHttpStatus() {
			return httpStatus;
		}

		@Override
		public String getCode() {
			return "HTTP_" + httpStatus.value();
		}

		@Override
		public String getMessage() {
			return httpStatus.is5xxServerError() ? INTERNAL_ERROR.getMessage() : "요청을 처리할 수 없습니다.";
		}
	}
}
