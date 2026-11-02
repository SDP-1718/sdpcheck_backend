package com.sdpcheck.sdpcheck.domain.member.dto.response;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;

public record NotificationSettingsResponse(
        boolean sessionStartEnabled,
        boolean newNoticeEnabled
) {

    public static NotificationSettingsResponse from(Member member) {
        return new NotificationSettingsResponse(
                member.isSessionStartEnabled(),
                member.isNewNoticeEnabled()
        );
    }
}
