package com.sdpcheck.sdpcheck.domain.member.service;

import com.sdpcheck.sdpcheck.domain.member.dto.request.NotificationSettingsRequest;
import com.sdpcheck.sdpcheck.domain.member.dto.response.MyInfoResponse;
import com.sdpcheck.sdpcheck.domain.member.dto.response.NotificationSettingsResponse;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.exception.MemberErrorCode;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MyInfoResponse getMyInfo(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        return MyInfoResponse.from(member);
    }

    @Transactional(readOnly = true)
    public NotificationSettingsResponse getNotificationSettings(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        return NotificationSettingsResponse.from(member);
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(
            Long memberId, NotificationSettingsRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        if (request.sessionStartEnabled() != null) {
            member.updateSessionStartEnabled(request.sessionStartEnabled());
        }
        if (request.newNoticeEnabled() != null) {
            member.updateNewNoticeEnabled(request.newNoticeEnabled());
        }

        return NotificationSettingsResponse.from(member);
    }
}
