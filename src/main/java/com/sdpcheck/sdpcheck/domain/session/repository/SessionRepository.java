package com.sdpcheck.sdpcheck.domain.session.repository;

import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, Long> {
}
