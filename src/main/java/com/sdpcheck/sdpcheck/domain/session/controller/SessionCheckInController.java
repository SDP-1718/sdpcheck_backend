package com.sdpcheck.sdpcheck.domain.session.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.session.dto.request.CheckInReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.CheckInResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.MySessionResDTO;
import com.sdpcheck.sdpcheck.domain.session.service.SessionCheckInService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionCheckInController {

	private static final String GET_MY_SESSION_CODE = "SESSION2010";
	private static final String GET_MY_SESSION_MESSAGE = "현재 세션 조회에 성공했습니다.";

	private final SessionCheckInService sessionCheckInService;

	public SessionCheckInController(SessionCheckInService sessionCheckInService) {
		this.sessionCheckInService = sessionCheckInService;
	}

	@GetMapping("/current")
	public ResponseEntity<ApiResponse<MySessionResDTO>> getMySession(@AuthenticationPrincipal AuthenticatedMember member) {
		if (member == null) {
			throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
		}
		MySessionResDTO response = sessionCheckInService.getMySession(member.memberId());

		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.of(GET_MY_SESSION_CODE, GET_MY_SESSION_MESSAGE, response));
	}

	@PostMapping("/{sessionId}/check-ins")
	public ResponseEntity<ApiResponse<CheckInResDTO>> createCheckIn(
			@AuthenticationPrincipal AuthenticatedMember member,
			@PathVariable UUID sessionId,
			@Valid @RequestBody CheckInReqDTO request
	) {
		if (member == null) {
			throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
		}
		CheckInResDTO response = sessionCheckInService.checkIn(sessionId, member.memberId(), request);

		return ResponseEntity.status(HttpStatus.CREATED)
				.cacheControl(CacheControl.noStore().cachePrivate())
				.body(ApiResponse.created(response));
	}
}
