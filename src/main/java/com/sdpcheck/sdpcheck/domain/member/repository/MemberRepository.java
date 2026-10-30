package com.sdpcheck.sdpcheck.domain.member.repository;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByLoginId(String loginId);

    Optional<Member> findByLoginId(String loginId);

    Optional<Member> findByKakaoId(Long kakaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

    long countByRoleAndOnboardingCompletedTrue(Role role);

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
