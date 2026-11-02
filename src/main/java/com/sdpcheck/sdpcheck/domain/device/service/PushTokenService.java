package com.sdpcheck.sdpcheck.domain.device.service;

import com.sdpcheck.sdpcheck.domain.device.dto.request.PushTokenRequest;
import com.sdpcheck.sdpcheck.domain.device.dto.response.PushTokenResponse;
import com.sdpcheck.sdpcheck.domain.device.exception.PushTokenErrorCode;
import com.sdpcheck.sdpcheck.domain.device.repository.PushTokenRepository;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushTokenService {
    private final PushTokenRepository pushTokenRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public PushTokenResponse addPushToken(Long memberId, PushTokenRequest pushTokenRequest){
        if(!memberRepository.existsById(memberId)){
            throw new BusinessException(PushTokenErrorCode.MEMBER_NOT_FOUND);
        }

        pushTokenRepository.upsert(memberId, pushTokenRequest.token());
        var pushToken = pushTokenRepository.findByToken(pushTokenRequest.token()).orElseThrow();

        return PushTokenResponse.from(pushToken);
    }

    @Transactional
    public void deletePushToken(Long memberId, Long tokenId) {
        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(PushTokenErrorCode.MEMBER_NOT_FOUND);
        }

        int deletedCount = pushTokenRepository.deleteOwnedToken(tokenId, memberId);
        if (deletedCount == 0) {
            throw new BusinessException(PushTokenErrorCode.PUSH_TOKEN_NOT_FOUND);
        }
    }

}
