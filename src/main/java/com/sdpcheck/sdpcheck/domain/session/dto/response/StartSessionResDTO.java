package com.sdpcheck.sdpcheck.domain.session.dto.response;

import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StartSessionResDTO(
		UUID sessionId,
		SessionStatus status,
		OffsetDateTime startedAt
) {

	public static StartSessionResDTO from(Session session) {
		return new StartSessionResDTO(
				session.getId(),
				session.getStatus(),
				Session.inZone(session.getStartedAt())
		);
	}
}
