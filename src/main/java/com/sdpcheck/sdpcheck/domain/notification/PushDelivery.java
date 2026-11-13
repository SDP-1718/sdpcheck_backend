package com.sdpcheck.sdpcheck.domain.notification;

import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import java.util.UUID;

public record PushDelivery(long id, String type, UUID resourceId, long tokenId, long memberId,
                           String token, String title, String body, int attempts, boolean eligible) {
    public Message toMessage() {
        String idKey = "NEW_NOTICE".equals(type) ? "noticeId" : "sessionId";
        return Message.builder().setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putData("type", type)
                .putData(idKey, resourceId.toString())
                .putData("eventId", type + ":" + resourceId)
                .build();
    }

    // Avoid accidentally including device tokens in logs.
    @Override
    public String toString() {
        return "PushDelivery[id=" + id + ", type=" + type + ", resourceId=" + resourceId + "]";
    }
}
