package com.aiassistant.learning.common.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.AiProperties;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutboundUrlGuardTest {

    private final AiProperties properties = new AiProperties();
    private final OutboundUrlGuard guard = new OutboundUrlGuard(properties);

    @BeforeEach
    void setUp() {
        properties.setAllowPrivateNetwork(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8083/api",
            "http://localhost/v1",
            "http://10.0.0.5/v1",
            "http://172.16.1.1/v1",
            "http://192.168.1.10/v1",
            "http://169.254.169.254/latest/meta-data",
            "http://100.100.100.200/latest/meta-data",
            "http://0.0.0.0/",
            "http://[::1]/v1",
            "http://[fd00::1]/v1",
            "http://[::ffff:127.0.0.1]/v1"
    })
    void rejectsLoopbackPrivateAndMetadataAddresses(String url) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.checkRequestTarget(URI.create(url), "AI 接口地址"));
        assertTrue(exception.getMessage().contains("内网"), exception.getMessage());
        assertThrows(BusinessException.class, () -> guard.validateConfiguredUrl(url, "聊天服务地址"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://2130706433/v1", "http://0x7f.0.0.1/v1"})
    void obfuscatedLoopbackFormsNeverReachTheNetwork(String url) {
        // 这些写法要么被解析成 127.0.0.1 判为内网，要么被当作域名而解析失败，两种情况都不会发出请求。
        assertThrows(BusinessException.class, () -> guard.checkRequestTarget(URI.create(url), "AI 接口地址"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://8.8.8.8/x", "gopher://8.8.8.8/", "jar:file:/a!/b"})
    void rejectsSchemesOtherThanHttpAndHttps(String url) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.validateConfiguredUrl(url, "聊天服务地址"));
        assertTrue(exception.getMessage().contains("http"));
    }

    @Test
    void rejectsCredentialsEmbeddedInTheUrl() {
        assertThrows(BusinessException.class,
                () -> guard.validateConfiguredUrl("https://user:pass@8.8.8.8/v1", "聊天服务地址"));
    }

    @Test
    void acceptsPublicAddresses() {
        assertDoesNotThrow(() -> guard.checkRequestTarget(URI.create("https://8.8.8.8/v1/chat/completions"), "AI 接口地址"));
        assertDoesNotThrow(() -> guard.checkRequestTarget(URI.create("https://[2001:4860:4860::8888]/v1"), "AI 接口地址"));
    }

    @Test
    void unresolvableDomainsCanBeSavedButAreRejectedWhenRequested() {
        assertDoesNotThrow(() -> guard.validateConfiguredUrl("https://model.nothing-here.invalid", "聊天服务地址"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.checkRequestTarget(URI.create("https://model.nothing-here.invalid/v1"), "AI 接口地址"));
        assertTrue(exception.getMessage().contains("无法解析"));
    }

    @Test
    void privateNetworkCanBeExplicitlyAllowedForLocalModelServers() {
        properties.setAllowPrivateNetwork(true);

        assertDoesNotThrow(() -> guard.checkRequestTarget(URI.create("http://127.0.0.1:11434/v1/chat/completions"), "AI 接口地址"));
        assertThrows(BusinessException.class, () -> guard.validateConfiguredUrl("file:///etc/passwd", "聊天服务地址"));
    }

    @Test
    void pathCannotMoveTheRequestToAnotherHost() {
        URI joined = OutboundUrlGuard.joinUrl("https://api.openai.com/", "@169.254.169.254/latest", "AI 接口地址");
        assertEquals("api.openai.com", joined.getHost());
        assertEquals("https://api.openai.com/@169.254.169.254/latest", joined.toString());

        assertThrows(BusinessException.class,
                () -> OutboundUrlGuard.joinUrl("https://api.openai.com", "http://169.254.169.254/latest", "AI 接口地址"));
    }

    @Test
    void joinKeepsExactlyOneSlashBetweenBaseAndPath() {
        assertEquals("https://ark.example.com/api/v3/embeddings",
                OutboundUrlGuard.joinUrl("https://ark.example.com/api/v3/", "/embeddings", "x").toString());
        assertEquals("https://ark.example.com/api/v3/embeddings",
                OutboundUrlGuard.joinUrl("https://ark.example.com/api/v3", "embeddings", "x").toString());
    }
}
