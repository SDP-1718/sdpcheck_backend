package com.sdpcheck.sdpcheck.domain.auth.dto.response;

public record AccessTokenResponse(
        String accessToken,
        boolean onboardingCompleted,
        String name,
        Integer generation
) {
}
