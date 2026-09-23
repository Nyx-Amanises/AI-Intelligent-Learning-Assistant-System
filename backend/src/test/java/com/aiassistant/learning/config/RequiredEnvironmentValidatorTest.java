package com.aiassistant.learning.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RequiredEnvironmentValidatorTest {

    @Test
    void passesWhenEverySecretIsProvidedAndLongEnough() {
        MockEnvironment environment = complete();

        assertDoesNotThrow(() -> RequiredEnvironmentValidator.validate(environment));
    }

    @Test
    void failsAndNamesTheMissingEnvironmentVariable() {
        MockEnvironment environment = complete();
        environment.setProperty("app.secret.encryption-key", "${APP_SECRET_ENCRYPTION_KEY}");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RequiredEnvironmentValidator.validate(environment));

        assertTrue(failure.getMessage().contains("APP_SECRET_ENCRYPTION_KEY"));
    }

    @Test
    void failsWhenAPlaceholderIsCompletelyAbsent() {
        MockEnvironment environment = complete();
        environment.setProperty("app.jwt.secret", "");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RequiredEnvironmentValidator.validate(environment));

        assertTrue(failure.getMessage().contains("APP_JWT_SECRET"));
    }

    @Test
    void failsWhenASecretIsTooShort() {
        MockEnvironment environment = complete();
        environment.setProperty("app.jwt.secret", "short");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RequiredEnvironmentValidator.validate(environment));

        assertTrue(failure.getMessage().contains("至少 32"));
    }

    private MockEnvironment complete() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.password", "db-password");
        environment.setProperty("app.jwt.secret", "jwt-secret-that-is-long-enough-0123456789");
        environment.setProperty("app.secret.encryption-key", "encryption-key-0123456789");
        return environment;
    }
}
