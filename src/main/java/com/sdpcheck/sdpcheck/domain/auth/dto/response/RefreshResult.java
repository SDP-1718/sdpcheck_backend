package com.sdpcheck.sdpcheck.domain.auth.dto.response;

public record RefreshResult(
        RefreshResponse response,
        String refreshToken
) {
    public static RefreshResult of(RefreshResponse response, String refreshToken){
        return new RefreshResult(
                response,
                refreshToken
        );
    }
}
