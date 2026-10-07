package com.sdpcheck.sdpcheck.domain.auth.dto.response;

public record RefreshResponse(
        String accessToken
) {
    public static RefreshResponse from(String accessToken){
        return new RefreshResponse(
                accessToken
        );
    }
}
