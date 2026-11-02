package com.sdpcheck.sdpcheck.global.validation;

import com.sdpcheck.sdpcheck.domain.auth.dto.request.LoginRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.SignupRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    static Stream<String> validPasswords() {
        return Stream.of("password", "a".repeat(72), "가".repeat(24), "a".repeat(68) + "😀");
    }

    static Stream<String> invalidPasswords() {
        return Stream.of(null, "", " ".repeat(8), "short", "a".repeat(73),
                "가".repeat(25), "a".repeat(69) + "😀");
    }

    @ParameterizedTest
    @MethodSource("validPasswords")
    void acceptsPasswordsWithinBcryptLimit(String password) {
        assertThat(VALIDATOR.validate(new LoginRequest("member", password))).isEmpty();
        assertThat(VALIDATOR.validate(signup(password))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidPasswords")
    void rejectsInvalidPasswordsForBothRequests(String password) {
        assertThat(VALIDATOR.validate(new LoginRequest("member", password))).isNotEmpty();
        assertThat(VALIDATOR.validate(signup(password))).isNotEmpty();
    }

    private SignupRequest signup(String password) {
        return new SignupRequest("member", password, "회원", 17, "invite");
    }
}
