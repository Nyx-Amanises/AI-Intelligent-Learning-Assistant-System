package com.aiassistant.learning.dto.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RegisterRequestValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc12345", "ABCDEFG1", "abcdefgh1", "A123456789012345678!", "abc123!@"})
    void acceptsEightToTwentyCharactersWithLettersAndDigits(String password) {
        assertTrue(VALIDATOR.validate(request(password)).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc1234", "A1234567890123456789!", "abcdefgh", "12345678", "abc!@#$%", "        "})
    void rejectsInvalidPasswordLengthOrMissingCharacterType(String password) {
        assertTrue(VALIDATOR.validate(request(password)).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("password")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"        "})
    void requiresConfirmation(String confirmation) {
        RegisterRequest request = request("abc12345");
        request.setConfirmPassword(confirmation);

        assertFalse(VALIDATOR.validateProperty(request, "confirmPassword").isEmpty());
    }

    @Test
    void existingShortNumericPasswordRemainsValidForLogin() {
        LoginRequest request = new LoginRequest();
        request.setUsername("existing-user");
        request.setPassword("123456");

        assertTrue(VALIDATOR.validate(request).isEmpty());
    }

    private RegisterRequest request(String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("learner");
        request.setNickname("学习者");
        request.setPassword(password);
        request.setConfirmPassword(password);
        return request;
    }
}
