package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.AiStreamAbortedException;
import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.security.OutboundUrlGuard;
import com.aiassistant.learning.service.AiChatService.ChatMessage;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AiConfigService.ResolvedAiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** 使用本地真实 HTTP 服务验证发给两种模型接口的会话结构。 */
@Timeout(15)
class AiChatConversationHttpTest {

    private static final String SYSTEM = "系统指令：只使用当前课程。\n请保留历史角色。";
    private static final List<ChatMessage> HISTORY = List.of(
            new ChatMessage("user", "请讲解多选题。\n保留中文和换行。"),
            new ChatMessage("assistant", "正确答案是 A、C，下面给出原因。"),
            new ChatMessage("user", "  继续解释上述例子：为什么不选 B？\n"));

    private final ObjectMapper mapper = new ObjectMapper();
    private final AiConfigService configs = mock(AiConfigService.class);
    // 仅测试实例放行环回地址；生产 OutboundUrlGuard 的内网限制保持不变。
    private final OutboundUrlGuard guard = mock(OutboundUrlGuard.class);
    private final LinkedBlockingQueue<CapturedRequest> requests = new LinkedBlockingQueue<>();
    private final AtomicReference<StubResponse> response = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                requests.add(new CapturedRequest(
                        exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        mapper.readTree(exchange.getRequestBody())));
                StubResponse stub = response.get();
                byte[] bytes = stub.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", stub.contentType());
                exchange.sendResponseHeaders(stub.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @ParameterizedTest(name = "responses={0}, stream={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void sendsOrderedConversationAndSeparateSystemInstructions(boolean responses, boolean stream) throws Exception {
        AiChatServiceImpl service = service(responses);
        setSuccessResponse(responses, stream);

        assertEquals("历史已收到", invoke(service, SYSTEM, HISTORY, stream));

        CapturedRequest request = takeRequest();
        assertEquals("POST", request.method());
        assertEquals(path(responses), request.path());
        assertEquals("Bearer test-key", request.authorization());
        assertEquals("application/json", request.contentType());
        assertEquals("test-model", request.body().path("model").asText());
        assertEquals(0.3, request.body().path("temperature").asDouble());
        assertEquals(stream, request.body().path("stream").asBoolean(false));
        assertConversation(request.body(), responses, SYSTEM, HISTORY);
        verify(guard).checkRequestTarget(URI.create(baseUrl() + path(responses)), "AI 接口地址");
    }

    @ParameterizedTest(name = "single turn: responses={0}, stream={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void retainsSingleTurnOverloadsAndDefaultTemperature(boolean responses, boolean stream) throws Exception {
        AiChatServiceImpl service = service(responses);
        setSuccessResponse(responses, stream);
        String userPrompt = "  单轮提问：勾选 A 和 C\n请解释。  ";
        String result;

        if (stream) {
            StringBuilder text = new StringBuilder();
            service.streamChat(SYSTEM, userPrompt, " test-model ", null, text::append);
            result = text.toString();
        } else {
            result = service.chat(SYSTEM, userPrompt, " test-model ", null);
        }

        assertEquals("历史已收到", result);
        JsonNode body = takeRequest().body();
        assertEquals(0.7, body.path("temperature").asDouble());
        assertEquals("test-model", body.path("model").asText());
        assertConversation(body, responses, SYSTEM, List.of(new ChatMessage("user", userPrompt)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void omitsBlankSystemInstructionWithoutAddingItToHistory(boolean responses) throws Exception {
        AiChatServiceImpl service = service(responses);
        setSuccessResponse(responses, false);

        service.chat(" \n ", HISTORY, "test-model", 0.3);

        assertConversation(takeRequest().body(), responses, null, HISTORY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"system", "developer", "tool", "function", "unknown", "  "})
    void rejectsPrivilegedOrUnknownHistoryRoles(String role) {
        assertThrows(IllegalArgumentException.class, () -> new ChatMessage(role, "历史文本"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void rejectsMissingHistoryContent(String content) {
        assertThrows(IllegalArgumentException.class, () -> new ChatMessage("user", content));
    }

    @Test
    void normalizesRolesWithoutChangingMessageContent() {
        ChatMessage message = new ChatMessage(" ASSISTANT ", " \n中文原文  ");
        assertEquals("assistant", message.role());
        assertEquals(" \n中文原文  ", message.content());
        assertEquals("user", new ChatMessage(" USER ", "问题").role());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsMissingConversationBeforeAnyOutboundRequest(boolean stream) {
        AiChatServiceImpl service = service(false);
        List<List<ChatMessage>> invalidHistories = Arrays.asList(null, List.of(), Arrays.asList(HISTORY.get(0), null));

        for (List<ChatMessage> history : invalidHistories) {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> invoke(service, SYSTEM, history, stream));
            assertEquals("AI 会话消息不能为空", exception.getMessage());
        }

        assertTrue(requests.isEmpty());
        verifyNoInteractions(guard);
    }

    @ParameterizedTest(name = "provider error: responses={0}, stream={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void reportsProviderMessageWithoutEchoingOtherFields(boolean responses, boolean stream) throws Exception {
        AiChatServiceImpl service = service(responses);
        response.set(new StubResponse(400, "application/json", "{\"error\":{\"message\":\"模型暂不可用\"},\"secret\":\"must-not-leak\"}"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> invoke(service, SYSTEM, HISTORY, stream));

        assertEquals("调用 AI 接口失败: HTTP 400 模型暂不可用", exception.getMessage());
        assertFalse(exception.getMessage().contains("must-not-leak"));
        assertConversation(takeRequest().body(), responses, SYSTEM, HISTORY);
    }

    @ParameterizedTest(name = "non-JSON error: responses={0}, stream={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void doesNotExposeRawErrorPageFromRealHttpResponse(boolean responses, boolean stream) {
        AiChatServiceImpl service = service(responses);
        response.set(new StubResponse(502, "text/html", "<html>internal-secret: must-not-leak</html>"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> invoke(service, SYSTEM, HISTORY, stream));

        assertEquals("调用 AI 接口失败: HTTP 502", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void propagatesStreamAbortAndStopsDeliveringFurtherDeltas(boolean responses) throws Exception {
        AiChatServiceImpl service = service(responses);
        setSuccessResponse(responses, true);
        AiStreamAbortedException aborted = new AiStreamAbortedException("用户停止生成");
        List<String> received = new ArrayList<>();

        AiStreamAbortedException actual = assertThrows(AiStreamAbortedException.class,
                () -> service.streamChat(SYSTEM, HISTORY, "test-model", 0.3, delta -> {
                    received.add(delta);
                    throw aborted;
                }));

        assertSame(aborted, actual);
        assertEquals(List.of("历史"), received);
        assertConversation(takeRequest().body(), responses, SYSTEM, HISTORY);
    }

    private AiChatServiceImpl service(boolean responses) {
        when(configs.getResolvedConfig()).thenReturn(new ResolvedAiConfig(
                true, false, true, true, "OPENAI_COMPATIBLE", baseUrl(), path(responses),
                "OPENAI_COMPATIBLE", baseUrl(), "/v1/embeddings", "test-key", "test-key", "test-model", "test-embedding-model"));
        return new AiChatServiceImpl(configs, mapper, guard);
    }

    private String invoke(AiChatServiceImpl service, String system, List<ChatMessage> history, boolean stream) {
        if (!stream) return service.chat(system, history, " test-model ", 0.3);
        StringBuilder text = new StringBuilder();
        service.streamChat(system, history, " test-model ", 0.3, text::append);
        return text.toString();
    }

    private void assertConversation(JsonNode body, boolean responses, String system, List<ChatMessage> history) {
        JsonNode messages;
        int offset;
        if (responses) {
            assertFalse(body.has("messages"));
            if (system == null) assertFalse(body.has("instructions"));
            else assertEquals(system, body.path("instructions").asText());
            messages = body.path("input");
            offset = 0;
        } else {
            assertFalse(body.has("instructions"));
            assertFalse(body.has("input"));
            messages = body.path("messages");
            offset = system == null ? 0 : 1;
            if (system != null) {
                assertEquals("system", messages.path(0).path("role").asText());
                assertEquals(system, messages.path(0).path("content").asText());
            }
        }
        assertTrue(messages.isArray());
        assertEquals(history.size() + offset, messages.size());
        for (int i = 0; i < history.size(); i++) {
            assertEquals(history.get(i).role(), messages.path(i + offset).path("role").asText());
            assertEquals(history.get(i).content(), messages.path(i + offset).path("content").asText());
        }
    }

    private void setSuccessResponse(boolean responses, boolean stream) {
        if (stream) {
            String first = responses
                    ? "{\"type\":\"response.output_text.delta\",\"delta\":\"历史\"}"
                    : "{\"choices\":[{\"delta\":{\"content\":\"历史\"}}]}";
            String second = responses
                    ? "{\"type\":\"response.output_text.delta\",\"delta\":\"已收到\"}"
                    : "{\"choices\":[{\"delta\":{\"content\":\"已收到\"}}]}";
            response.set(new StubResponse(200, "text/event-stream; charset=utf-8",
                    ": keepalive\n\ndata: " + first + "\n\ndata: " + second + "\n\ndata: [DONE]\n\n"));
        } else {
            String body = responses
                    ? "{\"output\":[{\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"历史已收到\"}]}]}"
                    : "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"历史已收到\"}}]}";
            response.set(new StubResponse(200, "application/json; charset=utf-8", body));
        }
    }

    private CapturedRequest takeRequest() throws InterruptedException {
        CapturedRequest request = requests.poll(2, TimeUnit.SECONDS);
        assertNotNull(request, "应向本地 HTTP 服务发送请求");
        return request;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String path(boolean responses) {
        return responses ? "/v1/responses" : "/v1/chat/completions";
    }

    private record CapturedRequest(String method, String path, String authorization, String contentType, JsonNode body) {}

    private record StubResponse(int status, String contentType, String body) {}
}
