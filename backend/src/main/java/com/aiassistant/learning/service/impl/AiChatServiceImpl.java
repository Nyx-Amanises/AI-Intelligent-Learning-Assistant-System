package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiChatServiceImpl implements AiChatService {

    private final AiConfigService aiConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiChatServiceImpl(AiConfigService aiConfigService, ObjectMapper objectMapper) {
        this.aiConfigService = aiConfigService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, String modelName, Double temperature) {
        AiConfigService.ResolvedAiConfig config = validateConfig();
        Map<String, Object> requestBody = buildChatRequestBody(config, systemPrompt, userPrompt, modelName, temperature, false);
        HttpRequest request = buildHttpRequest(config, requestBody);

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccessStatus(response.statusCode(), response.body());
            JsonNode root = objectMapper.readTree(response.body());
            String content = extractChatContent(root);
            if (!StringUtils.hasText(content)) {
                throw new BusinessException("AI 返回内容为空");
            }
            return content.trim();
        } catch (BusinessException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new BusinessException(500, "调用 AI 接口失败: 请求超时，请稍后重试");
        } catch (java.io.IOException exception) {
            throw new BusinessException(
                    500,
                    "调用 AI 接口失败: 无法连接到 AI 服务，请检查接口地址、网络或 TLS 配置"
                            + buildCauseSuffix(exception)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "调用 AI 接口失败: 请求被中断");
        } catch (Exception exception) {
            throw new BusinessException(500, "调用 AI 接口失败: " + extractBestMessage(exception));
        }
    }

    @Override
    public void streamChat(
            String systemPrompt,
            String userPrompt,
            String modelName,
            Double temperature,
            Consumer<String> onDelta
    ) {
        AiConfigService.ResolvedAiConfig config = validateConfig();
        Map<String, Object> requestBody = buildChatRequestBody(config, systemPrompt, userPrompt, modelName, temperature, true);
        HttpRequest request = buildHttpRequest(config, requestBody);

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(
                        500,
                        "调用 AI 接口失败: HTTP " + response.statusCode() + " " + truncateMessage(readStreamBody(response.body()), 300)
                );
            }

            try (InputStream inputStream = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                List<String> dataLines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        consumeEventPayload(dataLines, onDelta);
                        dataLines.clear();
                        continue;
                    }
                    if (line.startsWith(":")) {
                        continue;
                    }
                    if (line.startsWith("data:")) {
                        dataLines.add(line.substring(5).trim());
                    }
                }
                consumeEventPayload(dataLines, onDelta);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new BusinessException(500, "调用 AI 接口失败: 请求超时，请稍后重试");
        } catch (java.io.IOException exception) {
            throw new BusinessException(
                    500,
                    "调用 AI 接口失败: 无法连接到 AI 服务，请检查接口地址、网络或 TLS 配置"
                            + buildCauseSuffix(exception)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "调用 AI 接口失败: 请求被中断");
        } catch (Exception exception) {
            throw new BusinessException(500, "调用 AI 接口失败: " + extractBestMessage(exception));
        }
    }

    private AiConfigService.ResolvedAiConfig validateConfig() {
        AiConfigService.ResolvedAiConfig config = aiConfigService.getResolvedConfig();
        if (!Boolean.TRUE.equals(config.enabled())) {
            throw new BusinessException("AI 功能未启用");
        }
        if (Boolean.TRUE.equals(config.mockMode())) {
            throw new BusinessException("当前仍处于 Mock 模式，请先关闭 Mock 模式再调用真实接口");
        }
        if (!StringUtils.hasText(config.apiKey())) {
            throw new BusinessException("AI API Key 未配置");
        }
        return config;
    }

    private Map<String, Object> buildChatRequestBody(
            AiConfigService.ResolvedAiConfig config,
            String systemPrompt,
            String userPrompt,
            String modelName,
            Double temperature,
            boolean stream
    ) {
        if (!StringUtils.hasText(modelName)) {
            throw new BusinessException("AI 默认模型未配置");
        }
        if (isResponsesApi(config)) {
            return buildResponsesRequestBody(systemPrompt, userPrompt, modelName, temperature, stream);
        }
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName.trim());
        requestBody.put("temperature", temperature == null ? 0.7 : temperature);
        requestBody.put("stream", stream);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        return requestBody;
    }

    private Map<String, Object> buildResponsesRequestBody(
            String systemPrompt,
            String userPrompt,
            String modelName,
            Double temperature,
            boolean stream
    ) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName.trim());
        requestBody.put("temperature", temperature == null ? 0.7 : temperature);
        if (stream) {
            requestBody.put("stream", true);
        }
        requestBody.put("input", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of(
                        "type", "input_text",
                        "text", buildResponsesInputText(systemPrompt, userPrompt)
                ))
        )));
        return requestBody;
    }

    private String buildResponsesInputText(String systemPrompt, String userPrompt) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(systemPrompt)) {
            builder.append("系统指令：").append(System.lineSeparator())
                    .append(systemPrompt.trim())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }
        builder.append("用户消息：").append(System.lineSeparator())
                .append(userPrompt == null ? "" : userPrompt.trim());
        return builder.toString();
    }

    private boolean isResponsesApi(AiConfigService.ResolvedAiConfig config) {
        return config != null
                && StringUtils.hasText(config.chatPath())
                && config.chatPath().trim().toLowerCase().contains("/responses");
    }

    private HttpRequest buildHttpRequest(AiConfigService.ResolvedAiConfig config, Map<String, Object> requestBody) {
        try {
            return HttpRequest.newBuilder(buildChatUri(config.baseUrl(), config.chatPath()))
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                    .build();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(500, "调用 AI 接口失败: 请求构建失败 - " + extractBestMessage(exception));
        }
    }

    private URI buildChatUri(String baseUrl, String chatPath) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException("AI Base URL 未配置");
        }
        if (!StringUtils.hasText(chatPath)) {
            throw new BusinessException("AI Chat Path 未配置");
        }

        String normalizedBaseUrl = baseUrl.trim();
        String normalizedPath = chatPath.trim();
        boolean baseEndsWithSlash = normalizedBaseUrl.endsWith("/");
        boolean pathStartsWithSlash = normalizedPath.startsWith("/");

        String fullUrl;
        if (baseEndsWithSlash && pathStartsWithSlash) {
            fullUrl = normalizedBaseUrl + normalizedPath.substring(1);
        } else if (!baseEndsWithSlash && !pathStartsWithSlash) {
            fullUrl = normalizedBaseUrl + "/" + normalizedPath;
        } else {
            fullUrl = normalizedBaseUrl + normalizedPath;
        }
        return URI.create(fullUrl);
    }

    private void ensureSuccessStatus(int statusCode, String responseBody) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        throw new BusinessException(
                500,
                "调用 AI 接口失败: HTTP " + statusCode + " " + truncateMessage(responseBody, 300)
        );
    }

    private void consumeEventPayload(List<String> dataLines, Consumer<String> onDelta) {
        if (dataLines == null || dataLines.isEmpty()) {
            return;
        }
        String payload = String.join("\n", dataLines).trim();
        if (!StringUtils.hasText(payload) || "[DONE]".equals(payload)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String deltaText = extractDeltaContent(root);
            if (StringUtils.hasText(deltaText)) {
                onDelta.accept(deltaText);
            }
        } catch (Exception ignored) {
            // 某些中转站会插入非标准事件，这里忽略无法解析的分片，避免中断整条流。
        }
    }

    private String extractChatContent(JsonNode root) {
        JsonNode choices = root == null ? null : root.path("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode firstChoice = choices.get(0);
            JsonNode messageNode = firstChoice.path("message");
            if (!messageNode.isMissingNode()) {
                return extractContentNode(messageNode.path("content"));
            }
        }
        String responsesContent = extractResponsesContent(root);
        if (StringUtils.hasText(responsesContent)) {
            return responsesContent;
        }
        throw new BusinessException("AI 返回结果为空");
    }

    private String extractDeltaContent(JsonNode root) {
        if (root != null && StringUtils.hasText(root.path("type").asText())) {
            String eventType = root.path("type").asText();
            if ("response.output_text.delta".equals(eventType) || "response.refusal.delta".equals(eventType)) {
                String delta = root.path("delta").asText();
                return StringUtils.hasText(delta) ? delta : null;
            }
        }
        JsonNode choices = root == null ? null : root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode firstChoice = choices.get(0);
        JsonNode deltaNode = firstChoice.path("delta");
        if (!deltaNode.isMissingNode()) {
            return extractContentNode(deltaNode.path("content"));
        }
        JsonNode messageNode = firstChoice.path("message");
        if (!messageNode.isMissingNode()) {
            return extractContentNode(messageNode.path("content"));
        }
        return null;
    }

    private String extractResponsesContent(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        JsonNode outputTextNode = root.path("output_text");
        if (outputTextNode.isTextual() && StringUtils.hasText(outputTextNode.asText())) {
            return outputTextNode.asText();
        }
        JsonNode responseNode = root.path("response");
        if (!responseNode.isMissingNode() && !responseNode.isNull()) {
            String nested = extractResponsesContent(responseNode);
            if (StringUtils.hasText(nested)) {
                return nested;
            }
        }
        JsonNode outputNode = root.path("output");
        if (!outputNode.isArray()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : outputNode) {
            JsonNode contentNode = item.path("content");
            if (!contentNode.isArray()) {
                continue;
            }
            for (JsonNode contentItem : contentNode) {
                String text = extractContentNode(contentItem.path("text"));
                if (!StringUtils.hasText(text)) {
                    text = extractContentNode(contentItem.path("content"));
                }
                if (StringUtils.hasText(text)) {
                    builder.append(text);
                }
            }
        }
        return builder.toString();
    }

    private String extractContentNode(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode node : contentNode) {
                if (node == null || node.isNull()) {
                    continue;
                }
                if (node.isTextual()) {
                    builder.append(node.asText());
                    continue;
                }
                JsonNode textNode = node.path("text");
                if (textNode.isTextual()) {
                    builder.append(textNode.asText());
                }
            }
            return builder.toString();
        }
        return null;
    }

    private String readStreamBody(InputStream inputStream) {
        if (inputStream == null) {
            return "未知错误";
        }
        try (InputStream stream = inputStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return extractBestMessage(exception);
        }
    }

    private String buildCauseSuffix(Throwable throwable) {
        Throwable rootCause = extractRootCause(throwable);
        if (rootCause == null) {
            return "";
        }
        String message = buildThrowableLabel(rootCause);
        return StringUtils.hasText(message) ? "（" + truncateMessage(message, 180) + "）" : "";
    }

    private String extractBestMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                return current.getMessage().trim();
            }
            current = current.getCause();
        }
        Throwable rootCause = extractRootCause(throwable);
        return rootCause == null ? "未知异常" : rootCause.getClass().getSimpleName();
    }

    private Throwable extractRootCause(Throwable throwable) {
        Throwable current = throwable;
        Throwable root = throwable;
        while (current != null) {
            root = current;
            current = current.getCause();
        }
        return root;
    }

    private String buildThrowableLabel(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String simpleName = throwable.getClass().getSimpleName();
        if (StringUtils.hasText(throwable.getMessage())) {
            return simpleName + ": " + throwable.getMessage().trim();
        }
        return simpleName;
    }

    private String truncateMessage(String message, int maxLength) {
        if (!StringUtils.hasText(message)) {
            return "未知错误";
        }
        String normalized = message.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
