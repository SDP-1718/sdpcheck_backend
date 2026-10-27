package com.sdpcheck.sdpcheck.domain.member.repository;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByLoginId(String loginId);

    Optional<Member> findByLoginId(String loginId);
}
