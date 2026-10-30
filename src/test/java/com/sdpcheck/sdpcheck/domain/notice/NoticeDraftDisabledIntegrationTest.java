package com.sdpcheck.sdpcheck.domain.notice;

import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "notice.ai-draft.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class NoticeDraftDisabledIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;

    @Test
    void configurationDisablesFeatureWithoutSavingQuotaOrNotices() throws Exception {
        long member = jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('ADMIN', 'disabled-draft-admin', 'hash', '관리자', 17) RETURNING id
                """, Long.class);
        mvc.perform(post("/api/v1/admin/notices/ai-drafts")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\"메모\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FEATURE_UNAVAILABLE"))
                .andExpect(jsonPath("$.result").value((Object) null));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notice_draft_requests", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notices", Integer.class));
    }
}
