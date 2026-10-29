package com.sdpcheck.sdpcheck.domain.auth.service;

import com.sdpcheck.sdpcheck.domain.auth.config.KakaoProperties;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

class KakaoOAuthStateServiceTest {
    private static final String SECRET = "test-only-secret-key-for-ci-0123456789-abcdefghij";
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);
    private final KakaoOAuthStateService service = new KakaoOAuthStateService(SECRET, false, clock);

    @Test
    void statesAreDistinctAndValidOnlyWithMatchingCookie() {
        String state = service.createState();
        assertThat(service.createState()).isNotEqualTo(state);
        assertThatCode(() -> service.validate(state, state)).doesNotThrowAnyException();
        rejected(service, state, null);
        rejected(service, null, state);
        rejected(service, state, service.createState());
    }

    @Test
    void matchingButTamperedOrForgedCookieIsRejected() {
        String state = service.createState();
        String tampered = "x" + state.substring(1);
        rejected(service, tampered, tampered);
        String forged = new KakaoOAuthStateService("another-test-secret-key-0123456789-abcdefghij", false, clock).createState();
        rejected(service, forged, forged);
    }

    @Test
    void stateExpiresAfterFiveMinutes() {
        String state = service.createState();
        var later = new KakaoOAuthStateService(SECRET, false, Clock.offset(clock, java.time.Duration.ofMinutes(5)));
        rejected(later, state, state);
    }

    @Test
    void authTokensCannotServeAsStateAndStateCannotAuthenticateApis() {
        var jwt = new JwtProvider(SECRET, 60000, 120000);
        String refresh = jwt.createRefreshToken(1L);
        rejected(service, refresh, refresh);
        assertThat(jwt.validateAccessToken(service.createState())).isFalse();
        assertThat(jwt.validateRefreshToken(service.createState())).isFalse();
    }

    @Test
    void authorizationUrlEncodesConfigurationAndRejectsMissingClientId() {
        String url = service.authorizeUri(new KakaoProperties("client+id", "", "http://localhost/callback?x=1&y=2"), "state").toString();
        assertThat(url).contains("client_id=client%2Bid", "redirect_uri=http%3A%2F%2Flocalhost%2Fcallback%3Fx%3D1%26y%3D2", "state=state");
        assertThatThrownBy(() -> service.authorizeUri(new KakaoProperties("", "", "http://localhost"), "state"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.KAKAO_CONFIGURATION_ERROR));
    }

    private void rejected(KakaoOAuthStateService validator, String state, String cookie) {
        assertThatThrownBy(() -> validator.validate(state, cookie))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_OAUTH_STATE));
    }
}
