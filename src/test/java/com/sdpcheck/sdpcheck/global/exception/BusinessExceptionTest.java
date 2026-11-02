package com.sdpcheck.sdpcheck.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BusinessExceptionTest {

	@Test
	void acceptsAnErrorCodeDefinedOutsideTheCommonEnum() {
		BusinessException exception = new BusinessException(TestErrorCode.EXAMPLE_CONFLICT);

		assertThat(exception.getErrorCode()).isSameAs(TestErrorCode.EXAMPLE_CONFLICT);
		assertThat(exception.getMessage()).isEqualTo("이미 처리된 요청입니다.");
		assertThat(exception.getCause()).isNull();
	}

	@Test
	void preservesTheCauseWithoutUsingItsMessageAsTheClientMessage() {
		RuntimeException cause = new RuntimeException("private database detail");
		BusinessException exception = new BusinessException(CommonErrorCode.INTERNAL_ERROR, cause);

		assertThat(exception.getCause()).isSameAs(cause);
		assertThat(exception.getMessage()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getMessage());
	}

	@Test
	void requiresAnErrorCode() {
		assertThatNullPointerException().isThrownBy(() -> new BusinessException(null));
	}

	enum TestErrorCode implements ErrorCode {
		EXAMPLE_CONFLICT;

		@Override
		public HttpStatus getHttpStatus() {
			return HttpStatus.CONFLICT;
		}

		@Override
		public String getCode() {
			return name();
		}

		@Override
		public String getMessage() {
			return "이미 처리된 요청입니다.";
		}
	}
}
