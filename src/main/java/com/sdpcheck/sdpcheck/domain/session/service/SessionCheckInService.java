package com.sdpcheck.sdpcheck.domain.session.service;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import com.sdpcheck.sdpcheck.domain.session.dto.request.CheckInReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.request.UpdateAttendanceReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.CheckInResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.SessionAttendanceResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.SessionQrResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.UpdateAttendanceResDTO;
import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import com.sdpcheck.sdpcheck.domain.session.enums.LiveAttendanceStatus;
import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import com.sdpcheck.sdpcheck.domain.session.exception.SessionErrorCode;
import com.sdpcheck.sdpcheck.domain.session.exception.SessionException;
import com.sdpcheck.sdpcheck.domain.session.repository.SessionAttendanceRepository;
import com.sdpcheck.sdpcheck.domain.session.repository.SessionRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
			boolean markedAbsent = sessionAttendanceRepository.findAttendanceStatus(sessionId, memberId)
					.filter(existing -> existing == AttendanceStatus.ABSENT)
					.isPresent();
			throw new SessionException(markedAbsent
					? SessionErrorCode.ATTENDANCE_MARKED_ABSENT
					: SessionErrorCode.ALREADY_CHECKED_IN);
		}

		return new CheckInResDTO(sessionId, status, Session.inZone(checkedInAt));
	}

	public SessionAttendanceResDTO getLiveAttendance(UUID sessionId) {
		Session session = sessionRepository.findByIdAndDeletedAtIsNull(sessionId)
				.orElseThrow(() -> new SessionException(SessionErrorCode.SESSION_NOT_FOUND));
		if (session.getStatus() == SessionStatus.SCHEDULED) {
			throw new SessionException(SessionErrorCode.SESSION_NOT_STARTED);
		}

		OffsetDateTime checkInClosesAt = session.getStartsAt().plus(LATE_GRACE_PERIOD);
		boolean checkInClosed = session.getStatus() != SessionStatus.IN_PROGRESS
				|| OffsetDateTime.now().isAfter(checkInClosesAt);

		List<SessionAttendanceResDTO.MemberAttendance> members = sessionAttendanceRepository
				.findTargetAttendances(sessionId).stream()
				.map(target -> new SessionAttendanceResDTO.MemberAttendance(
						target.memberId(),
						target.name(),
						target.generation(),
						toLiveStatus(target.status(), checkInClosed),
						Session.inZone(target.checkedInAt())
				))
				.toList();

		return new SessionAttendanceResDTO(
				sessionId,
				session.getStatus(),
				Session.inZone(checkInClosesAt),
				summarize(members),
				members
		);
	}

	@Transactional
	public UpdateAttendanceResDTO updateAttendance(UUID sessionId, long memberId, UpdateAttendanceReqDTO request) {
		AttendanceStatus status = parseStatus(request);

		Session session = sessionRepository.findByIdAndDeletedAtIsNull(sessionId)
				.orElseThrow(() -> new SessionException(SessionErrorCode.SESSION_NOT_FOUND));
		if (session.getStatus() == SessionStatus.SCHEDULED) {
			throw new SessionException(SessionErrorCode.SESSION_NOT_STARTED);
		}
		sessionAttendanceRepository.findTargetAttendance(sessionId, memberId)
				.orElseThrow(() -> new SessionException(SessionErrorCode.ATTENDANCE_TARGET_NOT_FOUND));

		sessionAttendanceRepository.upsertAttendanceStatus(sessionId, memberId, status, OffsetDateTime.now());

		SessionAttendanceRepository.TargetAttendance updated = sessionAttendanceRepository
				.findTargetAttendance(sessionId, memberId)
				.orElseThrow(() -> new SessionException(SessionErrorCode.ATTENDANCE_TARGET_NOT_FOUND));
		return new UpdateAttendanceResDTO(
				sessionId,
				updated.memberId(),
				updated.name(),
				updated.generation(),
				updated.status() == null ? AttendanceStatus.ABSENT : updated.status(),
				Session.inZone(updated.checkedInAt())
		);
	}

	private AttendanceStatus parseStatus(UpdateAttendanceReqDTO request) {
		if (request == null || request.status() == null) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_INPUT);
		}
		try {
			return AttendanceStatus.valueOf(request.status());
		} catch (IllegalArgumentException exception) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_INPUT);
		}
	}

	private LiveAttendanceStatus toLiveStatus(AttendanceStatus status, boolean checkInClosed) {
		if (status == AttendanceStatus.PRESENT) {
			return LiveAttendanceStatus.PRESENT;
		}
		if (status == AttendanceStatus.LATE) {
			return LiveAttendanceStatus.LATE;
		}
		if (status == AttendanceStatus.ABSENT) {
			return LiveAttendanceStatus.ABSENT;
		}
		return checkInClosed ? LiveAttendanceStatus.ABSENT : LiveAttendanceStatus.NOT_CHECKED_IN;
	}

	private SessionAttendanceResDTO.Summary summarize(List<SessionAttendanceResDTO.MemberAttendance> members) {
		Map<LiveAttendanceStatus, Long> counts = members.stream()
				.collect(Collectors.groupingBy(SessionAttendanceResDTO.MemberAttendance::status, Collectors.counting()));
		int present = counts.getOrDefault(LiveAttendanceStatus.PRESENT, 0L).intValue();
		int late = counts.getOrDefault(LiveAttendanceStatus.LATE, 0L).intValue();
		int total = members.size();
		// Late arrivals count toward the attendance rate.
		double attendanceRate = total == 0 ? 0.0 : Math.round((present + late) * 1000.0 / total) / 10.0;

		return new SessionAttendanceResDTO.Summary(
				total,
				present,
				late,
				counts.getOrDefault(LiveAttendanceStatus.NOT_CHECKED_IN, 0L).intValue(),
				counts.getOrDefault(LiveAttendanceStatus.ABSENT, 0L).intValue(),
				attendanceRate
		);
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
