package com.sdpcheck.sdpcheck.domain.device.repository;

import com.sdpcheck.sdpcheck.domain.device.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO push_tokens (member_id, token) VALUES (:memberId, :token)
            ON CONFLICT (token) DO UPDATE SET member_id = EXCLUDED.member_id
            """, nativeQuery = true)
    void upsert(@Param("memberId") Long memberId, @Param("token") String token);

    Optional<PushToken> findByToken(String token);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PushToken p WHERE p.id = :tokenId AND p.memberId = :memberId")
    int deleteOwnedToken(@Param("tokenId") Long tokenId, @Param("memberId") Long memberId);
}
