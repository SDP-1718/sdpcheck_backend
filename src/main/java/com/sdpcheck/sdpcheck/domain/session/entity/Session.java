package com.sdpcheck.sdpcheck.domain.session.entity;

import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Session {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false)
	private LocalDate sessionDate;

	@Column(nullable = false)
	private LocalTime startTime;

	@Column(nullable = false)
	private LocalTime endTime;

	@Column(nullable = false, length = 255)
	private String location;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SessionStatus status;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	private LocalDateTime startedAt;

	private Session(String title, LocalDate sessionDate, LocalTime startTime, LocalTime endTime, String location) {
		this.title = title;
		this.sessionDate = sessionDate;
		this.startTime = startTime;
		this.endTime = endTime;
		this.location = location;
		this.status = SessionStatus.SCHEDULED;
	}

	public static Session create(
			String title,
			LocalDate sessionDate,
			LocalTime startTime,
			LocalTime endTime,
			String location
	) {
		return new Session(title, sessionDate, startTime, endTime, location);
	}

	public void update(String title, LocalDate sessionDate, LocalTime startTime, LocalTime endTime, String location) {
		this.title = title;
		this.sessionDate = sessionDate;
		this.startTime = startTime;
		this.endTime = endTime;
		this.location = location;
		this.updatedAt = LocalDateTime.now();
	}

	public void start(LocalDateTime startedAt) {
		this.status = SessionStatus.IN_PROGRESS;
		this.startedAt = startedAt;
	}

	@PrePersist
	private void prePersist() {
		this.createdAt = LocalDateTime.now();
	}

	@PreUpdate
	private void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
