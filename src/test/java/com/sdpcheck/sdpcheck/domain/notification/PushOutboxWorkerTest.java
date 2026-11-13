package com.sdpcheck.sdpcheck.domain.notification;

import com.google.firebase.messaging.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class PushOutboxWorkerTest {
    private final PushOutboxRepository repository = mock(PushOutboxRepository.class);
    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    private final PushOutboxWorker worker = new PushOutboxWorker(repository, messaging);

    @Test
    void ineligibleBatchDoesNotContactFirebase() {
        when(repository.lockDue()).thenReturn(List.of(delivery(1, 0, false)));
        worker.deliver();
        verify(repository).finish(1, "SKIPPED");
        verifyNoInteractions(messaging);
    }

    @Test
    void partialFailureRetriesOnlyTransientFailureAndPreservesResponseOrder() throws Exception {
        var first = delivery(1, 0, true);
        var second = delivery(2, 0, true);
        var third = delivery(3, 0, true);
        var fourth = delivery(4, 0, true);
        when(repository.lockDue()).thenReturn(List.of(first, second, third, fourth));
        var success = mock(SendResponse.class);
        when(success.isSuccessful()).thenReturn(true);
        var batch = mock(BatchResponse.class);
        var results = List.of(success,
                failure(MessagingErrorCode.UNAVAILABLE), failure(MessagingErrorCode.UNREGISTERED),
                failure(MessagingErrorCode.INVALID_ARGUMENT));
        when(batch.getResponses()).thenReturn(results);
        when(messaging.sendEach(anyList())).thenReturn(batch);
        worker.deliver();
        verify(repository).finish(1, "SENT");
        verify(repository).retry(eq(second), longThat(delay -> delay >= 60 && delay < 90));
        verify(repository).deleteUnregisteredToken(third);
        verify(repository).finish(4, "FAILED");
        verify(repository, never()).retry(eq(first), anyLong());
        verify(repository, never()).deleteUnregisteredToken(fourth);
    }

    @Test
    void wholeBatchTransientFailureStopsAfterFiveAttempts() throws Exception {
        var delivery = delivery(5, 4, true);
        when(repository.lockDue()).thenReturn(List.of(delivery));
        var exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INTERNAL);
        when(messaging.sendEach(anyList())).thenThrow(exception);
        worker.deliver();
        verify(repository).finish(5, "FAILED");
        verify(repository, never()).retry(any(), anyLong());
    }

    private SendResponse failure(MessagingErrorCode code) {
        var response = mock(SendResponse.class);
        var exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        when(response.getException()).thenReturn(exception);
        return response;
    }

    @Test
    void retryAfterSupportsSecondsAndHttpDates() {
        var now = java.time.Instant.parse("2026-11-13T00:00:00Z");
        assertThat(PushOutboxWorker.parseRetryAfter("3600", now)).isEqualTo(3600);
        assertThat(PushOutboxWorker.parseRetryAfter("Fri, 13 Nov 2026 01:00:00 GMT", now)).isEqualTo(3601);
        assertThat(PushOutboxWorker.parseRetryAfter("invalid", now)).isZero();
    }

    @Test
    void payloadHasStableRoutingAndTokenIsExcludedFromLogRepresentation() {
        var delivery = delivery(7, 0, true);
        var json = com.google.gson.JsonParser.parseString(new com.google.gson.Gson()
                .toJson(delivery.toMessage())).getAsJsonObject();
        assertThat(json.get("token").getAsString()).isEqualTo(delivery.token());
        var data = json.getAsJsonObject("data");
        assertThat(data.get("type").getAsString()).isEqualTo("NEW_NOTICE");
        assertThat(data.get("noticeId").getAsString()).isEqualTo(delivery.resourceId().toString());
        assertThat(data.get("eventId").getAsString()).isEqualTo("NEW_NOTICE:" + delivery.resourceId());
        assertThat(delivery.toString()).doesNotContain(delivery.token());
    }

    private PushDelivery delivery(long id, int attempts, boolean eligible) {
        return new PushDelivery(id, "NEW_NOTICE", UUID.randomUUID(), id, 1,
                "private-device-token-" + id, "title", "body", attempts, eligible);
    }
}
