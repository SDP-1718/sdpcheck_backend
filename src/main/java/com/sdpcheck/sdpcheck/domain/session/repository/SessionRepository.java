package com.sdpcheck.sdpcheck.domain.session.repository;

import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<Session, UUID> {

	Optional<Session> findByIdAndDeletedAtIsNull(UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Session s WHERE s.id = :id")
	Optional<Session> findByIdForUpdate(@Param("id") UUID id);

	// In-progress sessions come first, then not-yet-finished scheduled sessions by start time.
	// Sessions without an end time stay startable until their check-in window closes.
	@Query("""
			SELECT s FROM Session s
			WHERE s.deletedAt IS NULL
				AND s.cancelledAt IS NULL
				AND s.endedAt IS NULL
				AND (s.startedAt IS NOT NULL
					OR s.endsAt > :now
					OR (s.endsAt IS NULL AND s.startsAt > :startableAfter))
			ORDER BY CASE WHEN s.startedAt IS NOT NULL THEN 0 ELSE 1 END, s.startsAt ASC, s.id ASC
			""")
	List<Session> findNextSessions(
			@Param("now") OffsetDateTime now,
			@Param("startableAfter") OffsetDateTime startableAfter,
			Pageable pageable
	);

	Optional<Session> findFirstByStartedAtIsNotNullAndEndedAtIsNullAndCancelledAtIsNullAndDeletedAtIsNullOrderByStartedAtDesc();

	Optional<Session> findFirstByEndedAtIsNotNullAndCancelledAtIsNullAndDeletedAtIsNullOrderByEndedAtDesc();
}
