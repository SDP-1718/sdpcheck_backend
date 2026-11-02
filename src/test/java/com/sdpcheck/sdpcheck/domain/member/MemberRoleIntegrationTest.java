package com.sdpcheck.sdpcheck.domain.member;

import com.sdpcheck.sdpcheck.domain.auth.dto.request.OnboardingRequest;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.auth.service.AuthService;
import com.sdpcheck.sdpcheck.domain.member.dto.request.NotificationSettingsRequest;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.exception.MemberErrorCode;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberCommandService;
import com.sdpcheck.sdpcheck.domain.member.service.MemberService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.config.import=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class MemberRoleIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired MemberRepository members;
    @Autowired AdminMemberCommandService command;
    @Autowired MemberService memberService;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JwtProvider jwt;
    @Autowired MockMvc mvc;

    private long admin;
    private long otherAdmin;
    private long member;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE members, semesters RESTART IDENTITY CASCADE");
        admin = createMember("admin", Role.ADMIN);
        otherAdmin = createMember("other-admin", Role.ADMIN);
        member = createMember("member", Role.MEMBER);
    }

    @Test
    void persistsRoleChangesWithoutChangingProfilePreferencesOrSemesterMembership() {
        UUID semester = createSemester();
        jdbc.update("INSERT INTO semester_members (semester_id, member_id) VALUES (?, ?)", semester, member);
        memberService.updateNotificationSettings(member, new NotificationSettingsRequest(false, false));

        var promoted = command.updateMemberRole(admin, member, Role.ADMIN);
        assertEquals(member, promoted.memberId());
        assertEquals("member", promoted.name());
        assertEquals(18, promoted.generation());
        assertEquals(Role.ADMIN, promoted.role());
        assertEquals(Role.ADMIN, members.findById(member).orElseThrow().getRole());

        String version = rowVersion(member);
        assertEquals(promoted, command.updateMemberRole(admin, member, Role.ADMIN));
        assertEquals(version, rowVersion(member));
        command.updateMemberRole(admin, member, Role.MEMBER);
        var saved = members.findById(member).orElseThrow();
        assertEquals(Role.MEMBER, saved.getRole());
        assertEquals("hash", saved.getPassword());
        assertFalse(saved.isNewNoticeEnabled());
        assertFalse(saved.isSessionStartEnabled());
        assertTrue(saved.isOnboardingCompleted());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM semester_members WHERE member_id = ?", Integer.class, member));
    }

    @Test
    void rejectsMissingIncompleteAndSelfTargetsWithoutChangingStoredRoles() {
        long pending = members.saveAndFlush(Member.createKakaoMember(12345L)).getId();
        assertEquals(MemberErrorCode.MEMBER_NOT_FOUND,
                assertThrows(BusinessException.class, () -> command.updateMemberRole(admin, 999L, Role.ADMIN)).getErrorCode());
        assertEquals(MemberErrorCode.MEMBER_ONBOARDING_INCOMPLETE,
                assertThrows(BusinessException.class, () -> command.updateMemberRole(admin, pending, Role.ADMIN)).getErrorCode());
        for (Role role : Role.values()) {
            assertEquals(MemberErrorCode.SELF_ROLE_CHANGE_NOT_ALLOWED,
                    assertThrows(BusinessException.class, () -> command.updateMemberRole(admin, admin, role)).getErrorCode());
        }
        assertNull(members.findById(pending).orElseThrow().getRole());
        assertEquals(2, adminCount());
    }

    @Test
    void rejectsRemovalOfTheLastAdmin() {
        command.updateMemberRole(admin, otherAdmin, Role.MEMBER);
        assertEquals(MemberErrorCode.LAST_ADMIN_REMOVAL_NOT_ALLOWED,
                assertThrows(BusinessException.class, () -> command.updateMemberRole(admin, admin, Role.MEMBER)).getErrorCode());
        assertEquals(1, adminCount());
    }

    @Test
    void realTokensGainAndLoseAdminAccessOnTheNextRequestWithoutBeingReissued() throws Exception {
        String memberToken = bearer(member);
        mvc.perform(get("/api/v1/admin/members/generations").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/admin/members/{memberId}/role", member)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.memberId").value(member))
                .andExpect(jsonPath("$.result.role").value("ADMIN"));
        mvc.perform(get("/api/v1/admin/members/generations").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk());

        command.updateMemberRole(admin, member, Role.MEMBER);
        mvc.perform(patch("/api/v1/admin/members/{memberId}/role", otherAdmin)
                        .header(HttpHeaders.AUTHORIZATION, memberToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertEquals(Role.ADMIN, members.findById(otherAdmin).orElseThrow().getRole());
    }

    @Test
    void simultaneousMutualDemotionsLeaveOneAdminAndRecheckTheWaitingActorsRole() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                var result = command.updateMemberRole(admin, otherAdmin, Role.MEMBER);
                firstUpdated.countDown();
                await(commitFirst);
                return result;
            }));
            assertTrue(firstUpdated.await(10, TimeUnit.SECONDS));
            var second = executor.submit(() -> assertThrows(BusinessException.class,
                    () -> command.updateMemberRole(otherAdmin, admin, Role.MEMBER)).getErrorCode());

            awaitRoleLockWaiter();
            assertFalse(second.isDone());
            commitFirst.countDown();
            assertEquals(Role.MEMBER, first.get(10, TimeUnit.SECONDS).role());
            assertEquals(AuthErrorCode.ACCESS_DENIED, second.get(10, TimeUnit.SECONDS));
            assertEquals(1, adminCount());
            assertEquals(Role.ADMIN, members.findById(admin).orElseThrow().getRole());
        } finally {
            commitFirst.countDown();
            shutdown(executor);
        }
    }

    @Test
    void aStaleNotificationUpdateDoesNotRestoreRevokedAdminRights() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch updateSettings = new CountDownLatch(1);
        try {
            var notification = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                assertEquals(Role.ADMIN, members.findById(otherAdmin).orElseThrow().getRole());
                loaded.countDown();
                await(updateSettings);
                return memberService.updateNotificationSettings(otherAdmin, new NotificationSettingsRequest(false, null));
            }));
            assertTrue(loaded.await(10, TimeUnit.SECONDS));
            command.updateMemberRole(admin, otherAdmin, Role.MEMBER);
            updateSettings.countDown();
            notification.get(10, TimeUnit.SECONDS);
            var updated = members.findById(otherAdmin).orElseThrow();
            assertEquals(Role.MEMBER, updated.getRole());
            assertFalse(updated.isSessionStartEnabled());
            assertTrue(updated.isNewNoticeEnabled());
        } finally {
            updateSettings.countDown();
            shutdown(executor);
        }
    }

    @Test
    void concurrentOnboardingCannotOverwriteAnAlreadyCompletedMembersRole() throws Exception {
        createSemester();
        long pending = members.saveAndFlush(Member.createKakaoMember(45678L)).getId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                var result = auth.completeOnboarding(pending, new OnboardingRequest("신규 회원", 19, "SDPADMIN2026"));
                firstUpdated.countDown();
                await(commitFirst);
                return result;
            }));
            assertTrue(firstUpdated.await(10, TimeUnit.SECONDS));
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return assertThrows(BusinessException.class,
                        () -> auth.completeOnboarding(pending, new OnboardingRequest("다른 이름", 20, "SDP2026"))).getErrorCode();
            });
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
            awaitRowLockWaiter();
            commitFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertEquals(AuthErrorCode.ONBOARDING_ALREADY_COMPLETED, second.get(10, TimeUnit.SECONDS));
            Member completed = members.findById(pending).orElseThrow();
            assertEquals(Role.ADMIN, completed.getRole());
            assertEquals("신규 회원", completed.getName());
            assertEquals(19, completed.getGeneration());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM semester_members WHERE member_id = ?", Integer.class, pending));
        } finally {
            commitFirst.countDown();
            shutdown(executor);
        }
    }

    @Test
    void swaggerPublishesThePatchOperationWithBearerAuthenticationAndRoleSchema() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/members/{memberId}/role'].patch.operationId").value("updateMemberRole"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/members/{memberId}/role'].patch.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.components.schemas.MemberRoleUpdateRequest.required[0]").value("role"))
                .andExpect(jsonPath("$.components.schemas.MemberRoleUpdateRequest.properties.role.enum.length()").value(2));
    }

    private long createMember(String loginId, Role role) {
        return members.saveAndFlush(new Member(loginId, "hash", loginId, 18, role)).getId();
    }

    private UUID createSemester() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO semesters (id, name, starts_on, ends_on, is_current, period_code)
                VALUES (?, '2026 하반기', '2026-07-01', '2026-12-31', true, '2026-2H')
                """, id);
        return id;
    }

    private int adminCount() {
        return jdbc.queryForObject("SELECT count(*) FROM members WHERE role = 'ADMIN' AND onboarding_completed", Integer.class);
    }

    private String rowVersion(long id) {
        return jdbc.queryForObject("SELECT xmin::text FROM members WHERE id = ?", String.class, id);
    }

    private String bearer(long id) {
        return "Bearer " + jwt.createAccessToken(id);
    }

    private void awaitRoleLockWaiter() throws InterruptedException {
        awaitBlockedQuery("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND objid::bigint = 2026103056 AND NOT granted");
    }

    private void awaitRowLockWaiter() throws InterruptedException {
        awaitBlockedQuery("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock' AND query ILIKE '%members%'");
    }

    private void awaitBlockedQuery(String query) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject(query, Integer.class) > 0) {
                return;
            }
            Thread.sleep(20);
        }
        fail("Concurrent request did not wait for the PostgreSQL lock");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
}
