package com.sdpcheck.sdpcheck.domain.notification;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.sdpcheck.sdpcheck.domain.notice.dto.request.CreateNoticeRequest;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeService;
import com.sdpcheck.sdpcheck.domain.session.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "push.enabled=true")
@ActiveProfiles("test")
@Testcontainers
@Transactional
class PushNotificationIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    @MockitoBean FirebaseApp firebaseApp;
    @MockitoBean FirebaseMessaging messaging;
    @MockitoBean PushOutboxWorker scheduledWorker;
    @Autowired JdbcTemplate jdbc;
    @Autowired PushNotificationService notifications;
    @Autowired PushOutboxRepository outbox;
    @Autowired NoticeService notices;
    @Autowired SessionService sessions;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void noticeCreationQueuesOnlyOptedInMembersAndAllTheirDevices() {
        long admin = member("ADMIN", true, true);
        long enabled = member("MEMBER", true, true);
        long disabled = member("MEMBER", false, true);
        token(enabled); token(enabled); token(disabled);
        var notice = notices.create(new CreateNoticeRequest("공지 제목", "본문", false, List.of()), admin);
        assertThat(count(notice.id())).isEqualTo(2);
        notifications.noticeCreated(notice.id(), "공지 제목");
        assertThat(count(notice.id())).isEqualTo(2);
        assertThat(outbox.lockDue()).allSatisfy(d -> {
            assertThat(d.type()).isEqualTo("NEW_NOTICE");
            assertThat(d.body()).isEqualTo("공지 제목");
            assertThat(d.eligible()).isTrue();
        });
    }

    @Test
    void sessionStartQueuesOnlyOptedInAttendanceTargets() {
        long target = member("MEMBER", true, true);
        long disabled = member("MEMBER", true, false);
        long outsider = member("MEMBER", true, true);
        token(target); token(disabled); token(outsider);
        UUID session = UUID.randomUUID();
        jdbc.update("INSERT INTO study_sessions(id,title,starts_at) VALUES (?, '세션', now())", session);
        jdbc.update("INSERT INTO session_attendance_targets VALUES (?, ?), (?, ?)", session, target, session, disabled);
        sessions.startSession(session, outsider);
        assertThat(count(session)).isEqualTo(1);
        assertThat(outbox.lockDue().getFirst().memberId()).isEqualTo(target);
        assertThatThrownBy(() -> sessions.startSession(session, outsider)).isInstanceOf(RuntimeException.class);
        assertThat(count(session)).isEqualTo(1);
    }

    @Test
    void preferenceAndOwnershipAreRecheckedAndExpiredNotificationsAreSkipped() {
        long first = member("MEMBER", true, true);
        long second = member("MEMBER", true, true);
        long firstToken = token(first);
        long secondToken = token(second);
        notifications.noticeCreated(UUID.randomUUID(), "공지");
        jdbc.update("UPDATE members SET new_notice_enabled=false WHERE id=?", first);
        jdbc.update("UPDATE push_tokens SET member_id=? WHERE id=?", first, secondToken);
        assertThat(outbox.lockDue()).hasSize(2).allMatch(d -> !d.eligible());
        jdbc.update("UPDATE members SET new_notice_enabled=true WHERE id=?", first);
        jdbc.update("UPDATE push_outbox SET created_at=now()-interval '25 hours'");
        assertThat(outbox.lockDue()).allMatch(d -> !d.eligible());
        outbox.deleteUnregisteredToken(new PushDelivery(0, "NEW_NOTICE", UUID.randomUUID(),
                firstToken, second, "wrong-token", "title", "body", 0, true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_tokens", Integer.class)).isEqualTo(2);
    }

    @Test
    void rollingBackBusinessOperationAlsoRollsBackQueuedNotifications() {
        UUID resource = UUID.randomUUID();
        var transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // The fixture and queued work are both rolled back in an independent transaction.
        transaction.executeWithoutResult(status -> {
            long recipient = member("MEMBER", true, true);
            token(recipient);
            notifications.noticeCreated(resource, "공지");
            assertThat(count(resource)).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(count(resource)).isZero();
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void anotherWorkerSkipsLockedRowsAndCanClaimThemAfterRollback() {
        var transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        long recipient = transaction.execute(status -> {
            long member = member("MEMBER", true, true);
            token(member);
            notifications.noticeCreated(UUID.randomUUID(), "공지");
            return member;
        });
        try {
            transaction.executeWithoutResult(status -> {
                assertThat(outbox.lockDue()).hasSize(1);
                transaction.executeWithoutResult(other -> assertThat(outbox.lockDue()).isEmpty());
                status.setRollbackOnly();
            });
            transaction.executeWithoutResult(status -> assertThat(outbox.lockDue()).hasSize(1));
        } finally {
            jdbc.update("DELETE FROM members WHERE id=?", recipient);
        }
    }

    private long member(String role, boolean notice, boolean session) {
        return jdbc.queryForObject("""
                INSERT INTO members(role,login_id,password,name,generation,new_notice_enabled,session_start_enabled)
                VALUES (?, ?, 'hash', '테스트', 17, ?, ?) RETURNING id
                """, Long.class, role, UUID.randomUUID().toString(), notice, session);
    }

    private long token(long member) {
        return jdbc.queryForObject("INSERT INTO push_tokens(member_id,token) VALUES (?,?) RETURNING id",
                Long.class, member, "test-token-" + UUID.randomUUID());
    }

    private int count(UUID resource) {
        return jdbc.queryForObject("SELECT count(*) FROM push_outbox WHERE resource_id=?", Integer.class, resource);
    }
}
