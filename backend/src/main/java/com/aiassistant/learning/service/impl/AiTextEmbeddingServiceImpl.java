package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.TextEmbeddingService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AiTextEmbeddingServiceImpl implements TextEmbeddingService {

    private static final int MOCK_VECTOR_SIZE = 1536;
    private static final String PROVIDER_OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";
    private static final String PROVIDER_ARK_MULTIMODAL_TEXT = "ARK_MULTIMODAL_TEXT";

    private final AiConfigService aiConfigService;

    public AiTextEmbeddingServiceImpl(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    @Override
    public EmbeddingBatchResult embedTexts(List<String> texts, String modelName) {
        if (texts == null || texts.isEmpty()) {
            throw new BusinessException("待向量化文本不能为空");
        }

        AiConfigService.ResolvedAiConfig config = aiConfigService.getResolvedConfig();
        if (!Boolean.TRUE.equals(config.enabled())) {
            throw new BusinessException("AI 功能未启用");
        }

        String resolvedModel = StringUtils.hasText(modelName)
                ? modelName.trim()
                : config.defaultEmbeddingModel();
        if (!StringUtils.hasText(resolvedModel)) {
            throw new BusinessException("未配置默认 Embedding 模型");
        }

        if (Boolean.TRUE.equals(config.mockMode())) {
            List<List<Double>> vectors = texts.stream()
                    .map(this::buildMockVector)
                    .toList();
            return new EmbeddingBatchResult(resolvedModel, vectors);
        }

        String providerType = normalizeProviderType(config.embeddingProviderType());
        String embeddingBaseUrl = config.embeddingBaseUrl();
        String embeddingPath = config.embeddingPath();
        String embeddingApiKey = config.embeddingApiKey();

        if (!StringUtils.hasText(embeddingApiKey)) {
            throw new BusinessException("Embedding API Key 未配置");
        }
        if (!StringUtils.hasText(embeddingBaseUrl)) {
            throw new BusinessException("Embedding Base URL 未配置");
        }
        if (!StringUtils.hasText(embeddingPath)) {
            throw new BusinessException("Embedding Path 未配置");
        }

        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(embeddingBaseUrl.trim())
                    .build();

            List<List<Double>> vectors = switch (providerType) {
                case PROVIDER_ARK_MULTIMODAL_TEXT -> embedByArkMultimodal(
                        restClient,
                        embeddingPath,
                        embeddingApiKey,
                        resolvedModel,
                        texts
                );
                case PROVIDER_OPENAI_COMPATIBLE -> embedByOpenAiCompatible(
                        restClient,
                        embeddingPath,
                        embeddingApiKey,
                        resolvedModel,
                        texts
                );
                default -> throw new BusinessException("不支持的 Embedding Provider 类型: " + providerType);
            };

            if (vectors.size() != texts.size()) {
                throw new BusinessException("Embedding 返回数量与输入文本数量不一致");
            }

            return new EmbeddingBatchResult(resolvedModel, vectors);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            String responseBody = StringUtils.hasText(exception.getResponseBodyAsString())
                    ? exception.getResponseBodyAsString()
                    : exception.getStatusText();
            throw new BusinessException(
                    500,
                    "调用 Embedding 接口失败: HTTP " + exception.getStatusCode().value() + " " + truncateMessage(responseBody, 240)
            );
        } catch (ResourceAccessException exception) {
            throw new BusinessException(
                    500,
                    "调用 Embedding 接口失败: 无法连接到 AI 服务，请检查接口地址、网络或 TLS 配置" + buildCauseSuffix(exception)
            );
        } catch (Exception exception) {
            throw new BusinessException(500, "调用 Embedding 接口失败: " + extractBestMessage(exception));
        }
    }

    private List<List<Double>> embedByOpenAiCompatible(
            RestClient restClient,
            String embeddingPath,
            String embeddingApiKey,
            String modelName,
            List<String> texts
    ) {
        Map<String, Object> response = restClient.post()
                .uri(embeddingPath)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + embeddingApiKey)
                .body(Map.of(
                        "model", modelName,
                        "input", texts
                ))
                .retrieve()
                .body(Map.class);
        return extractVectorsFromResponse(response);
    }

    private List<List<Double>> embedByArkMultimodal(
            RestClient restClient,
            String embeddingPath,
            String embeddingApiKey,
            String modelName,
            List<String> texts
    ) {
        List<List<Double>> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            Map<String, Object> response = restClient.post()
                    .uri(embeddingPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + embeddingApiKey)
                    .body(Map.of(
                            "model", modelName,
                            "input", List.of(
                                    Map.of(
                                            "type", "text",
                                            "text", text
                                    )
                            )
                    ))
                    .retrieve()
                    .body(Map.class);
            List<List<Double>> responseVectors = extractVectorsFromResponse(response);
            if (responseVectors.isEmpty()) {
                throw new BusinessException("豆包 Embedding 接口未返回有效向量");
            }
            vectors.add(responseVectors.get(0));
        }
        return vectors;
    }

    private List<List<Double>> extractVectorsFromResponse(Map<String, Object> response) {
        Object dataObj = response == null ? null : response.get("data");
        List<?> dataList;
        if (dataObj instanceof List<?> list) {
            dataList = list;
        } else if (dataObj instanceof Map<?, ?> map) {
            dataList = List.of(map);
        } else {
            dataList = List.of();
        }
        if (dataList.isEmpty()) {
            Object embeddingsObj = response == null ? null : response.get("embeddings");
            if (embeddingsObj instanceof List<?> embeddingsList && !embeddingsList.isEmpty()) {
                dataList = embeddingsList;
            }
        }
        if (dataList.isEmpty()) {
            throw new BusinessException("Embedding 接口未返回有效向量");
        }

        List<List<Double>> vectors = new ArrayList<>();
        for (Object item : dataList) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                throw new BusinessException("Embedding 返回结构异常");
            }
            List<Double> vector = extractSingleVector(itemMap);
            if (vector.isEmpty()) {
                throw new BusinessException("Embedding 向量格式异常");
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private List<Double> extractSingleVector(Map<?, ?> itemMap) {
        Object embeddingObj = itemMap.get("embedding");
        if (!(embeddingObj instanceof List<?>)) {
            embeddingObj = itemMap.get("dense_embedding");
        }
        if (!(embeddingObj instanceof List<?>)) {
            embeddingObj = itemMap.get("vector");
        }
        if (!(embeddingObj instanceof List<?> embeddingList) || embeddingList.isEmpty()) {
            return List.of();
        }

        List<Double> vector = new ArrayList<>(embeddingList.size());
        for (Object value : embeddingList) {
            if (value instanceof Number number) {
                vector.add(number.doubleValue());
            }
        }
        return vector;
    }

    private List<Double> buildMockVector(String text) {
        long seed = text == null ? 0L : text.hashCode();
        double[] raw = new double[MOCK_VECTOR_SIZE];
        double norm = 0D;
        for (int i = 0; i < MOCK_VECTOR_SIZE; i++) {
            double value = Math.sin(seed * 0.0001D + i * 0.017D) + Math.cos(seed * 0.00007D - i * 0.013D);
            raw[i] = value;
            norm += value * value;
        }
        double divisor = norm <= 0 ? 1D : Math.sqrt(norm);
        List<Double> vector = new ArrayList<>(MOCK_VECTOR_SIZE);
        for (double value : raw) {
            vector.add(value / divisor);
        }
        return vector;
    }

    private String normalizeProviderType(String providerType) {
        if (!StringUtils.hasText(providerType)) {
            return PROVIDER_OPENAI_COMPATIBLE;
        }
        return providerType.trim().toUpperCase();
    }

    private String buildCauseSuffix(Throwable throwable) {
        String message = extractBestMessage(throwable);
        return StringUtils.hasText(message) ? "（" + truncateMessage(message, 180) + "）" : "";
    }

    private String extractBestMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                return current.getClass().getSimpleName() + ": " + current.getMessage().trim();
            }
            current = current.getCause();
        }
        return "未知异常";
    }

    private String truncateMessage(String message, int maxLength) {
        if (!StringUtils.hasText(message)) {
            return "未知错误";
        }
        String normalized = message.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
