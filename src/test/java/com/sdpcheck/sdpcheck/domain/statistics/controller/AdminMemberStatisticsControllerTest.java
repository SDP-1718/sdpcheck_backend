package com.sdpcheck.sdpcheck.domain.statistics.controller;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.MemberAttendanceStatisticsItem;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.MemberAttendanceStatisticsResponse;
import com.sdpcheck.sdpcheck.domain.statistics.enums.MemberStatisticsSort;
import com.sdpcheck.sdpcheck.domain.statistics.service.MemberStatisticsQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import com.sdpcheck.sdpcheck.global.exception.GlobalExceptionHandler;
import com.sdpcheck.sdpcheck.global.security.config.SecurityConfig;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
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

import java.util.List;
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

@WebMvcTest(controllers = AdminMemberStatisticsController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class, GlobalExceptionHandler.class})
class AdminMemberStatisticsControllerTest {
    private static final String PATH = "/api/v1/admin/statistics/members";

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository members;
    @MockitoBean MemberStatisticsQueryService service;

    @Test
    void adminReceivesDefaultPaginationAndTheExactFiveAndNineFieldResponseShape() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getMemberAttendanceStatistics(0, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC))
                .thenReturn(fullResponse(0, 20, false));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result", aMapWithSize(5)))
                .andExpect(jsonPath("$.result.periodCode").value("2026-2H"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.members[0]", aMapWithSize(9)))
                .andExpect(jsonPath("$.result.members[0].memberId").value(1))
                .andExpect(jsonPath("$.result.members[0].name").value("가나다"))
                .andExpect(jsonPath("$.result.members[0].generation").value(17))
                .andExpect(jsonPath("$.result.members[0].presentCount").value(5))
                .andExpect(jsonPath("$.result.members[0].lateCount").value(1))
                .andExpect(jsonPath("$.result.members[0].absentCount").value(2))
                .andExpect(jsonPath("$.result.members[0].totalSessionCount").value(8))
                .andExpect(jsonPath("$.result.members[0].attendanceRate").value(75))
                .andExpect(jsonPath("$.result.members[0].isAtRisk").value(false))
                .andExpect(jsonPath("$.result.members[1].attendanceRate").hasJsonPath())
                .andExpect(jsonPath("$.result.members[1].attendanceRate").value((Object) null))
                .andExpect(jsonPath("$.result.members[1].isAtRisk").hasJsonPath())
                .andExpect(jsonPath("$.result.members[1].isAtRisk").value((Object) null));

        verify(service).getMemberAttendanceStatistics(0, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC);
    }

    @Test
    void emptyAndBeyondRangePagesAreSuccessful() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getMemberAttendanceStatistics(9, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC))
                .thenReturn(new MemberAttendanceStatisticsResponse("2026-2H", List.of(), 9, 20, false));

        mvc.perform(get(PATH).queryParam("page", "9").header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.members").isEmpty())
                .andExpect(jsonPath("$.result.page").value(9))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.hasNext").value(false));

        verify(service).getMemberAttendanceStatistics(9, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC);
    }

    @ParameterizedTest
    @EnumSource(MemberStatisticsSort.class)
    void everySupportedSortIsBoundAndForwarded(MemberStatisticsSort sort) throws Exception {
        authenticate(Role.ADMIN);
        when(service.getMemberAttendanceStatistics(2, 3, sort))
                .thenReturn(new MemberAttendanceStatisticsResponse("2026-2H", List.of(), 2, 3, false));

        mvc.perform(get(PATH).queryParam("page", "2").queryParam("size", "3")
                        .queryParam("sort", sort.name()).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(2))
                .andExpect(jsonPath("$.result.size").value(3));

        verify(service).getMemberAttendanceStatistics(2, 3, sort);
    }

    @Test
    void serviceValidationErrorsForNegativeOrOutOfRangeNumbersAreReturnedAs400() throws Exception {
        authenticate(Role.ADMIN);
        BusinessException validationError = new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        when(service.getMemberAttendanceStatistics(-1, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC))
                .thenThrow(validationError);
        when(service.getMemberAttendanceStatistics(0, 0, MemberStatisticsSort.ATTENDANCE_RATE_ASC))
                .thenThrow(validationError);
        when(service.getMemberAttendanceStatistics(0, 51, MemberStatisticsSort.ATTENDANCE_RATE_ASC))
                .thenThrow(validationError);

        for (String query : List.of("?page=-1", "?size=0", "?size=51")) {
            mvc.perform(get(PATH + query).header(HttpHeaders.AUTHORIZATION, accessToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void malformedOrOverflowingParametersAreRejectedBeforeServiceAccess() throws Exception {
        authenticate(Role.ADMIN);

        for (String query : List.of(
                "?page=not-a-number",
                "?size=not-a-number",
                "?page=2147483648",
                "?size=2147483648",
                "?sort=NOT_A_SORT")) {
            mvc.perform(get(PATH + query).header(HttpHeaders.AUTHORIZATION, accessToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        verifyNoInteractions(service);
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
    void anonymousInvalidExpiredAndRefreshTokensAreRejectedBeforeServiceAccess() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

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
    void deletedMemberCannotReadStatisticsWithAnOldValidToken() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.empty());

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(service);
    }

    @Test
    void memberAndPeriodQueryParametersCannotOverrideTheConfiguredResponse() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getMemberAttendanceStatistics(0, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC))
                .thenReturn(new MemberAttendanceStatisticsResponse("2026-2H", List.of(), 0, 20, false));

        mvc.perform(get(PATH).queryParam("memberId", "2").queryParam("periodCode", "2025-1H")
                        .header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.periodCode").value("2026-2H"));

        verify(service).getMemberAttendanceStatistics(0, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC);
    }

    @Test
    void missingOperatingSemesterUsesTheExisting503Contract() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getMemberAttendanceStatistics(0, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC))
                .thenThrow(new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("OPERATING_SEMESTER_NOT_CONFIGURED"));

        verify(service).getMemberAttendanceStatistics(0, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC);
    }

    @Test
    void unexpectedStorageFailureUsesTheCommon500ContractWithoutLeakingDetails() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getMemberAttendanceStatistics(0, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC))
                .thenThrow(new IllegalStateException("storage detail"));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));

        verify(service).getMemberAttendanceStatistics(0, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC);
    }

    @Test
    void controllerRejectsANullPrincipalWhenInvokedDirectly() {
        AdminMemberStatisticsController controller = new AdminMemberStatisticsController(service);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.getMemberAttendanceStatistics(null, 0, 20, MemberStatisticsSort.ATTENDANCE_RATE_ASC));

        assertEquals(AuthErrorCode.AUTHENTICATION_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(service);
    }

    private MemberAttendanceStatisticsResponse fullResponse(int page, int size, boolean hasNext) {
        return new MemberAttendanceStatisticsResponse("2026-2H", List.of(
                new MemberAttendanceStatisticsItem(1L, "가나다", 17, 5, 1, 2, 8, 75, false),
                new MemberAttendanceStatisticsItem(2L, "라마바", 18, 0, 0, 0, 0, null, null)),
                page, size, hasNext);
    }

    private void authenticate(Role role) {
        when(members.findById(1L)).thenReturn(Optional.of(
                new Member("member-statistics-admin", "hash", "관리자", 17, role)));
    }

    private String accessToken() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
