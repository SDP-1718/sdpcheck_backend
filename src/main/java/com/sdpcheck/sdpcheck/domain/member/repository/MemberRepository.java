package com.sdpcheck.sdpcheck.domain.member.repository;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByLoginId(String loginId);

    Optional<Member> findByLoginId(String loginId);

    Optional<Member> findByKakaoId(Long kakaoId);

    @Query("""
            SELECT DISTINCT m.generation
            FROM Member m
            WHERE m.onboardingCompleted = true
                  AND m.role IN :roles
                  AND m.generation > 0
            ORDER BY m.generation DESC
            """)
    List<Integer> findCompletedMemberGenerations(@Param("roles") Collection<Role> roles);
}
