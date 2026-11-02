package com.sdpcheck.sdpcheck.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sdpcheck.sdpcheck.global.exception.ErrorCode;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(
		@JsonProperty("isSuccess") boolean isSuccess,
		String code,
		String message,
		T result
) {

	public static <T> ApiResponse<T> success(T result) {
		return success(CommonSuccessCode.SUCCESS, result);
	}

	public static <T> ApiResponse<T> created(T result) {
		return success(CommonSuccessCode.CREATED, result);
	}

	public static <T> ApiResponse<T> of(String code, String message, T result) {
		return new ApiResponse<>(true, code, message, result);
	}

	public static ApiResponse<Void> failure(ErrorCode errorCode) {
		return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
	}

	private static <T> ApiResponse<T> success(CommonSuccessCode successCode, T result) {
		return new ApiResponse<>(true, successCode.getCode(), successCode.getMessage(), result);
	}
}
