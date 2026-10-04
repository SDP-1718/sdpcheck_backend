package com.sdpcheck.sdpcheck.global.exception;

import java.util.Objects;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, null);
	}

	public BusinessException(ErrorCode errorCode, Throwable cause) {
		super(Objects.requireNonNull(errorCode, "errorCode must not be null").getMessage(), cause);
		this.errorCode = errorCode;
	}
}
