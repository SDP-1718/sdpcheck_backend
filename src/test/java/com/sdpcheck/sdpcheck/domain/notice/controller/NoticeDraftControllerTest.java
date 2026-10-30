package com.sdpcheck.sdpcheck.domain.notice.controller;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeDraftResponse;
import com.sdpcheck.sdpcheck.domain.notice.enums.NoticeDraftMode;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeDraftService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import com.sdpcheck.sdpcheck.global.exception.GlobalExceptionHandler;
import com.sdpcheck.sdpcheck.global.exception.RateLimitExceededException;
import com.sdpcheck.sdpcheck.global.security.config.SecurityConfig;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import com.sdpcheck.sdpcheck.global.security.jwt.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = NoticeDraftController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000", "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class, GlobalExceptionHandler.class})
class NoticeDraftControllerTest {
    private static final String URL = "/api/v1/admin/notices/ai-drafts";
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @MockitoBean MemberRepository members;
    @MockitoBean NoticeDraftService service;

    @Test
    void successHasExpectedEnvelopeAndNoStoreHeader() throws Exception {
        String token = authenticate(new Member("admin", "hash", "관리자", 17, Role.ADMIN));
        when(service.generate(1L)).thenReturn(new NoticeDraftResponse("정기 세션 안내",
                "10월 2일 금요일 오후 7시에 정기 세션이 진행됩니다.", NoticeDraftMode.MOCK));
        mvc.perform(post(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"  공지용 메모  \"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("NOTICE_DRAFT_CREATED"))
                .andExpect(jsonPath("$.message").value("공지 초안이 생성되었습니다."))
                .andExpect(jsonPath("$.result.title").value("정기 세션 안내"))
                .andExpect(jsonPath("$.result.content").value("10월 2일 금요일 오후 7시에 정기 세션이 진행됩니다."))
                .andExpect(jsonPath("$.result.mode").value("MOCK"));
        verify(service).generate(1L);
    }

    @Test
    void rejectsMissingBlankOversizedAndNonStringMemoAndUnknownFields() throws Exception {
        String token = authenticate(new Member("admin", "hash", "관리자", 17, Role.ADMIN));
        for (String body : List.of("{}", "null", "[]", "{\"memo\":null}", "{\"memo\":\"\"}",
                "{\"memo\":\" \\t\\n\"}", "{\"memo\":123}", "{\"memo\":true}",
                "{\"memo\":[]}", "{\"memo\":{}}", "{\"memo\":\"메모\",\"mode\":\"AI\"}",
                "{\"memo\":\"" + "가".repeat(2001) + "\"}")) {
            mvc.perform(post(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void acceptsOneCharacterAndTwoThousandCharactersAfterTrimming() throws Exception {
        String token = authenticate(new Member("admin", "hash", "관리자", 17, Role.ADMIN));
        for (String memo : List.of("가", "  " + "가".repeat(2000) + "  ")) {
            mvc.perform(post(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memo\":\"" + memo + "\"}"))
                    .andExpect(status().isOk());
        }
        verify(service, times(2)).generate(1L);
    }

    @Test
    void missingMalformedAndExpiredTokensUseContractCodeAndChallenge() throws Exception {
        var expired = new JwtProvider("test-only-secret-key-for-ci-0123456789-abcdefghij", -10000, 60000);
        for (String token : List.of("", "Bearer invalid", "Bearer " + expired.createAccessToken(1L))) {
            mvc.perform(post(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memo\":\"메모\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void ordinaryAndPendingKakaoMembersAreForbidden() throws Exception {
        for (Member member : List.of(new Member("member", "hash", "회원", 17, Role.MEMBER),
                Member.createKakaoMember(123L))) {
            String token = authenticate(member);
            mvc.perform(post(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memo\":\"메모\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void rateLimitHasRetryAfterAndFailureEnvelope() throws Exception {
        String token = authenticate(new Member("admin", "hash", "관리자", 17, Role.ADMIN));
        when(service.generate(1L)).thenThrow(new RateLimitExceededException(17));
        mvc.perform(post(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"메모\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "17"))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.result").value((Object) null));
    }

    @Test
    void disabledFeatureReturns503() throws Exception {
        String token = authenticate(new Member("admin", "hash", "관리자", 17, Role.ADMIN));
        when(service.generate(1L)).thenThrow(new BusinessException(CommonErrorCode.FEATURE_UNAVAILABLE));
        mvc.perform(post(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"메모\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("FEATURE_UNAVAILABLE"));
    }

    @Test
    void unexpectedErrorsUseCommon500EnvelopeWithoutLeakingDetails() throws Exception {
        String token = authenticate(new Member("admin", "hash", "관리자", 17, Role.ADMIN));
        when(service.generate(1L)).thenThrow(new IllegalStateException("internal test detail"));
        mvc.perform(post(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"메모\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    private String authenticate(Member member) {
        when(members.findById(1L)).thenReturn(Optional.of(member));
        return "Bearer " + jwt.createAccessToken(1L);
    }
}
