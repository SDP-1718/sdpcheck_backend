package com.sdpcheck.sdpcheck.domain.session.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.session.dto.request.CheckInReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.CheckInResDTO;
import com.sdpcheck.sdpcheck.domain.session.service.SessionCheckInService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionCheckInController {

	private static final String CHECK_IN_CODE = "SESSION2007";
	private static final String CHECK_IN_MESSAGE = "출석 체크가 완료되었습니다.";

	private final SessionCheckInService sessionCheckInService;

	public SessionCheckInController(SessionCheckInService sessionCheckInService) {
		this.sessionCheckInService = sessionCheckInService;
	}

	@PostMapping("/check-in")
	public ResponseEntity<ApiResponse<CheckInResDTO>> checkIn(
			@AuthenticationPrincipal AuthenticatedMember member,
			@RequestBody(required = false) CheckInReqDTO request
	) {
		if (member == null) {
			throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
		}
		CheckInResDTO response = sessionCheckInService.checkIn(member.memberId(), request);

		return ResponseEntity.ok(ApiResponse.of(CHECK_IN_CODE, CHECK_IN_MESSAGE, response));
	}
}
