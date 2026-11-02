package com.sdpcheck.sdpcheck.domain.auth.dto.response;

public record OnboardingResponse(
        String name,
        Integer generation
) {

    public static OnboardingResponse of(String name, Integer generation){
        return new OnboardingResponse(
                name,
                generation
        );
    }
}
