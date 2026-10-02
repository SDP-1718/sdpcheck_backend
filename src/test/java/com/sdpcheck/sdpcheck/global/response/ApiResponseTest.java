package com.sdpcheck.sdpcheck.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ApiResponseTest {

	private final JsonMapper objectMapper = JsonMapper.builder()
			.changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
			.build();

	@Test
	void successContainsTheAgreedEnvelope() {
		ApiResponse<ExampleResDTO> response = ApiResponse.success(new ExampleResDTO("스딥첵"));

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.code()).isEqualTo("SUCCESS");
		assertThat(response.message()).isEqualTo(CommonSuccessCode.SUCCESS.getMessage());
		assertThat(objectMapper.writeValueAsString(response)).isEqualTo(
				"{\"isSuccess\":true,\"code\":\"SUCCESS\",\"message\":\"요청을 성공적으로 처리했습니다.\",\"result\":{\"name\":\"스딥첵\"}}"
		);
	}

	@Test
	void createdUsesTheCreatedCode() {
		ApiResponse<Long> response = ApiResponse.created(1L);

		assertThat(response.isSuccess()).isTrue();
		assertThat(response.code()).isEqualTo("CREATED");
		assertThat(response.result()).isEqualTo(1L);
	}

	@Test
	void failureKeepsNullResultEvenWhenNullPropertiesAreExcludedGlobally() {
		ApiResponse<Void> response = ApiResponse.failure(CommonErrorCode.VALIDATION_ERROR);

		assertThat(objectMapper.writeValueAsString(response)).isEqualTo(
				"{\"isSuccess\":false,\"code\":\"VALIDATION_ERROR\",\"message\":\"입력값이 올바르지 않습니다.\",\"result\":null}"
		);
	}

	@Test
	void successKeepsNullAndEmptyResults() {
		assertThat(objectMapper.writeValueAsString(ApiResponse.success(null))).contains("\"result\":null");
		assertThat(objectMapper.writeValueAsString(ApiResponse.success(List.of()))).contains("\"result\":[]");
	}

	record ExampleResDTO(String name) {
	}
}
