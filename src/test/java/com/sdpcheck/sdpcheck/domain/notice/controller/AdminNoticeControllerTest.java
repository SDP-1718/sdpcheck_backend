package com.sdpcheck.sdpcheck.domain.notice.controller;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeAuthor;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeListResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeResponse;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeService;
import com.sdpcheck.sdpcheck.global.exception.GlobalExceptionHandler;
import com.sdpcheck.sdpcheck.global.security.config.SecurityConfig;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import com.sdpcheck.sdpcheck.global.security.jwt.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminNoticeController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class,
        GlobalExceptionHandler.class})
class AdminNoticeControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean NoticeService service;

    @Test
    void onlyAdminCanListAndCreate() throws Exception {
        mvc.perform(get("/api/v1/admin/notices")).andExpect(status().isUnauthorized());
        authenticate(Role.MEMBER);
        String token = "Bearer " + jwtProvider.createAccessToken(1L);
        mvc.perform(get("/api/v1/admin/notices").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/notices").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void adminCreatesNoticeWithExpectedEnvelopeAndLocation() throws Exception {
        authenticate(Role.ADMIN);
        UUID id = UUID.randomUUID();
        var now = OffsetDateTime.parse("2026-10-08T12:00:00+09:00");
        when(service.create(any(), eq(1L))).thenReturn(new NoticeResponse(id, "공지", "본문",
                true, new NoticeAuthor("1", "관리자"), List.of(), now, now));

        mvc.perform(post("/api/v1/admin/notices").header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/admin/notices/" + id))
                .andExpect(jsonPath("$.code").value("NOTICE_CREATED"))
                .andExpect(jsonPath("$.result.isPinned").value(true))
                .andExpect(jsonPath("$.result.author.name").value("관리자"))
                .andExpect(jsonPath("$.result.images").isEmpty());
    }

    @Test
    void invalidBodyDoesNotCreateNotice() throws Exception {
        authenticate(Role.ADMIN);
        for (String body : List.of(
                "{\"title\":\" \",\"content\":\"본문\"}",
                "{\"title\":\"공지\",\"content\":\" \"}",
                "{\"title\":\"공지\",\"content\":\"본문\",\"isPinned\":null}",
                "{\"title\":\"공지\",\"content\":\"본문\",\"type\":\"SESSION\"}")) {
            mvc.perform(post("/api/v1/admin/notices").header(HttpHeaders.AUTHORIZATION, token())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void adminSeesEmptyNoticeList() throws Exception {
        authenticate(Role.ADMIN);
        when(service.list()).thenReturn(new NoticeListResponse(null, List.of()));
        mvc.perform(get("/api/v1/admin/notices").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOTICE_LIST_OK"))
                .andExpect(jsonPath("$.result.pinnedNotice").value((Object) null))
                .andExpect(jsonPath("$.result.items").isEmpty());
    }

    private void authenticate(Role role) {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(
                new Member("notice-admin", "hash", "관리자", 17, role)));
    }

    private String token() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }

    private String validBody() {
        return "{\"title\":\"공지\",\"content\":\"본문\",\"isPinned\":true,\"imageIds\":[]}";
    }
}
