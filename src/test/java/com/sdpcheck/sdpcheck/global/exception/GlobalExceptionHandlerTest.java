package com.sdpcheck.sdpcheck.global.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(
		controllers = GlobalExceptionHandlerTest.TestController.class,
		properties = {"spring.config.import=", "spring.jackson.default-property-inclusion=non_null"}
)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsSuccessWithExactlyFourEnvelopeFields() throws Exception {
		mockMvc.perform(get("/test-api/success"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.*", hasSize(4)))
				.andExpect(jsonPath("$.isSuccess").value(true))
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.result.name").value("스딥첵"))
				.andExpect(jsonPath("$.success").doesNotExist());
	}

	@Test
	void returnsCreatedWithTheCorrectHttpStatus() throws Exception {
		mockMvc.perform(post("/test-api/created"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("CREATED"))
				.andExpect(jsonPath("$.result.name").value("스딥첵"));
	}

	@Test
	void acceptsValidRequestBody() throws Exception {
		mockMvc.perform(post("/test-api/validation")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"스딥첵\",\"count\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.name").value("스딥첵"));
	}

	@Test
	void mapsRequestBodyValidationToBadRequest() throws Exception {
		assertError(mockMvc.perform(post("/test-api/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\",\"count\":0}")), CommonErrorCode.VALIDATION_ERROR);
	}

	@Test
	void mapsMalformedJsonToBadRequest() throws Exception {
		assertError(mockMvc.perform(post("/test-api/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{broken")), CommonErrorCode.VALIDATION_ERROR);
	}

	@Test
	void mapsMissingBodyToBadRequest() throws Exception {
		assertError(mockMvc.perform(post("/test-api/validation")
				.contentType(MediaType.APPLICATION_JSON)), CommonErrorCode.VALIDATION_ERROR);
	}

	@Test
	void mapsMissingQueryParameterToBadRequest() throws Exception {
		assertError(mockMvc.perform(get("/test-api/query")), CommonErrorCode.VALIDATION_ERROR);
	}

	@Test
	void mapsQueryParameterTypeMismatchToBadRequest() throws Exception {
		assertError(mockMvc.perform(get("/test-api/query").param("page", "invalid")), CommonErrorCode.VALIDATION_ERROR);
	}

	@Test
	void mapsQueryParameterConstraintViolationToBadRequest() throws Exception {
		assertError(mockMvc.perform(get("/test-api/query").param("page", "-1")), CommonErrorCode.VALIDATION_ERROR);
	}

	@Test
	void mapsPathVariableTypeMismatchToBadRequest() throws Exception {
		assertError(mockMvc.perform(get("/test-api/items/not-a-number")), CommonErrorCode.VALIDATION_ERROR);
	}

	@Test
	void mapsPathVariableConstraintViolationToBadRequest() throws Exception {
		assertError(mockMvc.perform(get("/test-api/items/0")), CommonErrorCode.VALIDATION_ERROR);
	}

	@Test
	void mapsMissingResourceToNotFound() throws Exception {
		assertError(mockMvc.perform(get("/missing-resource")), CommonErrorCode.RESOURCE_NOT_FOUND);
	}

	@Test
	void preservesAllowHeaderForMethodNotAllowed() throws Exception {
		assertError(mockMvc.perform(post("/test-api/success")), CommonErrorCode.METHOD_NOT_ALLOWED)
				.andExpect(header().string(HttpHeaders.ALLOW, "GET"));
	}

	@Test
	void mapsUnsupportedContentType() throws Exception {
		assertError(mockMvc.perform(post("/test-api/validation")
				.contentType(MediaType.TEXT_PLAIN)
				.content("text")), CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	void mapsUnacceptableResponseType() throws Exception {
		assertError(mockMvc.perform(get("/test-api/success").accept(MediaType.APPLICATION_XML)), CommonErrorCode.NOT_ACCEPTABLE);
	}

	@Test
	void supportsAnErrorCodeDefinedByAnotherDomain() throws Exception {
		assertError(mockMvc.perform(get("/test-api/business")), BusinessExceptionTest.TestErrorCode.EXAMPLE_CONFLICT);
	}

	@Test
	void hidesUnexpectedExceptionDetails() throws Exception {
		assertError(mockMvc.perform(get("/test-api/failure")), CommonErrorCode.INTERNAL_ERROR);
	}

	@Test
	void doesNotTreatEveryDatabaseFailureAsAConflict() throws Exception {
		assertError(mockMvc.perform(get("/test-api/database-failure")), CommonErrorCode.INTERNAL_ERROR);
	}

	@Test
	void treatsReturnValueValidationAsAServerError() throws Exception {
		assertError(mockMvc.perform(get("/test-api/invalid-return")), CommonErrorCode.INTERNAL_ERROR);
	}

	@Test
	void preservesRetryAfterHeaderWithoutExposingFrameworkDetails() throws Exception {
		assertError(mockMvc.perform(get("/test-api/rate-limit")), CommonErrorCode.RATE_LIMITED)
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"));
	}

	@ParameterizedTest
	@ValueSource(ints = {401, 403, 409, 422, 502, 504, 599})
	void preservesOtherFrameworkHttpErrorsWithoutExposingTheirReasons(int httpStatus) throws Exception {
		mockMvc.perform(get("/test-api/framework-error/{status}", httpStatus))
				.andExpect(status().is(httpStatus))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.*", hasSize(4)))
				.andExpect(jsonPath("$.isSuccess").value(false))
				.andExpect(jsonPath("$.code").value("HTTP_" + httpStatus))
				.andExpect(jsonPath("$.message").value(
						httpStatus >= 500 ? CommonErrorCode.INTERNAL_ERROR.getMessage() : "요청을 처리할 수 없습니다."
				))
				.andExpect(jsonPath("$.result", nullValue()));
	}

	@Test
	void leavesNoContentResponseEmpty() throws Exception {
		mockMvc.perform(post("/test-api/no-content"))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));
	}

	@Test
	void leavesRedirectResponseUnwrapped() throws Exception {
		mockMvc.perform(get("/test-api/redirect"))
				.andExpect(status().isFound())
				.andExpect(header().string(HttpHeaders.LOCATION, "/test-api/success"))
				.andExpect(content().string(""));
	}

	@Test
	void leavesFileResponseUnwrapped() throws Exception {
		mockMvc.perform(get("/test-api/file"))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statistics.xlsx"))
				.andExpect(content().bytes(new byte[] {1, 2, 3}));
	}

	@Test
	void preservesNullAndEmptySuccessResults() throws Exception {
		mockMvc.perform(get("/test-api/null"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.*", hasSize(4)))
				.andExpect(jsonPath("$.result", nullValue()));
		mockMvc.perform(get("/test-api/empty"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result").isEmpty());
	}

	private ResultActions assertError(ResultActions result, ErrorCode errorCode) throws Exception {
		return result
				.andExpect(status().is(errorCode.getHttpStatus().value()))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.*", hasSize(4)))
				.andExpect(jsonPath("$.isSuccess").value(false))
				.andExpect(jsonPath("$.code").value(errorCode.getCode()))
				.andExpect(jsonPath("$.message").value(errorCode.getMessage()))
				.andExpect(jsonPath("$.result", nullValue()));
	}

	@RestController
	@RequestMapping("/test-api")
	static class TestController {

		@GetMapping("/success")
		ApiResponse<ExampleResDTO> success() {
			return ApiResponse.success(new ExampleResDTO("스딥첵"));
		}

		@PostMapping("/created")
		ResponseEntity<ApiResponse<ExampleResDTO>> created() {
			return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(new ExampleResDTO("스딥첵")));
		}

		@PostMapping(value = "/validation", consumes = MediaType.APPLICATION_JSON_VALUE)
		ApiResponse<ExampleResDTO> validation(@Valid @RequestBody ExampleReqDTO request) {
			return ApiResponse.success(new ExampleResDTO(request.name()));
		}

		@GetMapping("/query")
		ApiResponse<Integer> query(@RequestParam("page") @Min(0) int page) {
			return ApiResponse.success(page);
		}

		@GetMapping("/items/{id}")
		ApiResponse<Long> item(@PathVariable("id") @Positive long id) {
			return ApiResponse.success(id);
		}

		@GetMapping("/business")
		ApiResponse<Void> business() {
			throw new BusinessException(BusinessExceptionTest.TestErrorCode.EXAMPLE_CONFLICT);
		}

		@GetMapping("/failure")
		ApiResponse<Void> failure() {
			throw new IllegalStateException("private implementation detail");
		}

		@GetMapping("/database-failure")
		ApiResponse<Void> databaseFailure() {
			throw new DataIntegrityViolationException("private database detail");
		}

		@GetMapping("/invalid-return")
		@NotNull
		String invalidReturn() {
			return null;
		}

		@GetMapping("/rate-limit")
		ApiResponse<Void> rateLimit() {
			ErrorResponseException exception = new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS);
			exception.getHeaders().set(HttpHeaders.RETRY_AFTER, "30");
			exception.getBody().setDetail("private framework detail");
			throw exception;
		}

		@PostMapping("/no-content")
		ResponseEntity<Void> noContent() {
			return ResponseEntity.noContent().build();
		}

		@GetMapping("/framework-error/{status}")
		ApiResponse<Void> frameworkError(@PathVariable("status") int status) {
			throw new ResponseStatusException(HttpStatusCode.valueOf(status), "private framework detail", null);
		}

		@GetMapping("/redirect")
		ResponseEntity<Void> redirect() {
			return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/test-api/success")).build();
		}

		@GetMapping("/file")
		ResponseEntity<byte[]> file() {
			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statistics.xlsx")
					.body(new byte[] {1, 2, 3});
		}

		@GetMapping("/null")
		ApiResponse<Void> nullResult() {
			return ApiResponse.success(null);
		}

		@GetMapping("/empty")
		ApiResponse<List<String>> emptyResult() {
			return ApiResponse.success(List.of());
		}
	}

	record ExampleReqDTO(@NotBlank String name, @NotNull @Min(1) Integer count) {
	}

	record ExampleResDTO(String name) {
	}
}
