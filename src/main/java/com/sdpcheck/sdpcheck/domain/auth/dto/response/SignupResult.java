package com.sdpcheck.sdpcheck.domain.auth.dto.response;

public record SignupResult(
        SignupResponse response,
        String refreshToken
) {

    public static SignupResult of(SignupResponse signupResponse, String refreshToken){
        return new SignupResult(
                signupResponse,
                refreshToken
        );
    }
}
