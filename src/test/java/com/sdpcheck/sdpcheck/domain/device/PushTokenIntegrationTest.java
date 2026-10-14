package com.sdpcheck.sdpcheck.domain.device;

import com.sdpcheck.sdpcheck.domain.device.dto.request.PushTokenRequest;
import com.sdpcheck.sdpcheck.domain.device.service.PushTokenService;
import com.sdpcheck.sdpcheck.domain.device.exception.PushTokenErrorCode;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PushTokenIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    @Autowired PushTokenService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void repeatedRegistrationKeepsOneRowAndSameId() {
        Long memberId = member();
        var request = request();
        var first = service.addPushToken(memberId, request);
        assertThat(service.addPushToken(memberId, request).tokenId()).isEqualTo(first.tokenId());
        assertThat(count(request.token())).isEqualTo(1);
    }

    @Test
    void accountChangeTransfersOwnershipWithoutChangingId() {
        Long firstMember = member();
        Long secondMember = member();
        var request = request();
        Long id = service.addPushToken(firstMember, request).tokenId();
        assertThat(service.addPushToken(secondMember, request).tokenId()).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT member_id FROM push_tokens WHERE id = ?", Long.class, id))
                .isEqualTo(secondMember);
        assertThat(count(request.token())).isEqualTo(1);
    }

    @Test
    void oneMemberCanRegisterMultipleDevices() {
        Long memberId = member();
        service.addPushToken(memberId, request());
        service.addPushToken(memberId, request());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_tokens WHERE member_id = ?",
                Integer.class, memberId)).isEqualTo(2);
    }

    @Test
    void concurrentRegistrationsKeepOneIdAndOneRow() throws Exception {
        Long memberId = member();
        var request = request();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return service.addPushToken(memberId, request); });
            var second = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return service.addPushToken(memberId, request); });
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS).tokenId()).isEqualTo(second.get(20, TimeUnit.SECONDS).tokenId());
        }
        assertThat(count(request.token())).isEqualTo(1);
    }

    @Test
    void databaseEnforcesTokenConstraintsAndMemberForeignKey() {
        Long memberId = member();
        for (String invalid : List.of("", "a b", "a".repeat(513))) {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO push_tokens (member_id, token) VALUES (?, ?)", memberId, invalid))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        var request = request();
        service.addPushToken(memberId, request);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO push_tokens (member_id, token) VALUES (?, ?)", memberId, request.token()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO push_tokens (member_id, token) VALUES (?, ?)", -1L, request().token()))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM members WHERE id = ?", memberId);
        assertThat(count(request.token())).isZero();
    }

    @Test
    void deletionPreservesOtherDevicesAndRejectsOtherMembers() {
        Long owner = member();
        Long otherMember = member();
        var phone = request();
        var computer = request();
        Long phoneId = service.addPushToken(owner, phone).tokenId();
        service.addPushToken(owner, computer);

        assertThatThrownBy(() -> service.deletePushToken(otherMember, phoneId))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(PushTokenErrorCode.PUSH_TOKEN_NOT_FOUND));
        assertThat(count(phone.token())).isEqualTo(1);

        service.deletePushToken(owner, phoneId);
        assertThat(count(phone.token())).isZero();
        assertThat(count(computer.token())).isEqualTo(1);
        assertThatThrownBy(() -> service.deletePushToken(owner, phoneId))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(PushTokenErrorCode.PUSH_TOKEN_NOT_FOUND));
    }

    @Test
    void previousOwnerCannotDeleteTokenAfterAccountChange() {
        Long previousOwner = member();
        Long currentOwner = member();
        var request = request();
        Long id = service.addPushToken(previousOwner, request).tokenId();
        service.addPushToken(currentOwner, request);

        assertThatThrownBy(() -> service.deletePushToken(previousOwner, id))
                .isInstanceOf(BusinessException.class);
        assertThat(count(request.token())).isEqualTo(1);
        service.deletePushToken(currentOwner, id);
        assertThat(count(request.token())).isZero();
    }

    private PushTokenRequest request() {
        return new PushTokenRequest(UUID.randomUUID().toString());
    }

    private int count(String token) {
        return jdbc.queryForObject("SELECT count(*) FROM push_tokens WHERE token = ?", Integer.class, token);
    }

    private Long member() {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('MEMBER', ?, 'hash', '회원', 17) RETURNING id
                """, Long.class, "push-" + UUID.randomUUID());
    }
}
