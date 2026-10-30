package com.sdpcheck.sdpcheck.domain.attendance.controller;

import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceSummaryResponse;
import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.service.AttendanceQueryService;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AttendanceSummaryController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class, GlobalExceptionHandler.class})
class AttendanceSummaryControllerTest {
    private static final String PATH = "/api/v1/members/me/attendance-summary";

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository members;
    @MockitoBean AttendanceQueryService service;

    @ParameterizedTest
    @EnumSource(Role.class)
    void memberAndAdminReceiveTheirOwnSummaryInTheCommonEnvelope(Role role) throws Exception {
        authenticate(role);
        when(service.getMyAttendanceSummary(1L))
                .thenReturn(new AttendanceSummaryResponse("2026-2H", 67, 7, 1, 4, 12));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result", aMapWithSize(6)))
                .andExpect(jsonPath("$.result.periodCode").value("2026-2H"))
                .andExpect(jsonPath("$.result.attendanceRate").value(67))
                .andExpect(jsonPath("$.result.presentCount").value(7))
                .andExpect(jsonPath("$.result.lateCount").value(1))
                .andExpect(jsonPath("$.result.absentCount").value(4))
                .andExpect(jsonPath("$.result.totalSessionCount").value(12));

        verify(service).getMyAttendanceSummary(1L);
    }

    @Test
    void emptySummaryIs200AndStillContainsAllSixFieldsIncludingNullRate() throws Exception {
        authenticate(Role.MEMBER);
        when(service.getMyAttendanceSummary(1L))
                .thenReturn(new AttendanceSummaryResponse("2026-2H", null, 0, 0, 0, 0));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result", aMapWithSize(6)))
                .andExpect(jsonPath("$.result.periodCode").value("2026-2H"))
                .andExpect(jsonPath("$.result.attendanceRate").hasJsonPath())
                .andExpect(jsonPath("$.result.attendanceRate").value((Object) null))
                .andExpect(jsonPath("$.result.presentCount").value(0))
                .andExpect(jsonPath("$.result.lateCount").value(0))
                .andExpect(jsonPath("$.result.absentCount").value(0))
                .andExpect(jsonPath("$.result.totalSessionCount").value(0));
    }

    @Test
    void queryParametersCannotOverrideTheTokenIdentityOrOperatingPeriod() throws Exception {
        authenticate(Role.MEMBER);
        when(service.getMyAttendanceSummary(1L))
                .thenReturn(new AttendanceSummaryResponse("2026-2H", null, 0, 0, 0, 0));

        mvc.perform(get(PATH).queryParam("memberId", "2").queryParam("periodCode", "2025-1H")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.periodCode").value("2026-2H"));

        verify(service).getMyAttendanceSummary(1L);
        verify(service, never()).getMyAttendanceSummary(2L);
    }

    @Test
    void anonymousRequestIsUnauthorizedBeforeServiceAccess() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(service);
    }

    @Test
    void invalidExpiredAndRefreshTokensCannotReadASummary() throws Exception {
        var expired = new JwtProvider("test-only-secret-key-for-ci-0123456789-abcdefghij", -10_000, 120_000);
        for (String authorization : new String[] {"Bearer broken.token",
                "Bearer " + expired.createAccessToken(1L), "Bearer " + jwtProvider.createRefreshToken(1L)}) {
            mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        verifyNoInteractions(service);
    }

    @Test
    void deletedMemberCannotReadASummaryWithAnOldValidToken() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.empty());

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(service);
    }

    @Test
    void missingOperatingSemesterConfigurationUsesTheExisting503Error() throws Exception {
        authenticate(Role.MEMBER);
        when(service.getMyAttendanceSummary(1L))
                .thenThrow(new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("OPERATING_SEMESTER_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.result").hasJsonPath())
                .andExpect(jsonPath("$.result").value((Object) null));
    }

    @Test
    void unexpectedStorageFailureUsesTheExisting500ErrorWithoutLeakingDetails() throws Exception {
        authenticate(Role.MEMBER);
        when(service.getMyAttendanceSummary(1L)).thenThrow(new IllegalStateException("internal DB detail"));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    void controllerAlsoRejectsAMissingPrincipalWhenInvokedDirectly() {
        var exception = assertThrows(BusinessException.class,
                () -> new AttendanceSummaryController(service).getMyAttendanceSummary(null));

        assertEquals(AuthErrorCode.AUTHENTICATION_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(service);
    }

    private void authenticate(Role role) {
        when(members.findById(1L)).thenReturn(Optional.of(new Member("summary-reader", "hash", "회원", 17, role)));
    }

    private String token() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
