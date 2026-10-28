package com.aiassistant.learning.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

/**
 * 把 AI 服务返回的错误响应整理成可以展示给用户的说明。
 *
 * <p>只提取 JSON 里常见的错误字段（error.message / error / message / msg），其余内容一律不回显：
 * 之前直接把响应体前 300 字拼进错误提示，配合可随意填写的服务地址，就能把内网服务的响应内容带回前端。
 * 原始响应只写日志，便于排查。</p>
 */
final class AiProviderErrorMessages {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_MESSAGE_LENGTH = 200;

    private AiProviderErrorMessages() {
    }

    /**
     * 生成形如“HTTP 401 Incorrect API key provided”的说明；提取不到错误字段时只保留状态码。
     */
    static String describe(int statusCode, String responseBody) {
        String message = extractMessage(responseBody);
        return "HTTP " + statusCode + (StringUtils.hasText(message) ? " " + message : "");
    }

    /**
     * 截断原始响应，只用于写日志。
     */
    static String forLog(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        String normalized = responseBody.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }

    private static String extractMessage(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        JsonNode root;
        try {
            root = JSON.readTree(responseBody);
        } catch (Exception exception) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode error = root.path("error");
        String message = firstText(error.path("message"), error, root.path("message"), root.path("msg"));
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_MESSAGE_LENGTH ? normalized : normalized.substring(0, MAX_MESSAGE_LENGTH) + "...";
    }

    private static String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
                return node.asText();
            }
        }
        return null;
    }
}
