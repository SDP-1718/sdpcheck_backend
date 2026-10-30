package com.sdpcheck.sdpcheck.domain.device.dto.response;

import com.sdpcheck.sdpcheck.domain.device.entity.PushToken;

public record PushTokenResponse(
        Long tokenId
) {
    public static PushTokenResponse from(PushToken pushToken){
        return new PushTokenResponse(
                pushToken.getId()
        );
    }
}
