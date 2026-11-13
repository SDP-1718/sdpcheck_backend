package com.sdpcheck.sdpcheck.domain.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PushOutboxRepository {
    private final JdbcTemplate jdbc;

    public PushOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PushDelivery> lockDue() {
        return jdbc.query("""
                SELECT o.*, p.token,
                    (p.member_id = o.member_id AND m.onboarding_completed = true
                     AND m.role IN ('MEMBER', 'ADMIN') AND
                     CASE WHEN o.event_type = 'NEW_NOTICE' THEN m.new_notice_enabled
                          ELSE m.session_start_enabled END
                     AND CASE WHEN o.event_type = 'NEW_NOTICE' THEN EXISTS (
                         SELECT 1 FROM notices n WHERE n.id = o.resource_id
                         AND n.deleted_at IS NULL AND n.is_published = true
                     ) ELSE EXISTS (
                         SELECT 1 FROM study_sessions s WHERE s.id = o.resource_id
                         AND s.deleted_at IS NULL AND s.cancelled_at IS NULL
                         AND s.is_published = true
                         AND s.started_at IS NOT NULL AND s.ended_at IS NULL
                     ) END
                     AND o.created_at > now() - CASE WHEN o.event_type = 'NEW_NOTICE'
                         THEN INTERVAL '24 hours' ELSE INTERVAL '1 hour' END) AS eligible
                FROM push_outbox o
                JOIN push_tokens p ON p.id = o.token_id
                JOIN members m ON m.id = o.member_id
                WHERE o.status = 'PENDING' AND o.next_attempt_at <= now()
                ORDER BY o.next_attempt_at, o.id
                LIMIT 100 FOR UPDATE OF o SKIP LOCKED
                """, (rs, row) -> new PushDelivery(rs.getLong("id"), rs.getString("event_type"),
                rs.getObject("resource_id", java.util.UUID.class), rs.getLong("token_id"),
                rs.getLong("member_id"), rs.getString("token"), rs.getString("title"),
                rs.getString("body"), rs.getInt("attempts"), rs.getBoolean("eligible")));
    }

    public void finish(long id, String status) {
        jdbc.update("UPDATE push_outbox SET status = ?, attempts = attempts + 1 WHERE id = ?", status, id);
    }

    public void retry(PushDelivery delivery, long delaySeconds) {
        jdbc.update("""
                UPDATE push_outbox SET attempts = attempts + 1,
                    next_attempt_at = now() + (? * INTERVAL '1 second')
                WHERE id = ?
                """, delaySeconds, delivery.id());
    }

    public void deleteUnregisteredToken(PushDelivery delivery) {
        jdbc.update("DELETE FROM push_tokens WHERE id = ? AND member_id = ? AND token = ?",
                delivery.tokenId(), delivery.memberId(), delivery.token());
    }
}
