package com.sdpcheck.sdpcheck.global.security.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigCorsValidationTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "*", "https://*.example.com", "https://frontend.example.com/auth/callback",
            "https://frontend.example.com?query=value", "https://frontend.example.com#fragment",
            "https://user:password@frontend.example.com", "ftp://frontend.example.com",
            "https://frontend.example.com:65536"
    })
    void invalidOriginFailsConfigurationInsteadOfGrantingAccess(String origin) {
        assertThatThrownBy(() -> new SecurityConfig().corsConfigurationSource(origin))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
