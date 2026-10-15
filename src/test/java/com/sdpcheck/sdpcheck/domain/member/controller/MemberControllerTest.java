package com.sdpcheck.sdpcheck.domain.member.controller;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.insight.service.AttendanceInsightService;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.member.service.MemberService;
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

import java.util.Optional;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MemberController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({MemberService.class, SecurityConfig.class, JwtProvider.class,
        SecurityExceptionHandler.class, GlobalExceptionHandler.class})
class MemberControllerTest {
    private static final String URL = "/api/v1/members/me/notification-settings";
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @MockitoBean MemberRepository members;
    @MockitoBean AttendanceInsightService attendanceInsightService;

    @Test
    void attendanceInsightReturnsStandardResponseForAuthenticatedMember() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.of(member(Role.MEMBER)));
        when(attendanceInsightService.getAttendanceInsight(1L)).thenReturn("최근 세션에 출석하셨어요!");

        mvc.perform(get("/api/v1/members/me/attendance-insight")
                        .header(HttpHeaders.AUTHORIZATION, token()).param("memberId", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("ATTENDANCE_INSIGHT_GET_SUCCESS"))
                .andExpect(jsonPath("$.message").value("출석 인사이트 조회에 성공했습니다."))
                .andExpect(jsonPath("$.result.insight").value("최근 세션에 출석하셨어요!"));
        verify(attendanceInsightService).getAttendanceInsight(1L);
        verify(attendanceInsightService, never()).getAttendanceInsight(2L);
    }

    @Test
    void unauthenticatedAttendanceInsightDoesNotCallService() throws Exception {
        mvc.perform(get("/api/v1/members/me/attendance-insight"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(attendanceInsightService);
    }

    @Test
    void memberReceivesExactSettingsResponseAndCannotChooseAnotherMember() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.of(member(Role.MEMBER)));

        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token()).param("memberId", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_SETTINGS_GET_SUCCESS"))
                .andExpect(jsonPath("$.message").value("알림 설정 조회에 성공했습니다."))
                .andExpect(jsonPath("$.result", aMapWithSize(2)))
                .andExpect(jsonPath("$.result.sessionStartEnabled").value(true))
                .andExpect(jsonPath("$.result.newNoticeEnabled").value(true));
        verify(members, times(2)).findById(1L);
        verify(members, never()).findById(2L);
    }

    @Test
    void returnsStoredFalseValueInsteadOfAlwaysReturningDefaults() throws Exception {
        Member member = member(Role.MEMBER);
        member.updateSessionStartEnabled(false);
        when(members.findById(1L)).thenReturn(Optional.of(member));

        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sessionStartEnabled").value(false))
                .andExpect(jsonPath("$.result.newNoticeEnabled").value(true));
    }

    @Test
    void adminCanReadOwnSettings() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.of(member(Role.ADMIN)));
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk());
    }

    @Test
    void missingOrInvalidAccessTokenIsRejectedBeforeMemberLookup() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createRefreshToken(1L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        verifyNoInteractions(members);
    }

    @Test
    void memberWithIncompleteOnboardingIsForbidden() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.of(Member.createKakaoMember(123L)));
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verify(members).findById(1L);
    }

    @Test
    void memberMissingAtServiceLookupReturns404ThroughGlobalHandler() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.of(member(Role.MEMBER)))
                .thenReturn(Optional.empty());
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("회원 정보를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.result").value(nullValue()));
    }

    @Test
    void patchesOneSettingAndKeepsOtherValueAndMemberIdentity() throws Exception {
        Member member = member(Role.MEMBER);
        member.updateNewNoticeEnabled(false);
        when(members.findById(1L)).thenReturn(Optional.of(member));

        mvc.perform(patch(URL).header(HttpHeaders.AUTHORIZATION, token()).param("memberId", "2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sessionStartEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_SETTINGS_UPDATE_SUCCESS"))
                .andExpect(jsonPath("$.message").value("알림 설정 수정에 성공했습니다."))
                .andExpect(jsonPath("$.result.sessionStartEnabled").value(false))
                .andExpect(jsonPath("$.result.newNoticeEnabled").value(false));
        assertThat(member.isSessionStartEnabled()).isFalse();
        assertThat(member.isNewNoticeEnabled()).isFalse();
        verify(members, never()).findById(2L);
    }

    @Test
    void patchesNoticeSettingForAdminAndKeepsSessionSetting() throws Exception {
        Member member = member(Role.ADMIN);
        when(members.findById(1L)).thenReturn(Optional.of(member));
        mvc.perform(patch(URL).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newNoticeEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sessionStartEnabled").value(true))
                .andExpect(jsonPath("$.result.newNoticeEnabled").value(false));
        assertThat(member.isSessionStartEnabled()).isTrue();
        assertThat(member.isNewNoticeEnabled()).isFalse();
    }

    @Test
    void patchesBothSettingsAndAllowsTurningThemBackOn() throws Exception {
        Member member = member(Role.MEMBER);
        when(members.findById(1L)).thenReturn(Optional.of(member));
        for (boolean value : new boolean[]{false, true}) {
            mvc.perform(patch(URL).header(HttpHeaders.AUTHORIZATION, token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sessionStartEnabled\":" + value + ",\"newNoticeEnabled\":" + value + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.sessionStartEnabled").value(value))
                    .andExpect(jsonPath("$.result.newNoticeEnabled").value(value));
        }
    }

    @Test
    void emptyOrMalformedPatchDoesNotChangeSettings() throws Exception {
        Member member = member(Role.MEMBER);
        when(members.findById(1L)).thenReturn(Optional.of(member));
        for (String body : java.util.List.of("{}", "{\"sessionStartEnabled\":null,\"newNoticeEnabled\":null}",
                "{\"sessionStartEnabled\":{}}", "{", "")) {
            mvc.perform(patch(URL).header(HttpHeaders.AUTHORIZATION, token())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(member.isSessionStartEnabled()).isTrue();
        assertThat(member.isNewNoticeEnabled()).isTrue();
    }

    @Test
    void patchRequiresAuthenticationAndCompletedOnboarding() throws Exception {
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"newNoticeEnabled\":false}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(URL).header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newNoticeEnabled\":false}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(members);

        when(members.findById(1L)).thenReturn(Optional.of(Member.createKakaoMember(123L)));
        mvc.perform(patch(URL).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newNoticeEnabled\":false}"))
                .andExpect(status().isForbidden());
        verify(members).findById(1L);
    }

    @Test
    void patchReturns404IfMemberDisappearsBeforeServiceLookup() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.of(member(Role.MEMBER)))
                .thenReturn(Optional.empty());
        mvc.perform(patch(URL).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newNoticeEnabled\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    private Member member(Role role) {
        return new Member("member", "hash", "회원", 17, role);
    }

    private String token() {
        return "Bearer " + jwt.createAccessToken(1L);
    }
}
