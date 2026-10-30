package com.sdpcheck.sdpcheck.domain.notice;

import com.sdpcheck.sdpcheck.domain.notice.service.NoticeDraftRateLimiter;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeDraftService;
import com.sdpcheck.sdpcheck.global.exception.RateLimitExceededException;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class NoticeDraftIntegrationTest {
    private static final String URL = "/api/v1/admin/notices/ai-drafts";
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    @Autowired NoticeDraftService service;
    @Autowired NoticeDraftRateLimiter limiter;
    @Autowired JdbcTemplate jdbc;
    @Autowired NamedParameterJdbcTemplate namedJdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;

    @Test
    void differentMemosReturnSameMockWithoutSavingNotices() throws Exception {
        long member = admin();
        long before = jdbc.queryForObject("SELECT count(*) FROM notices", Long.class);
        for (String memo : List.of("첫 번째 메모", "전혀 다른 메모")) {
            mvc.perform(post(URL).header("Authorization", token(member)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memo\":\"" + memo + "\"}"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.result.title").value("정기 세션 안내"))
                    .andExpect(jsonPath("$.result.content").value("10월 2일 금요일 오후 7시에 정기 세션이 진행됩니다."))
                    .andExpect(jsonPath("$.result.mode").value("MOCK"));
        }
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM notices", Long.class));
        assertEquals(2, quotaRows(member));
    }

    @Test
    void eleventhRequestReturns429AndAnotherAdminHasIndependentQuota() throws Exception {
        long member = admin();
        for (int i = 0; i < 10; i++) { service.generate(member); }
        var response = mvc.perform(post(URL).header("Authorization", token(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"메모\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn().getResponse();
        int retryAfter = Integer.parseInt(response.getHeader("Retry-After"));
        assertTrue(retryAfter >= 1 && retryAfter <= 60);
        assertEquals(10, quotaRows(member));
        long anotherAdmin = admin();
        assertNotNull(service.generate(anotherAdmin));
        assertEquals(1, quotaRows(anotherAdmin));
    }

    @Test
    void rollingWindowExpiresOnlyOldRequestsAndNewLimiterSharesExistingQuota() {
        long member = admin();
        jdbc.update("""
                INSERT INTO notice_draft_requests (member_id, requested_at)
                SELECT ?, clock_timestamp() - interval '50 seconds' FROM generate_series(1, 9)
                """, member);
        jdbc.update("INSERT INTO notice_draft_requests VALUES (?, clock_timestamp() - interval '2 seconds')", member);
        var newLimiter = new NoticeDraftRateLimiter(namedJdbc);
        var denied = new TransactionTemplate(transactions).execute(status -> newLimiter.acquire(member));
        assertFalse(denied.allowed());
        assertTrue(denied.retryAfterSeconds() >= 1 && denied.retryAfterSeconds() <= 10);
        jdbc.update("""
                UPDATE notice_draft_requests SET requested_at = clock_timestamp() - interval '61 seconds'
                WHERE member_id = ? AND requested_at < clock_timestamp() - interval '20 seconds'
                """, member);
        assertTrue(limiter.acquire(member).allowed());
        assertEquals(2, quotaRows(member));
    }

    @Test
    void concurrentRequestsCannotExceedTenSuccessfulReservations() throws Exception {
        long member = admin();
        int count = 12;
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<String> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(count)) {
            List<Future<String>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                tasks.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("start timeout"); }
                    try {
                        service.generate(member);
                        return "OK";
                    } catch (RateLimitExceededException exception) {
                        return exception.getErrorCode().getCode();
                    }
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (var task : tasks) { results.add(task.get(10, TimeUnit.SECONDS)); }
        } finally {
            start.countDown();
        }
        assertEquals(10, results.stream().filter("OK"::equals).count());
        assertEquals(2, results.stream().filter("RATE_LIMITED"::equals).count());
        assertEquals(10, quotaRows(member));
    }

    @Test
    void invalidRequestsDoNotConsumeQuota() throws Exception {
        long member = admin();
        for (int i = 0; i < 11; i++) {
            mvc.perform(post(URL).header("Authorization", token(member)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memo\":\" \"}"))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, quotaRows(member));
        assertNotNull(service.generate(member));
    }

    @Test
    void deletingMemberAlsoDeletesQuotaMetadata() {
        long member = admin();
        service.generate(member);
        jdbc.update("DELETE FROM members WHERE id = ?", member);
        assertEquals(0, quotaRows(member));
    }

    @Test
    void swaggerDocumentsContractAndRateLimitHeader() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/notices/ai-drafts'].post.operationId").value("generateNoticeDraft"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/notices/ai-drafts'].post.summary").value("관리자 공지 초안 생성 (목업)"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/notices/ai-drafts'].post.responses['429'].headers['Retry-After']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/notices/ai-drafts'].post.responses['429'].content['application/json'].examples.rateLimited.value.isSuccess").value(false))
                .andExpect(jsonPath("$.paths['/api/v1/admin/notices/ai-drafts'].post.responses['429'].content['application/json'].examples.rateLimited.value.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/notices/ai-drafts'].post.responses['503']").exists())
                .andExpect(jsonPath("$.components.schemas.GenerateNoticeDraftRequest.required[0]").value("memo"))
                .andExpect(jsonPath("$.components.schemas.GenerateNoticeDraftRequest.properties.memo.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.GenerateNoticeDraftRequest.properties.memo.maxLength").value(2000))
                .andExpect(jsonPath("$.components.schemas.GenerateNoticeDraftRequest.additionalProperties").value(false))
                .andExpect(jsonPath("$.components.schemas.NoticeDraftResponse.properties.mode.enum[0]").value("MOCK"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
    }

    private long admin() {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('ADMIN', ?, 'hash', '관리자', 17) RETURNING id
                """, Long.class, "draft-" + UUID.randomUUID());
    }

    private int quotaRows(long member) {
        return jdbc.queryForObject("SELECT count(*) FROM notice_draft_requests WHERE member_id = ?", Integer.class, member);
    }

    private String token(long member) {
        return "Bearer " + jwt.createAccessToken(member);
    }
}
