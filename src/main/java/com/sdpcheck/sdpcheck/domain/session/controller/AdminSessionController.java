package com.sdpcheck.sdpcheck.domain.session.controller;

import com.sdpcheck.sdpcheck.domain.session.dto.request.CreateSessionReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.request.UpdateSessionReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.CreateSessionResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.EndSessionResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.SessionDetailResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.StartSessionResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.UpdateSessionResDTO;
import com.sdpcheck.sdpcheck.domain.session.service.SessionService;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/sessions")
public class AdminSessionController {

	private static final String GET_SESSION_CODE = "SESSION2001";
	private static final String GET_SESSION_MESSAGE = "세션 상세 조회에 성공했습니다.";
	private static final String CREATE_SESSION_CODE = "SESSION2011";
	private static final String CREATE_SESSION_MESSAGE = "\uC138\uC158\uC774 \uC0DD\uC131\uB418\uC5C8\uC2B5\uB2C8\uB2E4.";
	private static final String UPDATE_SESSION_CODE = "SESSION2004";
	private static final String UPDATE_SESSION_MESSAGE = "\uC138\uC158 \uC815\uBCF4\uAC00 \uC218\uC815\uB418\uC5C8\uC2B5\uB2C8\uB2E4.";
	private static final String START_SESSION_CODE = "SESSION2002";
	private static final String START_SESSION_MESSAGE = "\uC138\uC158\uC774 \uC2DC\uC791\uB418\uC5C8\uC2B5\uB2C8\uB2E4.";
	private static final String END_SESSION_CODE = "SESSION2003";
	private static final String END_SESSION_MESSAGE = "세션이 종료되었습니다.";

	private final SessionService sessionService;

	public AdminSessionController(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	@GetMapping("/{sessionId}")
	public ResponseEntity<ApiResponse<SessionDetailResDTO>> getSession(@PathVariable UUID sessionId) {
		SessionDetailResDTO response = sessionService.getSession(sessionId);

		return ResponseEntity.ok(ApiResponse.of(GET_SESSION_CODE, GET_SESSION_MESSAGE, response));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<CreateSessionResDTO>> createSession(@RequestBody(required = false) CreateSessionReqDTO request) {
		CreateSessionResDTO response = sessionService.createSession(request);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(CREATE_SESSION_CODE, CREATE_SESSION_MESSAGE, response));
	}

	@PatchMapping("/{sessionId}")
	public ResponseEntity<ApiResponse<UpdateSessionResDTO>> updateSession(
			@PathVariable UUID sessionId,
			@RequestBody(required = false) UpdateSessionReqDTO request
	) {
		UpdateSessionResDTO response = sessionService.updateSession(sessionId, request);

		return ResponseEntity.ok(ApiResponse.of(UPDATE_SESSION_CODE, UPDATE_SESSION_MESSAGE, response));
	}

	@DeleteMapping("/{sessionId}")
	public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
		sessionService.deleteSession(sessionId);

		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{sessionId}/start")
	public ResponseEntity<ApiResponse<StartSessionResDTO>> startSession(@PathVariable UUID sessionId) {
		StartSessionResDTO response = sessionService.startSession(sessionId);

		return ResponseEntity.ok(ApiResponse.of(START_SESSION_CODE, START_SESSION_MESSAGE, response));
	}

	@PostMapping("/{sessionId}/end")
	public ResponseEntity<ApiResponse<EndSessionResDTO>> endSession(@PathVariable UUID sessionId) {
		EndSessionResDTO response = sessionService.endSession(sessionId);

		return ResponseEntity.ok(ApiResponse.of(END_SESSION_CODE, END_SESSION_MESSAGE, response));
	}
}
