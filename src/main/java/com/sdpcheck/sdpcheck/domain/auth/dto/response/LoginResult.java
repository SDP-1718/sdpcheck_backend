package com.sdpcheck.sdpcheck.domain.auth.dto.response;

import com.sdpcheck.sdpcheck.domain.auth.entity.RefreshToken;

public record LoginResult(
        LoginResponse response,
        String refreshToken
) {

    public static LoginResult of(LoginResponse loginResponse, String refreshToken){
        return new LoginResult(
                loginResponse,
                refreshToken
        );
    }
}
