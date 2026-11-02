package com.sdpcheck.sdpcheck.domain.device;

import com.sdpcheck.sdpcheck.domain.device.controller.PushTokenController;
import com.sdpcheck.sdpcheck.domain.device.dto.request.PushTokenRequest;
import com.sdpcheck.sdpcheck.domain.device.dto.response.PushTokenResponse;
import com.sdpcheck.sdpcheck.domain.device.exception.PushTokenErrorCode;
import com.sdpcheck.sdpcheck.domain.device.service.PushTokenService;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.global.exception.GlobalExceptionHandler;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
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

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(controllers = PushTokenController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class,
        GlobalExceptionHandler.class})
class PushTokenControllerTest {
    private static final String URL = "/api/v1/devices/push-tokens";
    private static final String SECRET = "test-only-secret-key-for-ci-0123456789-abcdefghij";
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @MockitoBean MemberRepository members;
    @MockitoBean PushTokenService service;

    @Test
    void memberReceivesSpecifiedSuccessEnvelope() throws Exception {
        authenticate(Role.MEMBER);
        when(service.addPushToken(1L, new PushTokenRequest("fcm-token"))).thenReturn(new PushTokenResponse(37L));
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fcm-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PUSH_TOKEN_REGISTER_SUCCESS"))
                .andExpect(jsonPath("$.message").value("푸시 토큰 등록에 성공했습니다."))
                .andExpect(jsonPath("$.result.tokenId").value(37));
        verify(service).addPushToken(1L, new PushTokenRequest("fcm-token"));
    }

    @Test
    void adminCanRegister() throws Exception {
        authenticate(Role.ADMIN);
        when(service.addPushToken(1L, new PushTokenRequest("fcm-token"))).thenReturn(new PushTokenResponse(37L));
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fcm-token\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidRequestReturnsSpecifiedErrorAndDoesNotReachService() throws Exception {
        authenticate(Role.MEMBER);
        for (String body : List.of("{}", "{\"token\":null}", "{\"token\":\"\"}",
                "{\"token\":\" \"}", "{\"token\":\"a b\"}", "{\"token\":\"a\\nb\"}",
                "{\"token\":\"" + "a".repeat(513) + "\"}", "{\"token\":{}}", "{", "")) {
            mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, token())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                    .andExpect(jsonPath("$.result").value(nullValue()));
        }
        verifyNoInteractions(service);
    }

    @Test
    void maximumLengthTokenIsAccepted() throws Exception {
        authenticate(Role.MEMBER);
        String pushToken = "a".repeat(512);
        when(service.addPushToken(1L, new PushTokenRequest(pushToken))).thenReturn(new PushTokenResponse(37L));
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + pushToken + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void absentAccessTokenReturnsUnauthorized() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fcm-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
        verifyNoInteractions(service);
    }

    @Test
    void invalidExpiredAndRefreshTokensReturnUnauthorized() throws Exception {
        var expired = new JwtProvider(SECRET, -10_000, 60_000);
        var forged = new JwtProvider("another-test-secret-key-0123456789-abcdefghij", 60_000, 120_000);
        for (String authorization : List.of("Bearer invalid", "Basic invalid", "Bearer ",
                "Bearer " + expired.createAccessToken(1L), "Bearer " + jwt.createRefreshToken(1L),
                "Bearer " + forged.createAccessToken(1L))) {
            mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, authorization)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fcm-token\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }
        verifyNoInteractions(service, members);
    }

    @Test
    void missingAuthenticatedMemberReturnsInvalidToken() throws Exception {
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fcm-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("유효하지 않거나 만료된 인증 토큰입니다."));
        verifyNoInteractions(service);
    }

    @Test
    void deletesTokenForCurrentMemberWithExpectedResponse() throws Exception {
        authenticate(Role.MEMBER);
        mvc.perform(delete(URL + "/37").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verify(service).deletePushToken(1L, 37L);
    }

    @Test
    void adminCanDeleteToken() throws Exception {
        authenticate(Role.ADMIN);
        mvc.perform(delete(URL + "/37").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).deletePushToken(1L, 37L);
    }

    @Test
    void deletionRequiresValidAccessToken() throws Exception {
        mvc.perform(delete(URL + "/37"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(delete(URL + "/37").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        verifyNoInteractions(service);
    }

    @Test
    void invalidTokenIdDoesNotReachService() throws Exception {
        authenticate(Role.MEMBER);
        for (String tokenId : List.of("0", "-1", "abc", "9223372036854775808")) {
            mvc.perform(delete(URL + "/" + tokenId).header(HttpHeaders.AUTHORIZATION, token()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void missingOrOtherMembersTokenReturnsNotFound() throws Exception {
        authenticate(Role.MEMBER);
        doThrow(new BusinessException(PushTokenErrorCode.PUSH_TOKEN_NOT_FOUND))
                .when(service).deletePushToken(1L, 37L);
        mvc.perform(delete(URL + "/37").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PUSH_TOKEN_NOT_FOUND"));
    }

    @Test
    void memberWithIncompleteOnboardingCannotRegisterOrDeletePushToken() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.of(Member.createKakaoMember(123L)));
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fcm-token\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(delete(URL + "/37").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(service);
    }

    private void authenticate(Role role) {
        when(members.findById(1L)).thenReturn(Optional.of(new Member("push-member", "hash", "회원", 17, role)));
    }

    private String token() {
        return "Bearer " + jwt.createAccessToken(1L);
    }
}
