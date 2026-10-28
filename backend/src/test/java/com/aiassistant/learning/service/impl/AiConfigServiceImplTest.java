package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.security.OutboundUrlGuard;
import com.aiassistant.learning.common.security.SecretCipher;
import com.aiassistant.learning.config.AiProperties;
import com.aiassistant.learning.dto.ai.AiConfigUpdateRequest;
import com.aiassistant.learning.entity.AiConfig;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.mapper.AiConfigMapper;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.AiConfigService.ResolvedAiConfig;
import com.aiassistant.learning.vo.ai.AiConfigVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AiConfigServiceImplTest {

    private static final long USER_ID = 7L;
    private static final String SHARED_CHAT_KEY = "shared-chat-test-key";
    private static final String SHARED_EMBEDDING_KEY = "shared-embedding-test-key";
    private static final String CUSTOM_EMBEDDING_URL = "https://custom-embedding.example.test";

    private final AiConfigMapper configs = mock(AiConfigMapper.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final AiProperties properties = new AiProperties();
    private final SecretCipher cipher = SecretCipher.forPassphrase("unit-test-encryption-key-0123456789");
    private final AiConfigServiceImpl service = new AiConfigServiceImpl(
            properties, new ObjectMapper(), configs, users, cipher, new OutboundUrlGuard(properties));

    @TempDir
    Path temporaryDirectory;

    private AiConfig global;
    private AiConfig personal;

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AiConfig.class);
    }

    @BeforeEach
    void setUp() {
        // Never read the running application's legacy config or contact a real database/provider.
        properties.setConfigFile(temporaryDirectory.resolve("absent-ai-config.json").toString());
        properties.setEnabled(true);
        properties.setMockMode(false);
        properties.setChatProviderType("OPENAI_COMPATIBLE");
        properties.setBaseUrl("https://shared-chat.example.test");
        properties.setChatPath("/v1/chat/completions");
        properties.setDefaultModel("test-chat-model");
        properties.setEmbeddingProviderType("OPENAI_COMPATIBLE");
        properties.setEmbeddingPath("/v1/embeddings");
        properties.setDefaultEmbeddingModel("test-embedding-model");

        global = config("GLOBAL", 0L);
        global.setApiKey(SHARED_CHAT_KEY);
        global.setEmbeddingApiKey(SHARED_EMBEDDING_KEY);

        when(configs.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<AiConfig> query = invocation.getArgument(0);
            query.getSqlSegment();
            Map<String, Object> parameters = query.getParamNameValuePairs();
            return parameters.containsValue("GLOBAL") ? global : personal;
        });
        when(configs.insert(any(AiConfig.class))).thenAnswer(invocation -> {
            AiConfig saved = invocation.getArgument(0);
            saved.setId(1L);
            if ("GLOBAL".equals(saved.getScope())) {
                global = saved;
            } else {
                personal = saved;
            }
            return 1;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"URL", "PATH", "MODEL", "PROVIDER"})
    void inheritedChatKeyDoesNotReachAnUnmatchedEmbeddingEndpoint(String changedField) {
        personal = config("USER", USER_ID);
        switch (changedField) {
            case "URL" -> personal.setEmbeddingBaseUrl(CUSTOM_EMBEDDING_URL);
            case "PATH" -> personal.setEmbeddingPath("/custom/embeddings");
            case "MODEL" -> personal.setDefaultEmbeddingModel("another-embedding-model");
            case "PROVIDER" -> personal.setEmbeddingProviderType("ARK_MULTIMODAL_TEXT");
            default -> throw new IllegalArgumentException(changedField);
        }

        ResolvedAiConfig resolved = service.getResolvedConfig(USER_ID);

        assertEquals(SHARED_CHAT_KEY, resolved.apiKey());
        assertNull(resolved.embeddingApiKey());
        assertFalse(service.getConfig(USER_ID).getEmbeddingApiKeyConfigured());
    }

    @Test
    void rejectsSavingACustomEmbeddingServiceWithoutAPersonalKey() {
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setEmbeddingBaseUrl(CUSTOM_EMBEDDING_URL);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.updateConfig(USER_ID, request));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("向量 API Key"));
        verify(configs, never()).insert(any(AiConfig.class));
        verify(configs, never()).updateById(any(AiConfig.class));
    }

    @Test
    void matchingEndpointsReuseSharedKeysWithoutPersistingThemInThePersonalRow() {
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setBaseUrl(" https://shared-chat.example.test/ ");
        request.setChatPath("v1/chat/completions");
        request.setEmbeddingBaseUrl("https://shared-embedding.example.test/");
        request.setEmbeddingPath("v1/embeddings");

        AiConfigVO result = service.updateConfig(USER_ID, request);

        assertEquals("USER", result.getConfigSource());
        assertTrue(result.getApiKeyConfigured());
        assertTrue(result.getEmbeddingApiKeyConfigured());
        assertNull(personal.getApiKey());
        assertNull(personal.getEmbeddingApiKey());
        assertEquals(SHARED_CHAT_KEY, service.getResolvedConfig(USER_ID).apiKey());
        assertEquals(SHARED_EMBEDDING_KEY, service.getResolvedConfig(USER_ID).embeddingApiKey());
    }

    @Test
    void sharedChatKeyIsLentToAnEmbeddingServiceOnTheSameHost() {
        global.setEmbeddingApiKey(null);
        global.setEmbeddingBaseUrl("https://shared-chat.example.test/");

        service.updateConfig(USER_ID, new AiConfigUpdateRequest());

        assertEquals(SHARED_CHAT_KEY, service.getResolvedConfig(USER_ID).embeddingApiKey());
        assertNull(personal.getEmbeddingApiKey());
    }

    @Test
    void explicitPersonalEmbeddingKeyWorksWithSharedChatAtACustomEndpoint() {
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setEmbeddingBaseUrl(CUSTOM_EMBEDDING_URL);
        request.setEmbeddingApiKey("personal-embedding-test-key");

        service.updateConfig(USER_ID, request);

        ResolvedAiConfig resolved = service.getResolvedConfig(USER_ID);
        assertEquals(SHARED_CHAT_KEY, resolved.apiKey());
        assertEquals("personal-embedding-test-key", resolved.embeddingApiKey());
        assertNull(personal.getApiKey());
        assertTrue(cipher.isEncrypted(personal.getEmbeddingApiKey()));
        assertEquals("personal-embedding-test-key", cipher.decrypt(personal.getEmbeddingApiKey()));
    }

    @Test
    void explicitPersonalChatKeyStillSupportsEmbeddingFallbackAtACustomEndpoint() {
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setApiKey("personal-chat-test-key");
        request.setEmbeddingBaseUrl(CUSTOM_EMBEDDING_URL);

        service.updateConfig(USER_ID, request);

        assertEquals("personal-chat-test-key", service.getResolvedConfig(USER_ID).embeddingApiKey());
        assertNull(personal.getEmbeddingApiKey());
    }

    @Test
    void sharedEmbeddingKeyRemainsAvailableWhenPersonalChatUsesAnotherProvider() {
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setChatProviderType("DEEPSEEK");
        request.setBaseUrl("https://personal-chat.example.test");
        request.setChatPath("/chat/completions");
        request.setDefaultModel("deepseek-chat");
        request.setApiKey("personal-chat-test-key");

        service.updateConfig(USER_ID, request);

        assertEquals(SHARED_EMBEDDING_KEY, service.getResolvedConfig(USER_ID).embeddingApiKey());
    }

    @Test
    void blankFieldsPreservePreviouslySavedPersonalKeysAndEndpoints() {
        personal = config("USER", USER_ID);
        personal.setApiKey("personal-chat-test-key");
        personal.setEmbeddingBaseUrl(CUSTOM_EMBEDDING_URL);
        personal.setEmbeddingApiKey("personal-embedding-test-key");
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setApiKey(" ");
        request.setEmbeddingApiKey("");
        request.setEmbeddingBaseUrl("");
        request.setEmbeddingPath("");
        request.setDefaultEmbeddingModel("");

        service.updateConfig(USER_ID, request);

        ResolvedAiConfig resolved = service.getResolvedConfig(USER_ID);
        assertEquals("personal-chat-test-key", resolved.apiKey());
        assertEquals("personal-embedding-test-key", resolved.embeddingApiKey());
        assertEquals(CUSTOM_EMBEDDING_URL, resolved.embeddingBaseUrl());
        assertEquals("/v1/embeddings", resolved.embeddingPath());
        assertEquals("test-embedding-model", resolved.defaultEmbeddingModel());
    }

    @Test
    void blankGlobalKeysRemainUnchangedOnAnAdminUpdate() {
        SysUser admin = new SysUser();
        admin.setRoleCode("ADMIN");
        when(users.selectById(USER_ID)).thenReturn(admin);
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setApiKey("");
        request.setEmbeddingApiKey(" ");

        service.updateGlobalConfig(USER_ID, request);

        assertEquals(SHARED_CHAT_KEY, cipher.decrypt(global.getApiKey()));
        assertEquals(SHARED_EMBEDDING_KEY, cipher.decrypt(global.getEmbeddingApiKey()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", " admin", "ADMIN "})
    void usernameAloneDoesNotGrantAdminRights(String username) {
        SysUser impostor = new SysUser();
        impostor.setUsername(username);
        impostor.setRoleCode("USER");
        when(users.selectById(USER_ID)).thenReturn(impostor);
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setBaseUrl("https://attacker.example.test");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateGlobalConfig(USER_ID, request));

        assertEquals(403, exception.getCode());
        verify(configs, never()).updateById(any(AiConfig.class));
        verify(configs, never()).insert(any(AiConfig.class));
        assertFalse(service.getConfig(USER_ID).getCanManageGlobal());
    }

    @Test
    void sharedChatKeyIsNeverLentToAnEmbeddingServiceOnAnotherHost() {
        global.setEmbeddingApiKey(null);
        global.setEmbeddingBaseUrl("https://attacker.example.test");

        ResolvedAiConfig resolved = service.getResolvedConfig(USER_ID);

        assertEquals(SHARED_CHAT_KEY, resolved.apiKey());
        assertNull(resolved.embeddingApiKey());
    }

    @Test
    void adminMovingTheSharedChatServiceToAnotherHostMustReenterTheKey() {
        actAsAdmin();
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setBaseUrl("https://attacker.example.test");
        request.setApiKey("");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateGlobalConfig(USER_ID, request));

        assertTrue(exception.getMessage().contains("请重新填写对应的 API Key"));
        assertTrue(exception.getMessage().contains("attacker.example.test"));
        verify(configs, never()).updateById(any(AiConfig.class));
        assertEquals(SHARED_CHAT_KEY, cipher.decrypt(global.getApiKey()));
    }

    @Test
    void adminMovingTheSharedEmbeddingServiceToAnotherHostMustReenterTheEmbeddingKey() {
        actAsAdmin();
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setEmbeddingBaseUrl("https://attacker.example.test");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateGlobalConfig(USER_ID, request));

        assertTrue(exception.getMessage().contains("向量服务地址已改为"));
        verify(configs, never()).updateById(any(AiConfig.class));
    }

    @Test
    void movingToAnotherHostInMockModeDropsTheOldKeyInsteadOfCarryingItOver() {
        actAsAdmin();
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setMockMode(true);
        request.setBaseUrl("https://attacker.example.test");

        service.updateGlobalConfig(USER_ID, request);

        assertNull(global.getApiKey());
        assertEquals(SHARED_EMBEDDING_KEY, cipher.decrypt(global.getEmbeddingApiKey()));
    }

    @Test
    void changesOnTheSameHostKeepTheSavedSharedKeys() {
        actAsAdmin();
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setBaseUrl("https://SHARED-CHAT.example.test:443/");
        request.setChatPath("/v2/chat/completions");
        request.setDefaultModel("another-chat-model");

        service.updateGlobalConfig(USER_ID, request);

        assertEquals(SHARED_CHAT_KEY, cipher.decrypt(global.getApiKey()));
        assertEquals("another-chat-model", global.getDefaultModel());
    }

    @Test
    void environmentKeyIsNotPairedWithASavedSharedUrlOnAnotherHost() {
        properties.setApiKey("environment-test-key");
        global.setApiKey(null);
        global.setBaseUrl("https://attacker.example.test");

        // 共享配置没有可用 Key，整体退回环境变量配置：环境 Key 只会发往环境变量里的地址。
        ResolvedAiConfig resolved = service.getResolvedConfig(USER_ID);
        assertEquals("https://shared-chat.example.test", resolved.baseUrl());
        assertEquals("environment-test-key", resolved.apiKey());

        global.setBaseUrl(null);
        assertEquals("environment-test-key", service.getResolvedConfig(USER_ID).apiKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://169.254.169.254", "http://127.0.0.1:8083", "http://192.168.0.10", "file:///etc"})
    void personalConfigCannotPointAtInternalOrNonHttpAddresses(String baseUrl) {
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setMockMode(true);
        request.setBaseUrl(baseUrl);

        assertThrows(BusinessException.class, () -> service.updateConfig(USER_ID, request));
        verify(configs, never()).insert(any(AiConfig.class));
    }

    @Test
    void embeddingPathCannotSmuggleInAnotherHost() {
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setEmbeddingPath("http://169.254.169.254/latest/meta-data");
        request.setEmbeddingApiKey("personal-embedding-test-key");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.updateConfig(USER_ID, request));

        assertTrue(exception.getMessage().contains("接口路径"));
        verify(configs, never()).insert(any(AiConfig.class));
    }

    @Test
    void sharedConfigCannotPointAtInternalAddressesEither() {
        actAsAdmin();
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setBaseUrl("http://10.1.2.3");
        request.setApiKey("new-shared-key");

        assertThrows(BusinessException.class, () -> service.updateGlobalConfig(USER_ID, request));
        verify(configs, never()).updateById(any(AiConfig.class));
    }

    private void actAsAdmin() {
        SysUser admin = new SysUser();
        admin.setRoleCode("ADMIN");
        when(users.selectById(USER_ID)).thenReturn(admin);
    }

    @ParameterizedTest
    @CsvSource({"true, true", "false, false", "false, true"})
    void mockOrDisabledConfigCanBeSavedWithoutForwardingSharedKeys(boolean enabled, boolean mockMode) {
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setEnabled(enabled);
        request.setMockMode(mockMode);
        request.setEmbeddingBaseUrl(CUSTOM_EMBEDDING_URL);

        service.updateConfig(USER_ID, request);

        ResolvedAiConfig resolved = service.getResolvedConfig(USER_ID);
        assertEquals(enabled, resolved.enabled());
        assertEquals(mockMode, resolved.mockMode());
        assertNull(resolved.embeddingApiKey());
    }

    @Test
    void defaultPlaceholderIsNotShownAsAConfiguredKey() {
        global = null;
        properties.setMockMode(true);
        properties.setApiKey("replace-with-your-api-key");

        AiConfigVO result = service.getConfig(USER_ID);

        assertFalse(result.getApiKeyConfigured());
        assertFalse(result.getEmbeddingApiKeyConfigured());
        assertEquals("", result.getApiKeyPreview());
        assertEquals("", result.getEmbeddingApiKeyPreview());
        assertNull(service.getResolvedConfig(USER_ID).apiKey());
        assertNull(service.getResolvedConfig(USER_ID).embeddingApiKey());
    }

    @Test
    void aPlaceholderCannotEnableRealCallsWithoutAPersonalOrSharedKey() {
        global = null;
        properties.setMockMode(true);
        properties.setApiKey("replace-with-your-api-key");
        AiConfigUpdateRequest request = new AiConfigUpdateRequest();
        request.setEnabled(true);
        request.setMockMode(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.updateConfig(USER_ID, request));

        assertTrue(exception.getMessage().contains("API Key"));
        verify(configs, never()).insert(any(AiConfig.class));
    }

    @Test
    void aStoredEmbeddingPlaceholderFallsBackToTheValidChatKey() {
        global.setEmbeddingApiKey("replace-with-your-api-key");
        global.setEmbeddingBaseUrl("https://shared-chat.example.test");

        ResolvedAiConfig result = service.getResolvedConfig(USER_ID);

        assertEquals(SHARED_CHAT_KEY, result.embeddingApiKey());
    }

    @Test
    void unDecryptableKeyFromMismatchedPassphraseDoesNotCrashConfigPage() {
        global = null;
        SecretCipher otherCipher = SecretCipher.forPassphrase("completely-different-key-123456789");
        AiConfig corrupted = new AiConfig();
        corrupted.setScope("USER");
        corrupted.setUserId(USER_ID);
        corrupted.setEnabled(true);
        corrupted.setMockMode(true);
        corrupted.setApiKey(otherCipher.encrypt("sk-old-key"));
        personal = corrupted;

        AiConfigVO result = assertDoesNotThrow(() -> service.getConfig(USER_ID));
        assertNotNull(result);
        assertFalse(result.getApiKeyConfigured());
        assertFalse(result.getEmbeddingApiKeyConfigured());
    }

    private AiConfig config(String scope, long userId) {
        AiConfig config = new AiConfig();
        config.setId(1L);
        config.setScope(scope);
        config.setUserId(userId);
        config.setEnabled(true);
        config.setMockMode(false);
        config.setChatProviderType("OPENAI_COMPATIBLE");
        config.setBaseUrl("https://shared-chat.example.test");
        config.setChatPath("/v1/chat/completions");
        config.setDefaultModel("test-chat-model");
        config.setEmbeddingProviderType("OPENAI_COMPATIBLE");
        config.setEmbeddingBaseUrl("https://shared-embedding.example.test");
        config.setEmbeddingPath("/v1/embeddings");
        config.setDefaultEmbeddingModel("test-embedding-model");
        return config;
    }
}
