package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AiChatServiceImpl implements AiChatService {

    private final AiConfigService aiConfigService;

    public AiChatServiceImpl(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, String modelName, Double temperature) {
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

        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(config.baseUrl())
                    .build();

            Map<String, Object> response = restClient.post()
                    .uri(config.chatPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                    .body(Map.of(
                            "model", modelName,
                            "temperature", temperature == null ? 0.7 : temperature,
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemPrompt),
                                    Map.of("role", "user", "content", userPrompt)
                            )
                    ))
                    .retrieve()
                    .body(Map.class);

            Object choicesObj = response == null ? null : response.get("choices");
            if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
                throw new BusinessException("AI 返回结果为空");
            }
            Object firstChoice = choices.get(0);
            if (!(firstChoice instanceof Map<?, ?> firstChoiceMap)) {
                throw new BusinessException("AI 返回结构异常");
            }
            Object messageObj = firstChoiceMap.get("message");
            if (!(messageObj instanceof Map<?, ?> messageMap)) {
                throw new BusinessException("AI 返回消息为空");
            }
            Object contentObj = messageMap.get("content");
            if (!(contentObj instanceof String content) || !StringUtils.hasText(content)) {
                throw new BusinessException("AI 返回内容为空");
            }
            return content.trim();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            String responseBody = StringUtils.hasText(exception.getResponseBodyAsString())
                    ? exception.getResponseBodyAsString()
                    : exception.getStatusText();
            throw new BusinessException(
                    500,
                    "调用 AI 接口失败: HTTP " + exception.getStatusCode().value() + " " + truncateMessage(responseBody, 300)
            );
        } catch (ResourceAccessException exception) {
            throw new BusinessException(
                    500,
                    "调用 AI 接口失败: 无法连接到 AI 服务，请检查接口地址、网络或 TLS 配置"
                            + buildCauseSuffix(exception)
            );
        } catch (Exception exception) {
            throw new BusinessException(500, "调用 AI 接口失败: " + extractBestMessage(exception));
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
