package com.sdpcheck.sdpcheck.domain.auth.client;

import com.sdpcheck.sdpcheck.domain.auth.config.KakaoProperties;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KakaoClientTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final KakaoProperties properties = new KakaoProperties("client-id", "client-secret",
            "http://localhost:8080/login/oauth2/code/kakao");
    private final KakaoClient client = new KakaoClient(builder.build(), properties);

    @Test
    void exchangesFormEncodedCodeAndFetchesOnlyRequiredUserId() {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("client_id=client-id")))
                .andExpect(content().string(containsString("client_secret=client-secret")))
                .andExpect(content().string(containsString("code=authorization-code")))
                .andExpect(content().string(containsString(
                        "redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Flogin%2Foauth2%2Fcode%2Fkakao")))
                .andRespond(withSuccess("{\"access_token\":\"kakao-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-token"))
                .andRespond(withSuccess("{\"id\":123,\"kakao_account\":{}}", MediaType.APPLICATION_JSON));

        assertThat(client.getKakaoId("authorization-code")).isEqualTo(123L);
        server.verify();
    }

    @Test
    void clientSecretCanBeOmittedWhenNotConfigured() {
        var withoutSecret = new KakaoClient(builder.build(), new KakaoProperties(
                properties.clientId(), "", properties.redirectUri()));
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(content().string(not(containsString("client_secret="))))
                .andRespond(withSuccess("{\"access_token\":\"kakao-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withSuccess("{\"id\":123}", MediaType.APPLICATION_JSON));
        assertThat(withoutSecret.getKakaoId("code")).isEqualTo(123L);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"access_token\":null}", "{\"access_token\":\"\"}", "not-json"})
    void missingOrMalformedAccessTokenIsRejected(String body) {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertError(client, AuthErrorCode.KAKAO_TOKEN_REQUEST_FAILED);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"id\":null}", "{\"id\":0}", "{\"id\":-1}", "not-json"})
    void missingOrMalformedUserIdIsRejected(String body) {
        expectTokenSuccess();
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertError(client, AuthErrorCode.KAKAO_USER_INFO_REQUEST_FAILED);
        server.verify();
    }

    @Test
    void tokenApiHttpErrorIsMappedToBusinessException() {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertError(client, AuthErrorCode.KAKAO_TOKEN_REQUEST_FAILED);
        server.verify();
    }

    @Test
    void userApiHttpErrorIsMappedToBusinessException() {
        expectTokenSuccess();
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertError(client, AuthErrorCode.KAKAO_USER_INFO_REQUEST_FAILED);
        server.verify();
    }

    @Test
    void tokenApiCommunicationFailureIsMappedToBusinessException() {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withException(new IOException("simulated connection failure")));
        assertError(client, AuthErrorCode.KAKAO_TOKEN_REQUEST_FAILED);
        server.verify();
    }

    @Test
    void userApiCommunicationFailureIsMappedToBusinessException() {
        expectTokenSuccess();
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withException(new IOException("simulated connection failure")));
        assertError(client, AuthErrorCode.KAKAO_USER_INFO_REQUEST_FAILED);
        server.verify();
    }

    @Test
    void missingConfigurationIsRejectedWithoutMakingHttpRequests() {
        var missingConfiguration = new KakaoClient(builder.build(), new KakaoProperties("", "", ""));
        assertError(missingConfiguration, AuthErrorCode.KAKAO_CONFIGURATION_ERROR);
        server.verify();
    }

    private void expectTokenSuccess() {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withSuccess("{\"access_token\":\"kakao-token\"}", MediaType.APPLICATION_JSON));
    }

    private void assertError(KakaoClient target, AuthErrorCode expected) {
        assertThatThrownBy(() -> target.getKakaoId("authorization-code"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(expected));
    }
}
