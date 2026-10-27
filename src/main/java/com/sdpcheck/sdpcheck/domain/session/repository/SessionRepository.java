package com.sdpcheck.sdpcheck.domain.session.repository;

import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, UUID> {

	Optional<Session> findByIdAndDeletedAtIsNull(UUID id);
}
