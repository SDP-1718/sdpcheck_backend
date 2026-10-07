package com.sdpcheck.sdpcheck.domain.session.dto.response;

import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import java.time.LocalDateTime;

public record StartSessionResDTO(
		Long sessionId,
		SessionStatus status,
		LocalDateTime startedAt
) {

	public static StartSessionResDTO from(Session session) {
		return new StartSessionResDTO(
				session.getId(),
				session.getStatus(),
				session.getStartedAt()
		);
	}
}
