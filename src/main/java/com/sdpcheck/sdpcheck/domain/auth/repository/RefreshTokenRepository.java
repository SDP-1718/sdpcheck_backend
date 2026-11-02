package com.sdpcheck.sdpcheck.domain.auth.repository;

import com.sdpcheck.sdpcheck.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByMemberId(Long memberId);

    @Modifying
    @Query("delete from RefreshToken token where token.token = :token")
    int deleteByToken(@Param("token") String token);
}
