package com.sdpcheck.sdpcheck.global.exception;

import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public @Nullable ResponseEntity<Object> handleBusinessException(BusinessException exception, WebRequest request) {
		return errorResponse(exception, exception.getErrorCode(), new HttpHeaders(), request);
	}

	@ExceptionHandler(Exception.class)
	public @Nullable ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
		return errorResponse(exception, CommonErrorCode.INTERNAL_ERROR, new HttpHeaders(), request);
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(
			Exception exception,
			@Nullable Object body,
			HttpHeaders headers,
			HttpStatusCode statusCode,
			WebRequest request
	) {
		return errorResponse(exception, CommonErrorCode.from(statusCode), headers, request);
	}

	private @Nullable ResponseEntity<Object> errorResponse(
			Exception exception,
			ErrorCode errorCode,
			HttpHeaders headers,
			WebRequest request
	) {
		if (errorCode.getHttpStatus().is5xxServerError()) {
			log.error("서버 요청 처리 중 오류가 발생했습니다.", exception);
		}

		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.putAll(headers);
		responseHeaders.setContentType(MediaType.APPLICATION_JSON);

		return super.handleExceptionInternal(
				exception,
				ApiResponse.failure(errorCode),
				responseHeaders,
				errorCode.getHttpStatus(),
				request
		);
	}
}
