package com.sdpcheck.sdpcheck.domain.session.repository;

import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<Session, UUID> {

	Optional<Session> findByIdAndDeletedAtIsNull(UUID id);

	// In-progress sessions come first, then not-yet-finished scheduled sessions by start time.
	@Query("""
			SELECT s FROM Session s
			WHERE s.deletedAt IS NULL
				AND s.cancelledAt IS NULL
				AND s.endedAt IS NULL
				AND (s.startedAt IS NOT NULL
					OR s.endsAt > :now
					OR (s.endsAt IS NULL AND s.startsAt > :now))
			ORDER BY CASE WHEN s.startedAt IS NOT NULL THEN 0 ELSE 1 END, s.startsAt ASC, s.id ASC
			""")
	List<Session> findNextSessions(@Param("now") OffsetDateTime now, Pageable pageable);
}
