package com.sdpcheck.sdpcheck.domain.session.dto.response;

import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import java.time.LocalDateTime;

public record EndSessionResDTO(
		Long sessionId,
		SessionStatus status,
		LocalDateTime startedAt,
		LocalDateTime endedAt
) {

	public static EndSessionResDTO from(Session session) {
		return new EndSessionResDTO(
				session.getId(),
				session.getStatus(),
				session.getStartedAt(),
				session.getEndedAt()
		);
	}
}
