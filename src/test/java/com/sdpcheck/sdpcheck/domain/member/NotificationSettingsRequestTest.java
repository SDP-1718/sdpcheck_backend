package com.sdpcheck.sdpcheck.domain.member;

import com.sdpcheck.sdpcheck.domain.member.dto.request.NotificationSettingsRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSettingsRequestTest {
    @ParameterizedTest
    @CsvSource(value = {
            "true,null,true",
            "false,null,true",
            "null,true,true",
            "null,false,true",
            "null,null,false",
            "true,true,true",
            "true,false,true",
            "false,true,true",
            "false,false,true"
    }, nullValues = "null")
    void atLeastOneNonNullSettingIsRequired(Boolean sessionStartEnabled,
                                           Boolean newNoticeEnabled,
                                           boolean valid) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(
                    new NotificationSettingsRequest(sessionStartEnabled, newNoticeEnabled));
            assertThat(violations.isEmpty()).isEqualTo(valid);
            if (!valid) {
                assertThat(violations).singleElement()
                        .satisfies(error -> assertThat(error.getMessage())
                                .isEqualTo("수정할 알림 설정을 하나 이상 전달해야 합니다."));
            }
        }
    }
}
