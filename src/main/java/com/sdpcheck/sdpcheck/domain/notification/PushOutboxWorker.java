package com.sdpcheck.sdpcheck.domain.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
@ConditionalOnProperty(name = "push.enabled", havingValue = "true")
public class PushOutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(PushOutboxWorker.class);
    private static final int MAX_ATTEMPTS = 5;
    private final PushOutboxRepository repository;
    private final FirebaseMessaging messaging;

    public PushOutboxWorker(PushOutboxRepository repository, FirebaseMessaging messaging) {
        this.repository = repository;
        this.messaging = messaging;
    }

    @Scheduled(fixedDelayString = "${push.poll-delay-ms:5000}")
    @Transactional
    public void deliver() {
        // Keep row locks until results are recorded so other instances cannot send this batch.
        var deliveries = new ArrayList<PushDelivery>();
        for (var delivery : repository.lockDue()) {
            if (delivery.eligible()) deliveries.add(delivery);
            else repository.finish(delivery.id(), "SKIPPED");
        }
        if (deliveries.isEmpty()) return;
        try {
            var response = messaging.sendEach(deliveries.stream().map(PushDelivery::toMessage).toList());
            for (int i = 0; i < deliveries.size(); i++) {
                var result = response.getResponses().get(i);
                if (result.isSuccessful()) repository.finish(deliveries.get(i).id(), "SENT");
                else handleFailure(deliveries.get(i), result.getException());
            }
        } catch (FirebaseMessagingException e) {
            for (var delivery : deliveries) handleFailure(delivery, e);
        }
    }

    private void handleFailure(PushDelivery delivery, FirebaseMessagingException error) {
        var code = error.getMessagingErrorCode();
        if (code == MessagingErrorCode.UNREGISTERED) {
            repository.deleteUnregisteredToken(delivery);
            repository.finish(delivery.id(), "FAILED");
        } else if ((code == MessagingErrorCode.UNAVAILABLE || code == MessagingErrorCode.INTERNAL
                || code == MessagingErrorCode.QUOTA_EXCEEDED) && delivery.attempts() + 1 < MAX_ATTEMPTS) {
            long delay = (60L << delivery.attempts()) + ThreadLocalRandom.current().nextLong(30);
            delay = Math.max(delay, retryAfterSeconds(error));
            repository.retry(delivery, delay);
        } else {
            repository.finish(delivery.id(), "FAILED");
        }
        // Firebase exception messages can contain registration tokens; log only IDs and error codes.
        log.warn("Push delivery failed: outboxId={}, code={}", delivery.id(), code);
    }

    private long retryAfterSeconds(FirebaseMessagingException error) {
        if (error.getHttpResponse() == null) return 0;
        for (var header : error.getHttpResponse().getHeaders().entrySet()) {
            if (!"Retry-After".equalsIgnoreCase(header.getKey())) continue;
            Object value = header.getValue();
            if (value instanceof java.util.List<?> values) {
                if (values.isEmpty()) return 0;
                value = values.getFirst();
            }
            return parseRetryAfter(String.valueOf(value), Instant.now());
        }
        return 0;
    }

    static long parseRetryAfter(String value, Instant now) {
        try {
            return Math.max(0, Long.parseLong(value.strip()));
        } catch (NumberFormatException ignored) {
            try {
                return Math.max(0, Duration.between(now,
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds() + 1);
            } catch (java.time.format.DateTimeParseException invalidHeader) {
                return 0;
            }
        }
    }
}
