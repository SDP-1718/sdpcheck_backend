package com.sdpcheck.sdpcheck.domain.member.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

public record NotificationSettingsRequest(
        Boolean sessionStartEnabled,
        Boolean newNoticeEnabled
) {
    @JsonIgnore
    @AssertTrue(message = "수정할 알림 설정을 하나 이상 전달해야 합니다.")
    public boolean isAnySettingProvided() {
        return sessionStartEnabled != null || newNoticeEnabled != null;
    }
}
