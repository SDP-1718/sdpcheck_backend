package com.sdpcheck.sdpcheck.domain.session.service;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import com.sdpcheck.sdpcheck.domain.session.dto.request.CheckInReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.CheckInResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.SessionQrResDTO;
import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import com.sdpcheck.sdpcheck.domain.session.exception.SessionErrorCode;
import com.sdpcheck.sdpcheck.domain.session.exception.SessionException;
import com.sdpcheck.sdpcheck.domain.session.repository.SessionAttendanceRepository;
import com.sdpcheck.sdpcheck.domain.session.repository.SessionRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SessionCheckInService {

	// Check-ins up to the scheduled start are PRESENT, then LATE until this grace period ends.
	public static final Duration LATE_GRACE_PERIOD = Duration.ofMinutes(20);

	private final SessionRepository sessionRepository;
	private final SessionAttendanceRepository sessionAttendanceRepository;
	private final SessionQrTokenProvider qrTokenProvider;

	public SessionCheckInService(
			SessionRepository sessionRepository,
			SessionAttendanceRepository sessionAttendanceRepository,
			SessionQrTokenProvider qrTokenProvider
	) {
		this.sessionRepository = sessionRepository;
		this.sessionAttendanceRepository = sessionAttendanceRepository;
		this.qrTokenProvider = qrTokenProvider;
	}

	public SessionQrResDTO issueQr(UUID sessionId) {
		Session session = findInProgressSession(sessionId);

		OffsetDateTime expiresAt = OffsetDateTime.now().plus(SessionQrTokenProvider.TOKEN_TTL);
		String token = qrTokenProvider.createToken(session.getId(), expiresAt);

		return new SessionQrResDTO(session.getId(), token, Session.inZone(expiresAt));
	}

	@Transactional
	public CheckInResDTO checkIn(long memberId, CheckInReqDTO request) {
		if (request == null || request.qrToken() == null || request.qrToken().isBlank()) {
			throw new SessionException(SessionErrorCode.INVALID_QR_TOKEN);
		}

		UUID sessionId = qrTokenProvider.getSessionId(request.qrToken())
				.orElseThrow(() -> new SessionException(SessionErrorCode.INVALID_QR_TOKEN));
		Session session = findInProgressSession(sessionId);

		if (!sessionAttendanceRepository.isTarget(sessionId, memberId)) {
			throw new SessionException(SessionErrorCode.NOT_ATTENDANCE_TARGET);
		}

		OffsetDateTime checkedInAt = OffsetDateTime.now();
		AttendanceStatus status = resolveStatus(session, checkedInAt);

		if (!sessionAttendanceRepository.insertAttendance(sessionId, memberId, status, checkedInAt)) {
			throw new SessionException(SessionErrorCode.ALREADY_CHECKED_IN);
		}

		return new CheckInResDTO(sessionId, status, Session.inZone(checkedInAt));
	}

	private Session findInProgressSession(UUID sessionId) {
		Session session = sessionRepository.findByIdAndDeletedAtIsNull(sessionId)
				.orElseThrow(() -> new SessionException(SessionErrorCode.SESSION_NOT_FOUND));

		if (session.getStatus() == SessionStatus.SCHEDULED) {
			throw new SessionException(SessionErrorCode.SESSION_NOT_STARTED);
		}
		if (session.getStatus() != SessionStatus.IN_PROGRESS) {
			throw new SessionException(SessionErrorCode.SESSION_ALREADY_ENDED);
		}
		return session;
	}

	private AttendanceStatus resolveStatus(Session session, OffsetDateTime checkedInAt) {
		if (!checkedInAt.isAfter(session.getStartsAt())) {
			return AttendanceStatus.PRESENT;
		}
		if (!checkedInAt.isAfter(session.getStartsAt().plus(LATE_GRACE_PERIOD))) {
			return AttendanceStatus.LATE;
		}
		throw new SessionException(SessionErrorCode.CHECK_IN_CLOSED);
	}
}
