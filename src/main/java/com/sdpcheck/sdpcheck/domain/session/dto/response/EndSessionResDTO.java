package com.sdpcheck.sdpcheck.domain.session.dto.response;

import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EndSessionResDTO(
		UUID sessionId,
		SessionStatus status,
		OffsetDateTime startedAt,
		OffsetDateTime endedAt
) {

	public static EndSessionResDTO from(Session session) {
		return new EndSessionResDTO(
				session.getId(),
				session.getStatus(),
				Session.inZone(session.getStartedAt()),
				Session.inZone(session.getEndedAt())
		);
	}
}
