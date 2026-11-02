package com.sdpcheck.sdpcheck.global.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonSuccessCode {

	SUCCESS(HttpStatus.OK, "요청을 성공적으로 처리했습니다."),
	CREATED(HttpStatus.CREATED, "리소스가 생성되었습니다.");

	private final HttpStatus httpStatus;
	private final String message;

	public String getCode() {
		return name();
	}
}
