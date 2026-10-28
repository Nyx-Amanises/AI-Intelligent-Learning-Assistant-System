package com.aiassistant.learning.dto.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ChangePasswordRequestValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @Test
    void oldPasswordKeepsLegacyShortFormat() {
        assertTrue(VALIDATOR.validate(request("123456", "abc12345")).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc1234", "A1234567890123456789!", "abcdefgh", "12345678"})
    void newPasswordFollowsRegistrationRules(String newPassword) {
        assertTrue(VALIDATOR.validate(request("123456", newPassword)).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("newPassword")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void requiresOldPassword(String oldPassword) {
        assertTrue(VALIDATOR.validate(request(oldPassword, "abc12345")).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("oldPassword")));
    }

    private ChangePasswordRequest request(String oldPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);
        request.setConfirmPassword(newPassword);
        return request;
    }
}
