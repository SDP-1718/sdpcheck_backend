package com.sdpcheck.sdpcheck.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sdpcheck.sdpcheck.domain.auth.config.KakaoProperties;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class KakaoClient {

    private final RestClient restClient;
    private final KakaoProperties properties;

    @Autowired
    public KakaoClient(
            RestClient.Builder builder,
            KakaoProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = builder.clone()
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
    }

    KakaoClient(RestClient restClient, KakaoProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public Long getKakaoId(String authorizationCode) {
        validateConfiguration();

        if (!StringUtils.hasText(authorizationCode)) {
            throw new BusinessException(
                    AuthErrorCode.KAKAO_TOKEN_REQUEST_FAILED);
        }

        String accessToken = requestAccessToken(authorizationCode);
        return requestKakaoId(accessToken);
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.clientId())
                || !StringUtils.hasText(properties.redirectUri())) {
            throw new BusinessException(
                    AuthErrorCode.KAKAO_CONFIGURATION_ERROR);
        }
    }

    private String requestAccessToken(String authorizationCode) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", authorizationCode);

        if (StringUtils.hasText(properties.clientSecret())) {
            form.add("client_secret", properties.clientSecret());
        }

        KakaoTokenResponse response;

        try {
            response = restClient.post()
                    .uri("https://kauth.kakao.com/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
        } catch (RestClientException exception) {
            throw new BusinessException(
                    AuthErrorCode.KAKAO_TOKEN_REQUEST_FAILED,
                    exception);
        }

        if (response == null
                || !StringUtils.hasText(response.accessToken())) {
            throw new BusinessException(
                    AuthErrorCode.KAKAO_TOKEN_REQUEST_FAILED);
        }

        return response.accessToken();
    }

    private Long requestKakaoId(String accessToken) {
        KakaoUserResponse response;

        try {
            response = restClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(KakaoUserResponse.class);
        } catch (RestClientException exception) {
            throw new BusinessException(
                    AuthErrorCode.KAKAO_USER_INFO_REQUEST_FAILED,
                    exception);
        }

        if (response == null
                || response.id() == null
                || response.id() <= 0) {
            throw new BusinessException(
                    AuthErrorCode.KAKAO_USER_INFO_REQUEST_FAILED);
        }

        return response.id();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoUserResponse(Long id) {
    }
}
