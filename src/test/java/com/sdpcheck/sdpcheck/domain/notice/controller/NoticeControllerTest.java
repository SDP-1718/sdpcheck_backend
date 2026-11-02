package com.sdpcheck.sdpcheck.domain.notice.controller;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeAuthor;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeDetailResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticePageResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeSummary;
import com.sdpcheck.sdpcheck.domain.notice.exception.NoticeErrorCode;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.GlobalExceptionHandler;
import com.sdpcheck.sdpcheck.global.security.config.SecurityConfig;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import com.sdpcheck.sdpcheck.global.security.jwt.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NoticeController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class,
        GlobalExceptionHandler.class})
class NoticeControllerTest {
    private static final UUID NOTICE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-10-08T12:00:00+09:00");
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-10-08T13:00:00+09:00");

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean NoticeQueryService queryService;

    @ParameterizedTest
    @EnumSource(Role.class)
    void memberAndAdminCanReadDefaultListAndDetail(Role role) throws Exception {
        authenticate(role);
        when(queryService.getNotices(0, 10)).thenReturn(defaultPage());
        when(queryService.getNotice(NOTICE_ID)).thenReturn(detail());

        mvc.perform(get("/api/v1/notices").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(10))
                .andExpect(jsonPath("$.result.totalElements").value(11))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.items[0].id").value(NOTICE_ID.toString()))
                .andExpect(jsonPath("$.result.items[0].isPinned").value(true))
                .andExpect(jsonPath("$.result.items[0].author.id").value("1"))
                .andExpect(jsonPath("$.result.items[0].createdAt")
                        .value("2026-10-08T12:00:00+09:00"));

        mvc.perform(get("/api/v1/notices/{noticeId}", NOTICE_ID)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.id").value(NOTICE_ID.toString()))
                .andExpect(jsonPath("$.result.title").value("고정 공지"))
                .andExpect(jsonPath("$.result.content").value("첫 줄\n둘째 줄"))
                .andExpect(jsonPath("$.result.isPinned").value(true))
                .andExpect(jsonPath("$.result.author.name").value("관리자"))
                .andExpect(jsonPath("$.result.imageUrls").isEmpty())
                .andExpect(jsonPath("$.result.createdAt")
                        .value("2026-10-08T12:00:00+09:00"))
                .andExpect(jsonPath("$.result.updatedAt")
                        .value("2026-10-08T13:00:00+09:00"));

        verify(queryService).getNotices(0, 10);
        verify(queryService).getNotice(NOTICE_ID);
    }

    @Test
    void explicitPagingIsForwardedAndReturnedAsMetadata() throws Exception {
        authenticate(Role.MEMBER);
        var response = new NoticePageResponse(List.of(summary(false)), 2, 3, 7, 3, false);
        when(queryService.getNotices(2, 3)).thenReturn(response);

        mvc.perform(get("/api/v1/notices?page=2&size=3")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(2))
                .andExpect(jsonPath("$.result.size").value(3))
                .andExpect(jsonPath("$.result.totalElements").value(7))
                .andExpect(jsonPath("$.result.totalPages").value(3))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.items[0].isPinned").value(false));

        verify(queryService).getNotices(2, 3);
    }

    @Test
    void unauthenticatedRequestsCannotReadListOrDetail() throws Exception {
        mvc.perform(get("/api/v1/notices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/v1/notices/{noticeId}", NOTICE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(queryService);
    }

    @Test
    void invalidBearerCannotReadListOrDetail() throws Exception {
        String invalidBearer = "Bearer broken.token";

        mvc.perform(get("/api/v1/notices").header(HttpHeaders.AUTHORIZATION, invalidBearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mvc.perform(get("/api/v1/notices/{noticeId}", NOTICE_ID)
                        .header(HttpHeaders.AUTHORIZATION, invalidBearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(queryService);
    }

    @Test
    void invalidPageOrSizeIsRejectedBeforeQuerying() throws Exception {
        authenticate(Role.MEMBER);

        for (String query : List.of(
                "?page=not-a-number",
                "?size=not-a-number",
                "?page=-1",
                "?size=0",
                "?size=101")) {
            mvc.perform(get("/api/v1/notices" + query).header(HttpHeaders.AUTHORIZATION, token()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        verifyNoInteractions(queryService);
    }

    @Test
    void malformedNoticeIdIsRejectedBeforeQuerying() throws Exception {
        authenticate(Role.MEMBER);

        mvc.perform(get("/api/v1/notices/not-a-uuid").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(queryService);
    }

    @Test
    void absentOrNonPublicNoticeReturnsNotFound() throws Exception {
        authenticate(Role.MEMBER);
        UUID absentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(queryService.getNotice(absentId))
                .thenThrow(new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND));

        mvc.perform(get("/api/v1/notices/{noticeId}", absentId)
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));

        verify(queryService).getNotice(absentId);
    }

    private NoticePageResponse defaultPage() {
        return new NoticePageResponse(List.of(summary(true)), 0, 10, 11, 2, true);
    }

    private NoticeSummary summary(boolean isPinned) {
        return new NoticeSummary(NOTICE_ID, isPinned ? "고정 공지" : "일반 공지", isPinned,
                new NoticeAuthor("1", "관리자"), CREATED_AT, UPDATED_AT);
    }

    private NoticeDetailResponse detail() {
        return new NoticeDetailResponse(NOTICE_ID, "고정 공지", "첫 줄\n둘째 줄", true,
                new NoticeAuthor("1", "관리자"), List.of(), CREATED_AT, UPDATED_AT);
    }

    private void authenticate(Role role) {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(
                new Member("notice-reader", "hash", "회원", 17, role)));
    }

    private String token() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
