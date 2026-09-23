package com.aiassistant.learning.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.entity.AiConfig;
import com.aiassistant.learning.mapper.AiConfigMapper;
import com.aiassistant.learning.service.task.AiConfigSecretMigrationRunner;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private final SecretCipher cipher = SecretCipher.forPassphrase("unit-test-encryption-key-0123456789");

    @Test
    void roundTripsAndNeverStoresPlainText() {
        String stored = cipher.encrypt("sk-live-1234567890");

        assertTrue(cipher.isEncrypted(stored));
        assertTrue(stored.startsWith(SecretCipher.PREFIX));
        assertFalse(stored.contains("sk-live"));
        assertEquals("sk-live-1234567890", cipher.decrypt(stored));
    }

    @Test
    void usesARandomIvSoEqualSecretsProduceDifferentCipherText() {
        assertNotEquals(cipher.encrypt("same-secret"), cipher.encrypt("same-secret"));
    }

    @Test
    void blankValuesAndLegacyPlainTextPassThrough() {
        assertNull(cipher.encrypt(null));
        assertNull(cipher.decrypt(null));
        assertEquals("", cipher.encrypt(""));
        assertEquals("legacy-plain-key", cipher.decrypt("legacy-plain-key"));
        String encrypted = cipher.encrypt("once");
        assertEquals(encrypted, cipher.encrypt(encrypted));
    }

    @Test
    void aDifferentPassphraseCannotDecrypt() {
        String stored = cipher.encrypt("sk-live-1234567890");
        SecretCipher other = SecretCipher.forPassphrase("another-encryption-key-9876543210");

        assertThrows(BusinessException.class, () -> other.decrypt(stored));
    }

    @Test
    void startupMigrationEncryptsOnlyLegacyPlainTextRows() {
        AiConfigMapper mapper = mock(AiConfigMapper.class);
        AiConfig legacy = new AiConfig();
        legacy.setId(1L);
        legacy.setApiKey("plain-chat-key");
        legacy.setEmbeddingApiKey(cipher.encrypt("already-encrypted"));
        AiConfig clean = new AiConfig();
        clean.setId(2L);
        clean.setApiKey(cipher.encrypt("chat"));
        AiConfig empty = new AiConfig();
        empty.setId(3L);
        when(mapper.selectList(isNull())).thenReturn(List.of(legacy, clean, empty));

        int migrated = new AiConfigSecretMigrationRunner(mapper, cipher).encryptPlainTextKeys();

        assertEquals(1, migrated);
        verify(mapper).updateById(legacy);
        verify(mapper, never()).updateById(clean);
        verify(mapper, never()).updateById(empty);
        assertTrue(cipher.isEncrypted(legacy.getApiKey()));
        assertEquals("plain-chat-key", cipher.decrypt(legacy.getApiKey()));
        assertEquals("already-encrypted", cipher.decrypt(legacy.getEmbeddingApiKey()));
    }
}
