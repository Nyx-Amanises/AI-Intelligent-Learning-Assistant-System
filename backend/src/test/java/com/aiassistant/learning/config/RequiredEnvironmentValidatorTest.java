package com.aiassistant.learning.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class RequiredEnvironmentValidatorTest {

    @Test
    void strictMode_passesWhenEverySecretIsProvidedAndLongEnough() {
        MockEnvironment environment = completeStrict();

        assertDoesNotThrow(() -> RequiredEnvironmentValidator.validate(environment));
    }

    @Test
    void strictMode_failsAndNamesTheMissingEnvironmentVariable() {
        MockEnvironment environment = completeStrict();
        environment.setProperty("app.secret.encryption-key", "${APP_SECRET_ENCRYPTION_KEY}");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RequiredEnvironmentValidator.validate(environment));

        assertTrue(failure.getMessage().contains("APP_SECRET_ENCRYPTION_KEY"));
    }

    @Test
    void strictMode_failsWhenAPlaceholderIsCompletelyAbsent() {
        MockEnvironment environment = completeStrict();
        environment.setProperty("app.jwt.secret", "");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RequiredEnvironmentValidator.validate(environment));

        assertTrue(failure.getMessage().contains("APP_JWT_SECRET"));
    }

    @Test
    void strictMode_failsWhenASecretIsTooShort() {
        MockEnvironment environment = completeStrict();
        environment.setProperty("app.jwt.secret", "short");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RequiredEnvironmentValidator.validate(environment));

        assertTrue(failure.getMessage().contains("至少 32"));
    }

    @Test
    void strictMode_enabledByProdProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.password", "db-password");
        environment.setProperty("app.secret.encryption-key", "encryption-key-0123456789");
        // app.jwt.secret 缺失

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RequiredEnvironmentValidator.validate(environment));

        assertTrue(failure.getMessage().contains("APP_JWT_SECRET"));
    }

    @Test
    void devMode_autoGeneratesJwtSecretAndPersistsEncryptionKey(@TempDir Path tempDir) throws IOException {
        MockEnvironment environment = new MockEnvironment();
        Path keyFile = tempDir.resolve("secret-encryption.key");
        environment.setProperty("app.secret.key-file", keyFile.toString());

        assertDoesNotThrow(() -> RequiredEnvironmentValidator.validate(environment));

        // 验证 JWT 随机生成并注入环境
        String jwtSecret = environment.getProperty("app.jwt.secret");
        assertNotNull(jwtSecret);
        assertTrue(jwtSecret.length() >= 32);
        assertEquals(jwtSecret, environment.getProperty("APP_JWT_SECRET"));

        // 验证加密口令自动生成、注入环境并落盘
        String encryptionKey = environment.getProperty("app.secret.encryption-key");
        assertNotNull(encryptionKey);
        assertTrue(encryptionKey.length() >= 16);
        assertEquals(encryptionKey, environment.getProperty("APP_SECRET_ENCRYPTION_KEY"));

        assertTrue(Files.isRegularFile(keyFile));
        String fileContent = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
        assertEquals(encryptionKey, fileContent);
    }

    @Test
    void devMode_reusesExistingPersistedEncryptionKey(@TempDir Path tempDir) throws IOException {
        MockEnvironment environment = new MockEnvironment();
        Path keyFile = tempDir.resolve("secret-encryption.key");
        String existingKey = "existing-persisted-secret-key-123456";
        Files.writeString(keyFile, existingKey, StandardCharsets.UTF_8);

        environment.setProperty("app.secret.key-file", keyFile.toString());

        assertDoesNotThrow(() -> RequiredEnvironmentValidator.validate(environment));

        assertEquals(existingKey, environment.getProperty("app.secret.encryption-key"));
        assertEquals(existingKey, environment.getProperty("APP_SECRET_ENCRYPTION_KEY"));
        // 确认文件内容未被篡改
        assertEquals(existingKey, Files.readString(keyFile, StandardCharsets.UTF_8).trim());
    }

    @Test
    void devMode_doesNotOverwriteExistingConfiguredSecrets(@TempDir Path tempDir) {
        MockEnvironment environment = new MockEnvironment();
        Path keyFile = tempDir.resolve("secret-encryption.key");
        environment.setProperty("app.secret.key-file", keyFile.toString());

        String userJwt = "my-custom-jwt-secret-that-is-at-least-32-chars-long";
        String userKey = "my-custom-encryption-passphrase-123";
        environment.setProperty("app.jwt.secret", userJwt);
        environment.setProperty("app.secret.encryption-key", userKey);

        assertDoesNotThrow(() -> RequiredEnvironmentValidator.validate(environment));

        assertEquals(userJwt, environment.getProperty("app.jwt.secret"));
        assertEquals(userKey, environment.getProperty("app.secret.encryption-key"));
        // 未写入文件
        assertTrue(Files.notExists(keyFile));
    }

    private MockEnvironment completeStrict() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(RequiredEnvironmentValidator.STRICT_MODE_KEY, "true");
        environment.setProperty("spring.datasource.password", "db-password");
        environment.setProperty("app.jwt.secret", "jwt-secret-that-is-long-enough-0123456789");
        environment.setProperty("app.secret.encryption-key", "encryption-key-0123456789");
        return environment;
    }
}
