package com.sdpcheck.sdpcheck.domain.statistics.controller;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.StatisticsSummaryResponse;
import com.sdpcheck.sdpcheck.domain.statistics.service.StatisticsQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.GlobalExceptionHandler;
import com.sdpcheck.sdpcheck.global.security.config.SecurityConfig;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminStatisticsController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class, GlobalExceptionHandler.class})
class AdminStatisticsControllerTest {
    private static final String PATH = "/api/v1/admin/statistics";

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository members;
    @MockitoBean StatisticsQueryService service;

    @Test
    void adminReceivesTheFiveFieldSummaryInTheCommonEnvelope() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getAdminStatistics()).thenReturn(new StatisticsSummaryResponse("2026-2H", 68, 12, 8, 10));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result", aMapWithSize(5)))
                .andExpect(jsonPath("$.result.periodCode").value("2026-2H"))
                .andExpect(jsonPath("$.result.averageAttendanceRate").value(68))
                .andExpect(jsonPath("$.result.totalSessionCount").value(12))
                .andExpect(jsonPath("$.result.activeMemberCount").value(8))
                .andExpect(jsonPath("$.result.totalMemberCount").value(10));

        verify(service).getAdminStatistics();
    }

    @Test
    void emptyAggregateIsSuccessfulAndKeepsTheNullAverageField() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getAdminStatistics()).thenReturn(new StatisticsSummaryResponse("2026-2H", null, 0, 0, 0));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result", aMapWithSize(5)))
                .andExpect(jsonPath("$.result.averageAttendanceRate").hasJsonPath())
                .andExpect(jsonPath("$.result.averageAttendanceRate").value((Object) null))
                .andExpect(jsonPath("$.result.totalSessionCount").value(0))
                .andExpect(jsonPath("$.result.activeMemberCount").value(0))
                .andExpect(jsonPath("$.result.totalMemberCount").value(0));

        verify(service).getAdminStatistics();
    }

    @Test
    void memberCannotAccessStatisticsAndTheServiceIsNotCalled() throws Exception {
        authenticate(Role.MEMBER);

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(service);
    }

    @Test
    void anAdminTokenUsesTheCurrentDatabaseRoleAndCannotSurviveADemotion() throws Exception {
        authenticate(Role.ADMIN);
        String oldAdminToken = accessToken();
        authenticate(Role.MEMBER);

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, oldAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(service);
    }

    @Test
    void anonymousRequestIsUnauthorizedBeforeServiceAccess() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(service);
    }

    @Test
    void invalidExpiredAndRefreshTokensAreUnauthorizedBeforeServiceAccess() throws Exception {
        var expired = new JwtProvider("test-only-secret-key-for-ci-0123456789-abcdefghij", -10_000, 120_000);
        for (String authorization : new String[] {
                "Bearer broken.token",
                "Bearer " + expired.createAccessToken(1L),
                "Bearer " + jwtProvider.createRefreshToken(1L)
        }) {
            mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        verifyNoInteractions(service);
    }

    @Test
    void deletedMemberCannotAccessStatisticsWithAnOldValidToken() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.empty());

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(service);
    }

    @Test
    void queryParametersCannotOverrideTheAdministratorOrOperatingPeriod() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getAdminStatistics()).thenReturn(new StatisticsSummaryResponse("2026-2H", null, 0, 0, 0));

        mvc.perform(get(PATH).queryParam("memberId", "2").queryParam("periodCode", "2025-1H")
                        .header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.periodCode").value("2026-2H"));

        verify(service).getAdminStatistics();
    }

    @Test
    void missingOperatingSemesterUsesTheExisting503Contract() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getAdminStatistics())
                .thenThrow(new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("OPERATING_SEMESTER_NOT_CONFIGURED"));

        verify(service).getAdminStatistics();
    }

    @Test
    void unexpectedStorageFailureUsesTheCommon500ContractWithoutLeakingDetails() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getAdminStatistics()).thenThrow(new IllegalStateException("storage detail"));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));

        verify(service).getAdminStatistics();
    }

    @Test
    void controllerRejectsNullAndNonAdminPrincipalsWhenInvokedDirectly() {
        AdminStatisticsController controller = new AdminStatisticsController(service);

        BusinessException missing = assertThrows(BusinessException.class,
                () -> controller.getAdminStatistics(null));
        BusinessException denied = assertThrows(BusinessException.class,
                () -> controller.getAdminStatistics(new AuthenticatedMember(1L, "member", Role.MEMBER)));

        assertEquals(AuthErrorCode.AUTHENTICATION_REQUIRED, missing.getErrorCode());
        assertEquals(AuthErrorCode.ACCESS_DENIED, denied.getErrorCode());
        verifyNoInteractions(service);
    }

    private void authenticate(Role role) {
        when(members.findById(1L)).thenReturn(Optional.of(
                new Member("statistics-admin", "hash", "관리자", 17, role)));
    }

    private String accessToken() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
