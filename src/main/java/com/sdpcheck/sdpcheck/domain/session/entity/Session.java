package com.sdpcheck.sdpcheck.domain.session.entity;

import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "study_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Session {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false)
	private OffsetDateTime startsAt;

	private OffsetDateTime endsAt;

	@Column(length = 200)
	private String location;

	@Column(nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(nullable = false)
	private OffsetDateTime updatedAt;

	private OffsetDateTime startedAt;

	private OffsetDateTime endedAt;

	private OffsetDateTime cancelledAt;

	private OffsetDateTime deletedAt;

	private UUID semesterId;

	private Session(String title, OffsetDateTime startsAt, OffsetDateTime endsAt, String location, UUID semesterId) {
		this.title = title;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.location = location;
		this.semesterId = semesterId;
	}

	public static Session create(
			String title,
			LocalDate sessionDate,
			LocalTime startTime,
			LocalTime endTime,
			String location,
			UUID semesterId
	) {
		return new Session(
				title,
				toInstant(sessionDate, startTime),
				toInstant(sessionDate, endTime),
				location,
				semesterId
		);
	}

	public static OffsetDateTime toInstant(LocalDate date, LocalTime time) {
		return date.atTime(time).atZone(ZONE).toOffsetDateTime();
	}

	public static OffsetDateTime inZone(OffsetDateTime timestamp) {
		return timestamp == null ? null : timestamp.atZoneSameInstant(ZONE).toOffsetDateTime();
	}

	public void update(String title, LocalDate sessionDate, LocalTime startTime, LocalTime endTime, String location) {
		this.title = title;
		this.startsAt = toInstant(sessionDate, startTime);
		this.endsAt = toInstant(sessionDate, endTime);
		this.location = location;
		this.updatedAt = OffsetDateTime.now();
	}

	public void start(OffsetDateTime startedAt) {
		this.startedAt = startedAt;
	}

	public void end(OffsetDateTime endedAt) {
		this.endedAt = endedAt;
	}

	public void delete(OffsetDateTime deletedAt) {
		this.deletedAt = deletedAt;
	}

	public SessionStatus getStatus() {
		if (cancelledAt != null) {
			return SessionStatus.CANCELED;
		}
		if (endedAt != null) {
			return SessionStatus.COMPLETED;
		}
		if (startedAt != null) {
			return SessionStatus.IN_PROGRESS;
		}
		return SessionStatus.SCHEDULED;
	}

	public LocalDate getSessionDate() {
		return startsAt.atZoneSameInstant(ZONE).toLocalDate();
	}

	public LocalTime getStartTime() {
		return startsAt.atZoneSameInstant(ZONE).toLocalTime();
	}

	public LocalTime getEndTime() {
		return endsAt == null ? null : endsAt.atZoneSameInstant(ZONE).toLocalTime();
	}

	@PrePersist
	private void prePersist() {
		OffsetDateTime now = OffsetDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	private void preUpdate() {
		this.updatedAt = OffsetDateTime.now();
	}
}
