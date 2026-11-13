package com.sdpcheck.sdpcheck.domain.member.controller;

import com.sdpcheck.sdpcheck.domain.member.dto.response.AdminMemberListItem;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.AdminMemberListQueryRepository;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberCommandService;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberListQueryService;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberQueryService;
import com.sdpcheck.sdpcheck.global.exception.GlobalExceptionHandler;
import com.sdpcheck.sdpcheck.global.security.config.SecurityConfig;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import com.sdpcheck.sdpcheck.global.security.jwt.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminMemberController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class,
        GlobalExceptionHandler.class, AdminMemberListQueryService.class})
class AdminMemberListControllerTest {
    private static final String PATH = "/api/v1/admin/members";

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository members;
    @MockitoBean AdminMemberQueryService queryService;
    @MockitoBean AdminMemberCommandService commandService;
    @MockitoBean AdminMemberListQueryRepository listRepository;

    @Test
    void defaultsToAllGenerationsAndReturnsOnlyTheFourMemberFields() throws Exception {
        authenticate(Role.ADMIN);
        when(listRepository.findMembers(null, 21, 0L)).thenReturn(List.of(
                new AdminMemberListItem(1L, "관리자", 19, Role.ADMIN),
                new AdminMemberListItem(2L, "회원", 18, Role.MEMBER)));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result", aMapWithSize(4)))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.members", hasSize(2)))
                .andExpect(jsonPath("$.result.members[0]", aMapWithSize(4)))
                .andExpect(jsonPath("$.result.members[0].memberId").value(1))
                .andExpect(jsonPath("$.result.members[0].name").value("관리자"))
                .andExpect(jsonPath("$.result.members[0].generation").value(19))
                .andExpect(jsonPath("$.result.members[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.result.members[1].role").value("MEMBER"))
                .andExpect(jsonPath("$.result.members[0].password").doesNotExist())
                .andExpect(jsonPath("$.result.members[0].loginId").doesNotExist())
                .andExpect(jsonPath("$.result.members[0].kakaoId").doesNotExist());

        verify(listRepository).findMembers(null, 21, 0L);
        verifyNoInteractions(queryService, commandService);
    }

    @Test
    void appliesGenerationAndPaginationAndExcludesTheLookaheadRow() throws Exception {
        authenticate(Role.ADMIN);
        when(listRepository.findMembers(18, 2, 2L)).thenReturn(List.of(
                new AdminMemberListItem(3L, "세 번째", 18, Role.ADMIN),
                new AdminMemberListItem(4L, "네 번째", 18, Role.MEMBER)));

        mvc.perform(get(PATH).queryParam("generation", "18").queryParam("page", "2").queryParam("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(2))
                .andExpect(jsonPath("$.result.size").value(1))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.members", hasSize(1)))
                .andExpect(jsonPath("$.result.members[0].memberId").value(3))
                .andExpect(jsonPath("$.result.members[0].generation").value(18));

        verify(listRepository).findMembers(18, 2, 2L);
    }

    @Test
    void unknownGenerationReturnsAnEmptySuccessfulPage() throws Exception {
        authenticate(Role.ADMIN);
        when(listRepository.findMembers(99, 21, 0L)).thenReturn(List.of());

        mvc.perform(get(PATH).queryParam("generation", "99").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.members").isArray())
                .andExpect(jsonPath("$.result.members").isEmpty())
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @ParameterizedTest
    @CsvSource({"generation,0", "generation,-1", "page,-1", "size,0", "size,51"})
    void outOfRangeParametersAreRejectedBeforeTheRepository(String parameter, String value) throws Exception {
        authenticate(Role.ADMIN);

        mvc.perform(get(PATH).queryParam(parameter, value).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(listRepository);
    }

    @ParameterizedTest
    @CsvSource({"generation,abc", "page,abc", "size,abc", "generation,2147483648", "page,2147483648"})
    void malformedNumericParametersUseTheCommonValidationEnvelope(String parameter, String value) throws Exception {
        authenticate(Role.ADMIN);

        mvc.perform(get(PATH).queryParam(parameter, value).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(listRepository);
    }

    @Test
    void acceptsTheMaximumPageSizeAndUsesALongOffset() throws Exception {
        authenticate(Role.ADMIN);
        when(listRepository.findMembers(null, 51, 107374182350L)).thenReturn(List.of());

        mvc.perform(get(PATH).queryParam("page", "2147483647").queryParam("size", "50")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.result.size").value(50))
                .andExpect(jsonPath("$.result.members").isEmpty());

        verify(listRepository).findMembers(null, 51, 107374182350L);
    }

    @Test
    void memberCannotReadTheListOrElevateThroughParameters() throws Exception {
        authenticate(Role.MEMBER);

        mvc.perform(get(PATH).queryParam("role", "ADMIN").queryParam("memberId", "1")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(listRepository);
    }

    @Test
    void onboardingIncompleteMemberCannotReadTheList() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.of(Member.createKakaoMember(123L)));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(listRepository);
    }

    @Test
    void anAdminLosesListAccessAfterDemotionUsingTheSameToken() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.of(member(Role.ADMIN)), Optional.of(member(Role.MEMBER)));
        when(listRepository.findMembers(null, 21, 0L)).thenReturn(List.of());
        String authorization = token();

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, authorization)).andExpect(status().isOk());
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(listRepository, times(1)).findMembers(null, 21, 0L);
    }

    @Test
    void anonymousInvalidExpiredAndRefreshTokensCannotReadTheList() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        var expired = new JwtProvider("test-only-secret-key-for-ci-0123456789-abcdefghij", -10_000, 120_000);
        for (String authorization : List.of("Bearer broken.token", "Bearer " + expired.createAccessToken(1L),
                "Bearer " + jwtProvider.createRefreshToken(1L))) {
            mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        verifyNoInteractions(members, listRepository);
    }

    @Test
    void deletedMemberCannotUseAnOldToken() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.empty());

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(listRepository);
    }

    @Test
    void repositoryFailureReturnsTheCommon500WithoutStorageDetails() throws Exception {
        authenticate(Role.ADMIN);
        when(listRepository.findMembers(null, 21, 0L)).thenThrow(new IllegalStateException("private storage detail"));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.result").value((Object) null));
    }

    private void authenticate(Role role) {
        when(members.findById(1L)).thenReturn(Optional.of(member(role)));
    }

    private Member member(Role role) {
        return new Member("member-list-admin", "hash", "관리자", 18, role);
    }

    private String token() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
