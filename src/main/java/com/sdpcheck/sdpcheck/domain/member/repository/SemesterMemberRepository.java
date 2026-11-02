package com.sdpcheck.sdpcheck.domain.member.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SemesterMemberRepository {
    private final JdbcTemplate jdbc;

    public void register(UUID semesterId, Long memberId) {
        jdbc.update("""
                INSERT INTO semester_members (semester_id, member_id) VALUES (?, ?)
                ON CONFLICT (semester_id, member_id) DO NOTHING
                """, semesterId, memberId);
    }
}
