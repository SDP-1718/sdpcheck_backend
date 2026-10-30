package com.sdpcheck.sdpcheck.domain.member.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.dto.response.MemberGenerationsResponse;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberCommandService;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberQueryService;
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

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class, GlobalExceptionHandler.class})
class AdminMemberControllerTest {
    private static final String PATH = "/api/v1/admin/members/generations";

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository members;
    @MockitoBean AdminMemberQueryService service;
    @MockitoBean AdminMemberCommandService commandService;

    @Test
    void adminReceivesTheGenerationArrayInTheCommonEnvelopeWithoutCaching() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getMemberGenerations()).thenReturn(new MemberGenerationsResponse(List.of(19, 18, 17)));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("요청을 성공적으로 처리했습니다."))
                .andExpect(jsonPath("$.result", aMapWithSize(1)))
                .andExpect(jsonPath("$.result.generations", contains(19, 18, 17)));

        verify(service).getMemberGenerations();
    }

    @Test
    void noEligibleGenerationsReturnsAnEmptyArrayWith200() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getMemberGenerations()).thenReturn(new MemberGenerationsResponse(List.of()));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result", aMapWithSize(1)))
                .andExpect(jsonPath("$.result.generations", empty()));
    }

    @Test
    void memberCannotReadTheAdminGenerationListOrElevateThroughQueryParameters() throws Exception {
        authenticate(Role.MEMBER);

        mvc.perform(get(PATH).queryParam("role", "ADMIN")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(service, commandService);
    }

    @Test
    void aValidTokenForAnIncompleteKakaoMemberCannotReadGenerations() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.of(Member.createKakaoMember(12345L)));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(service, commandService);
    }

    @Test
    void anonymousRequestIsUnauthorizedBeforeQueryingMembers() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(members, service, commandService);
    }

    @Test
    void invalidExpiredAndRefreshTokensCannotReadGenerations() throws Exception {
        var expired = new JwtProvider("test-only-secret-key-for-ci-0123456789-abcdefghij", -10_000, 120_000);
        for (String authorization : List.of("Bearer broken.token",
                "Bearer " + expired.createAccessToken(1L),
                "Bearer " + jwtProvider.createRefreshToken(1L))) {
            mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        verifyNoInteractions(members, service, commandService);
    }

    @Test
    void theSameAccessTokenLosesAccessAfterTheDatabaseRoleIsDemoted() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.of(member(Role.ADMIN)), Optional.of(member(Role.MEMBER)));
        when(service.getMemberGenerations()).thenReturn(new MemberGenerationsResponse(List.of(17)));
        String authorization = token();

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(service, times(1)).getMemberGenerations();
    }

    @Test
    void aDeletedMemberCannotUseAnOldAccessToken() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.empty());

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(service, commandService);
    }

    @Test
    void aQueryFailureUsesTheCommon500EnvelopeWithoutLeakingStorageDetails() throws Exception {
        authenticate(Role.ADMIN);
        when(service.getMemberGenerations()).thenThrow(new IllegalStateException("private storage detail"));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.result").hasJsonPath())
                .andExpect(jsonPath("$.result").value((Object) null));
    }

    @Test
    void directControllerCallsAlsoRejectMissingAndNonAdminPrincipals() {
        var controller = new AdminMemberController(service, commandService);
        var missing = assertThrows(BusinessException.class, () -> controller.getMemberGenerations(null));
        assertEquals(AuthErrorCode.AUTHENTICATION_REQUIRED, missing.getErrorCode());

        for (AuthenticatedMember principal : List.of(
                new AuthenticatedMember(1L, "reader", Role.MEMBER),
                new AuthenticatedMember(1L, null, null))) {
            var denied = assertThrows(BusinessException.class, () -> controller.getMemberGenerations(principal));
            assertEquals(AuthErrorCode.ACCESS_DENIED, denied.getErrorCode());
        }
        verifyNoInteractions(service, commandService);
    }

    private void authenticate(Role role) {
        when(members.findById(1L)).thenReturn(Optional.of(member(role)));
    }

    private Member member(Role role) {
        return new Member("generation-reader", "hash", "회원", 17, role);
    }

    private String token() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
