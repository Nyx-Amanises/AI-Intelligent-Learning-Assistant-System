package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.security.OutboundUrlGuard;
import com.aiassistant.learning.config.AiProperties;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AiConfigService.ResolvedAiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 出站请求防护：AI 服务地址指向内网时不发请求，错误响应不回显原始内容。
 */
class AiOutboundRequestProtectionTest {

    private final AiConfigService configs = mock(AiConfigService.class);
    private final AiProperties properties = new AiProperties();
    private final OutboundUrlGuard guard = new OutboundUrlGuard(properties);

    @Test
    void chatRequestsToInternalAddressesAreBlockedBeforeTheKeyIsSent() {
        when(configs.getResolvedConfig()).thenReturn(config("http://169.254.169.254", "/latest/meta-data"));
        AiChatServiceImpl chat = new AiChatServiceImpl(configs, new ObjectMapper(), guard);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chat.chat("system", "user", "model", 0.2));

        assertTrue(exception.getMessage().contains("内网"), exception.getMessage());
    }

    @Test
    void embeddingRequestsToInternalAddressesAreBlocked() {
        when(configs.getResolvedConfig()).thenReturn(config("http://10.0.0.8:6333", "/collections"));
        AiTextEmbeddingServiceImpl embeddings = new AiTextEmbeddingServiceImpl(configs, guard);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> embeddings.embedTexts(List.of("文本"), null));

        assertTrue(exception.getMessage().contains("内网"), exception.getMessage());
    }

    @Test
    void providerErrorsOnlyExposeTheProviderMessageField() {
        assertEquals("HTTP 401 Incorrect API key provided",
                AiProviderErrorMessages.describe(401, "{\"error\":{\"message\":\"Incorrect API key provided\"}}"));
        assertEquals("HTTP 400 bad model", AiProviderErrorMessages.describe(400, "{\"message\":\"bad model\"}"));
        assertEquals("HTTP 403 forbidden", AiProviderErrorMessages.describe(403, "{\"error\":\"forbidden\"}"));
    }

    @Test
    void nonJsonOrUnknownErrorBodiesAreNotEchoed() {
        String internalPage = "<html><body>root:x:0:0:root:/root:/bin/bash ami-id instance-id</body></html>";
        assertEquals("HTTP 404", AiProviderErrorMessages.describe(404, internalPage));
        assertEquals("HTTP 500", AiProviderErrorMessages.describe(500, "{\"secretToken\":\"abc\",\"data\":[1,2]}"));
        assertFalse(AiProviderErrorMessages.describe(500, "{\"message\":\"" + "长".repeat(400) + "\"}").length() > 220);
    }

    private ResolvedAiConfig config(String baseUrl, String path) {
        return new ResolvedAiConfig(
                true, false, true, true, "OPENAI_COMPATIBLE", baseUrl, path,
                "OPENAI_COMPATIBLE", baseUrl, path, "test-key", "test-key", "test-model", "test-embedding-model");
    }
}
