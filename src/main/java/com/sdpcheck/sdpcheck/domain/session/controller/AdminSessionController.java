package com.sdpcheck.sdpcheck.domain.session.controller;

import com.sdpcheck.sdpcheck.domain.session.dto.request.CreateSessionReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.CreateSessionResDTO;
import com.sdpcheck.sdpcheck.domain.session.service.SessionService;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/sessions")
public class AdminSessionController {

	private static final String CREATE_SESSION_CODE = "SESSION2011";
	private static final String CREATE_SESSION_MESSAGE = "세션이 생성되었습니다.";

	private final SessionService sessionService;

	public AdminSessionController(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<CreateSessionResDTO>> createSession(@RequestBody(required = false) CreateSessionReqDTO request) {
		CreateSessionResDTO response = sessionService.createSession(request);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(CREATE_SESSION_CODE, CREATE_SESSION_MESSAGE, response));
	}
}
