package com.sdpcheck.sdpcheck.domain.session;

import com.sdpcheck.sdpcheck.domain.session.dto.request.CheckInReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.CheckInResDTO;
import com.sdpcheck.sdpcheck.domain.session.service.SessionCheckInService;
import com.sdpcheck.sdpcheck.domain.session.service.SessionQrTokenProvider;
import com.sdpcheck.sdpcheck.domain.session.service.SessionService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SessionCheckInConcurrencyIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SessionCheckInService checkInService;
    @Autowired SessionService sessionService;
    @Autowired SessionQrTokenProvider qrTokenProvider;

    private long memberId;

    @BeforeEach
    void setUp() {
        memberId = insertMember();
    }

    @Test
    void concurrentDuplicateCheckInsYieldOneCreationAndOneConflict() throws Exception {
        UUID sessionId = insertInProgressSession();
        registerTarget(sessionId, memberId);
        String qrToken = qrToken(sessionId);

        try (Connection sessionLock = lockSession(sessionId)) {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<Attempt> first = executor.submit(checkInTask(sessionId, qrToken, ready, start));
                Future<Attempt> second = executor.submit(checkInTask(sessionId, qrToken, ready, start));

                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                awaitBlockedDatabaseLock();
                sessionLock.commit();

                List<Attempt> attempts = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
                assertEquals(1, attempts.stream().filter(Attempt::succeeded).count());
                assertEquals(1, attempts.stream().filter(attempt -> "CHECK_IN409_1".equals(attempt.errorCode())).count());
                assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM attendances WHERE session_id = ? AND member_id = ?",
                        Integer.class, sessionId, memberId));
                assertEquals("PRESENT", jdbc.queryForObject(
                        "SELECT status FROM attendances WHERE session_id = ? AND member_id = ?",
                        String.class, sessionId, memberId));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void endingCommitsBeforeCheckInPreventsNewPresentAttendance() throws Exception {
        UUID sessionId = insertInProgressSession();
        registerTarget(sessionId, memberId);
        String qrToken = qrToken(sessionId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch endApplied = new CountDownLatch(1);
        CountDownLatch allowEndCommit = new CountDownLatch(1);
        try {
            TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
            Future<Void> ending = executor.submit(() -> outerTransaction.execute(status -> {
                sessionService.endSession(sessionId);
                endApplied.countDown();
                awaitLatch(allowEndCommit, "end transaction was not allowed to commit");
                return null;
            }));

            assertTrue(endApplied.await(5, TimeUnit.SECONDS));
            Future<Attempt> checking = executor.submit(() -> invokeCheckIn(sessionId, qrToken));
            awaitBlockedDatabaseLock();
            allowEndCommit.countDown();

            ending.get(10, TimeUnit.SECONDS);
            Attempt afterEnd = checking.get(10, TimeUnit.SECONDS);
            assertFalse(afterEnd.succeeded());
            assertEquals("SESSION409_1", afterEnd.errorCode());
            assertEquals("ABSENT", jdbc.queryForObject(
                    "SELECT status FROM attendances WHERE session_id = ? AND member_id = ?",
                    String.class, sessionId, memberId));
            assertEquals(0, jdbc.queryForObject("""
                    SELECT count(*)
                    FROM attendances
                    WHERE session_id = ? AND member_id = ? AND status = 'PRESENT'
                    """, Integer.class, sessionId, memberId));
        } finally {
            allowEndCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void checkInCommittedBeforeEndIsNotOverwrittenByAbsentMarking() throws Exception {
        UUID sessionId = insertInProgressSession();
        registerTarget(sessionId, memberId);
        String qrToken = qrToken(sessionId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch checkInApplied = new CountDownLatch(1);
        CountDownLatch allowCheckInCommit = new CountDownLatch(1);
        try {
            TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
            Future<CheckInResDTO> checkIn = executor.submit(() -> outerTransaction.execute(status -> {
                CheckInResDTO response = checkInService.checkIn(sessionId, memberId, new CheckInReqDTO(qrToken));
                checkInApplied.countDown();
                awaitLatch(allowCheckInCommit, "check-in transaction was not allowed to commit");
                return response;
            }));

            assertTrue(checkInApplied.await(5, TimeUnit.SECONDS));
            Future<?> ending = executor.submit(() -> sessionService.endSession(sessionId));
            awaitBlockedDatabaseLock();
            allowCheckInCommit.countDown();

            CheckInResDTO response = checkIn.get(10, TimeUnit.SECONDS);
            ending.get(10, TimeUnit.SECONDS);
            Long checkInId = jdbc.queryForObject(
                    "SELECT id FROM attendances WHERE session_id = ? AND member_id = ?", Long.class, sessionId, memberId);
            OffsetDateTime checkedInAt = jdbc.queryForObject(
                    "SELECT checked_in_at FROM attendances WHERE session_id = ? AND member_id = ?",
                    OffsetDateTime.class, sessionId, memberId);
            assertNotNull(checkInId);
            assertNotNull(checkedInAt);
            assertEquals(response.checkInId(), checkInId);
            assertEquals(response.attendanceStatus().name(), jdbc.queryForObject(
                    "SELECT status FROM attendances WHERE session_id = ? AND member_id = ?",
                    String.class, sessionId, memberId));
            assertEquals(response.checkedInAt().toInstant(), checkedInAt.toInstant());
        } finally {
            allowCheckInCommit.countDown();
            executor.shutdownNow();
        }
    }

    private Callable<Attempt> checkInTask(UUID sessionId, String qrToken, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("check-in task did not receive start signal");
            }
            return invokeCheckIn(sessionId, qrToken);
        };
    }

    private Attempt invokeCheckIn(UUID sessionId, String qrToken) {
        try {
            checkInService.checkIn(sessionId, memberId, new CheckInReqDTO(qrToken));
            return new Attempt(true, null);
        } catch (BusinessException exception) {
            return new Attempt(false, exception.getErrorCode().getCode());
        }
    }

    private void awaitLatch(CountDownLatch latch, String message) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, exception);
        }
    }

    private Connection lockSession(UUID sessionId) throws Exception {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM study_sessions WHERE id = ? FOR UPDATE")) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
            }
        }
        return connection;
    }

    private void awaitBlockedDatabaseLock() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(10);
        }
        fail("PESSIMISTIC_WRITE session lock did not block a competing transaction");
    }

    private UUID insertInProgressSession() {
        UUID sessionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO study_sessions (id, title, starts_at, ends_at, location, started_at)
                VALUES (?, '동시성 세션', now() + INTERVAL '1 hour', now() + INTERVAL '2 hours', '테스트실',
                        now() - INTERVAL '1 minute')
                """, sessionId);
        return sessionId;
    }

    private void registerTarget(UUID sessionId, long targetMemberId) {
        jdbc.update("INSERT INTO session_attendance_targets (session_id, member_id) VALUES (?, ?)",
                sessionId, targetMemberId);
    }

    private String qrToken(UUID sessionId) {
        return qrTokenProvider.createToken(sessionId, OffsetDateTime.now().plusMinutes(2));
    }

    private long insertMember() {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('MEMBER', ?, 'hash', '동시성 테스트', 17)
                RETURNING id
                """, Long.class, "checkin-concurrency-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private record Attempt(boolean succeeded, String errorCode) {
    }
}
