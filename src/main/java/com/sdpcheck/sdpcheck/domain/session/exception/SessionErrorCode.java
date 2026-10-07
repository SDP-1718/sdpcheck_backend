package com.sdpcheck.sdpcheck.domain.session.exception;

import com.sdpcheck.sdpcheck.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@RequiredArgsConstructor
public enum SessionErrorCode implements ErrorCode {

	INVALID_SESSION_INPUT(HttpStatus.BAD_REQUEST, "SESSION4001", "\uC694\uCCAD\uD55C \uC138\uC158 \uC815\uBCF4\uC758 \uD615\uC2DD\uC774 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4."),
	INVALID_SESSION_TIME_RANGE(HttpStatus.BAD_REQUEST, "SESSION4002", "\uC885\uB8CC \uC608\uC815 \uC2DC\uAC04\uC740 \uC2DC\uC791 \uC608\uC815 \uC2DC\uAC04\uBCF4\uB2E4 \uC774\uD6C4\uC5EC\uC57C \uD569\uB2C8\uB2E4."),
	INVALID_SESSION_DATE_TIME(HttpStatus.BAD_REQUEST, "SESSION4003", "\uC138\uC158 \uB0A0\uC9DC \uB610\uB294 \uC2DC\uAC04\uC774 \uD5C8\uC6A9\uB418\uC9C0 \uC54A\uB294 \uAC12\uC785\uB2C8\uB2E4."),
	SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION4041", "\uC694\uCCAD\uD55C \uC138\uC158\uC744 \uCC3E\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."),
	SESSION_ALREADY_STARTED(HttpStatus.CONFLICT, "SESSION4091", "\uC774\uBBF8 \uC2DC\uC791\uB41C \uC138\uC158\uC785\uB2C8\uB2E4."),
	SESSION_ALREADY_ENDED(HttpStatus.CONFLICT, "SESSION4092", "\uC885\uB8CC\uB41C \uC138\uC158\uC785\uB2C8\uB2E4."),
	SESSION_DELETE_ALREADY_STARTED(HttpStatus.CONFLICT, "SESSION4094", "\uC9C4\uD589 \uC911\uC778 \uC138\uC158\uC740 \uC0AD\uC81C\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."),
	SESSION_DELETE_ALREADY_ENDED(HttpStatus.CONFLICT, "SESSION4095", "\uC885\uB8CC\uB41C \uC138\uC158\uC740 \uC0AD\uC81C\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

	@Override
	public HttpStatusCode getHttpStatus() {
		return httpStatus;
	}
}
