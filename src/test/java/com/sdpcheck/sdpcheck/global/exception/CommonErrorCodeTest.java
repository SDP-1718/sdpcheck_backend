package com.sdpcheck.sdpcheck.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

class CommonErrorCodeTest {

	@ParameterizedTest
	@EnumSource(CommonErrorCode.class)
	void keepsTheDefinedCommonErrorCode(CommonErrorCode errorCode) {
		assertThat(CommonErrorCode.from(errorCode.getHttpStatus())).isSameAs(errorCode);
	}

	@ParameterizedTest
	@ValueSource(ints = {401, 403, 409, 422, 502, 504, 599})
	void preservesOtherHttpErrorStatuses(int status) {
		ErrorCode errorCode = CommonErrorCode.from(HttpStatusCode.valueOf(status));

		assertThat(errorCode.getHttpStatus().value()).isEqualTo(status);
		assertThat(errorCode.getCode()).isEqualTo("HTTP_" + status);
		assertThat(errorCode.getMessage()).isEqualTo(
				status >= 500 ? CommonErrorCode.INTERNAL_ERROR.getMessage() : "요청을 처리할 수 없습니다."
		);
	}

	@Test
	void doesNotReturnASuccessStatusForAnException() {
		assertThat(CommonErrorCode.from(HttpStatus.OK)).isSameAs(CommonErrorCode.INTERNAL_ERROR);
	}
}
