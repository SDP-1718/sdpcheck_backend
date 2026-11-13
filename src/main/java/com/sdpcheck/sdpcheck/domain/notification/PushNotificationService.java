package com.sdpcheck.sdpcheck.domain.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PushNotificationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final boolean enabled;

    public PushNotificationService(NamedParameterJdbcTemplate jdbc,
                                   @Value("${push.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    // The outbox must commit or roll back together with the originating operation.
    @Transactional(propagation = Propagation.MANDATORY)
    public void noticeCreated(UUID noticeId, String noticeTitle) {
        if (!enabled) return;
        enqueue("NEW_NOTICE", noticeId, "새 공지가 등록되었어요", noticeTitle, """
                m.new_notice_enabled = true
                """);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sessionStarted(UUID sessionId, String sessionTitle) {
        if (!enabled) return;
        enqueue("SESSION_STARTED", sessionId, "세션이 시작되었어요", sessionTitle, """
                m.session_start_enabled = true AND EXISTS (
                    SELECT 1 FROM session_attendance_targets t
                    WHERE t.session_id = :resourceId AND t.member_id = m.id
                )
                """);
    }

    private void enqueue(String type, UUID resourceId, String title, String body, String condition) {
        jdbc.update("""
                INSERT INTO push_outbox (event_type, resource_id, token_id, member_id, title, body)
                SELECT :type, :resourceId, p.id, m.id, :title, :body
                FROM push_tokens p JOIN members m ON m.id = p.member_id
                WHERE m.onboarding_completed = true AND m.role IN ('MEMBER', 'ADMIN') AND
                """ + condition + " ON CONFLICT DO NOTHING",
                new MapSqlParameterSource("type", type).addValue("resourceId", resourceId)
                        .addValue("title", title).addValue("body", body));
    }
}
