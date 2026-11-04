package com.sdpcheck.sdpcheck.domain.statistics.controller;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.SessionAttendanceStatisticsItem;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.SessionAttendanceStatisticsResponse;
import com.sdpcheck.sdpcheck.domain.statistics.service.SessionStatisticsQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
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

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSessionStatisticsController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class, GlobalExceptionHandler.class})
class AdminSessionStatisticsControllerTest {
    private static final String PATH = "/api/v1/admin/statistics/sessions";
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-10-08T19:00:00+09:00");

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository members;
    @MockitoBean SessionStatisticsQueryService service;

    @Test
    void adminReceivesSessionIdentityCountsAndRateInTheCommonEnvelope() throws Exception {
        authenticate(Role.ADMIN);
        var session = new SessionAttendanceStatisticsItem(SESSION_ID, "정규 세션", STARTS_AT, 3, 1, 1, 1, 67);
        when(service.getSessionAttendanceStatistics())
                .thenReturn(new SessionAttendanceStatisticsResponse("2026-2H", List.of(session)));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result", aMapWithSize(2)))
                .andExpect(jsonPath("$.result.periodCode").value("2026-2H"))
                .andExpect(jsonPath("$.result.sessions", hasSize(1)))
                .andExpect(jsonPath("$.result.sessions[0]", aMapWithSize(8)))
                .andExpect(jsonPath("$.result.sessions[0].sessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.result.sessions[0].sessionTitle").value("정규 세션"))
                .andExpect(jsonPath("$.result.sessions[0].startsAt").value("2026-10-08T19:00:00+09:00"))
                .andExpect(jsonPath("$.result.sessions[0].totalCount").value(3))
                .andExpect(jsonPath("$.result.sessions[0].presentCount").value(1))
                .andExpect(jsonPath("$.result.sessions[0].lateCount").value(1))
                .andExpect(jsonPath("$.result.sessions[0].absentCount").value(1))
                .andExpect(jsonPath("$.result.sessions[0].attendanceRate").value(67));

        verify(service).getSessionAttendanceStatistics();
    }

    @Test
    void sessionWithoutTargetsRetainsNullRateAndAllZeroCounts() throws Exception {
        authenticate(Role.ADMIN);
        var session = new SessionAttendanceStatisticsItem(SESSION_ID, "대상 없는 세션", STARTS_AT, 0, 0, 0, 0, null);
        when(service.getSessionAttendanceStatistics())
                .thenReturn(new SessionAttendanceStatisticsResponse("2026-2H", List.of(session)));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sessions[0]", aMapWithSize(8)))
                .andExpect(jsonPath("$.result.sessions[0].attendanceRate").hasJsonPath())
                .andExpect(jsonPath("$.result.sessions[0].attendanceRate").value((Object) null))
                .andExpect(jsonPath("$.result.sessions[0].totalCount").value(0))
                .andExpect(jsonPath("$.result.sessions[0].presentCount").value(0))
                .andExpect(jsonPath("$.result.sessions[0].lateCount").value(0))
                .andExpect(jsonPath("$.result.sessions[0].absentCount").value(0));
    }

    @Test
    void noEligibleSessionsReturnsAnEmptyArrayInsteadOfNotFound() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getSessionAttendanceStatistics())
                .thenReturn(new SessionAttendanceStatisticsResponse("2026-2H", List.of()));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.periodCode").value("2026-2H"))
                .andExpect(jsonPath("$.result.sessions").isArray())
                .andExpect(jsonPath("$.result.sessions").isEmpty());
    }

    @Test
    void memberCannotAccessSessionStatistics() throws Exception {
        authenticate(Role.MEMBER);

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(service);
    }

    @Test
    void incompleteMemberCannotAccessSessionStatisticsEvenWithAnAdminRole() throws Exception {
        Member member = Member.createKakaoMember(123L);
        member.changeRole(Role.ADMIN);
        when(members.findById(1L)).thenReturn(Optional.of(member));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(service);
    }

    @Test
    void previouslyIssuedAdminTokenCannotSurviveADemotion() throws Exception {
        authenticate(Role.ADMIN);
        String oldAdminToken = accessToken();
        authenticate(Role.MEMBER);

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, oldAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(service);
    }

    @Test
    void anonymousRequestIsRejectedBeforeQuerying() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(service);
    }

    @Test
    void invalidExpiredAndRefreshTokensAreRejectedBeforeQuerying() throws Exception {
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
    void deletedMemberCannotAccessWithAnOldValidToken() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.empty());

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(service);
    }

    @Test
    void missingOperatingSemesterReturnsTheExistingConfigurationError() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getSessionAttendanceStatistics())
                .thenThrow(new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("OPERATING_SEMESTER_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.result").value((Object) null));
    }

    @Test
    void storageFailureUsesTheCommonErrorWithoutLeakingDetails() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getSessionAttendanceStatistics()).thenThrow(new IllegalStateException("storage detail"));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.result").value((Object) null));
    }

    private void authenticate(Role role) {
        when(members.findById(1L)).thenReturn(Optional.of(
                new Member("statistics-admin", "hash", "관리자", 17, role)));
    }

    private String accessToken() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
