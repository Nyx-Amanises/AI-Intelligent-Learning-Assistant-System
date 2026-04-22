package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.QdrantProperties;
import com.aiassistant.learning.service.VectorStoreService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Qdrant 向量库服务实现类。
 *
 * <p>负责创建集合、写入资料片段向量、删除资料向量和按相似度检索片段。</p>
 */
@Service
public class QdrantVectorStoreServiceImpl implements VectorStoreService {

    /** Qdrant 连接配置。 */
    private final QdrantProperties qdrantProperties;

    public QdrantVectorStoreServiceImpl(QdrantProperties qdrantProperties) {
        this.qdrantProperties = qdrantProperties;
    }

    /**
     * 写入或更新资料片段向量。
     */
    @Override
    public void upsertMaterialSegments(List<MaterialSegmentVector> segmentVectors) {
        if (segmentVectors == null || segmentVectors.isEmpty()) {
            return;
        }
        validateQdrantEnabled();

        int vectorSize = segmentVectors.stream()
                .findFirst()
                .map(MaterialSegmentVector::vector)
                .map(List::size)
                .orElseThrow(() -> new BusinessException("向量数据不能为空"));
        ensureCollection(vectorSize);

        // Qdrant 的 point 由 id、vector 和 payload 三部分组成。
        List<Map<String, Object>> points = new ArrayList<>(segmentVectors.size());
        for (MaterialSegmentVector item : segmentVectors) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("segmentId", item.segmentId());
            payload.put("userId", item.userId());
            payload.put("materialId", item.materialId());
            payload.put("segmentNo", item.segmentNo());
            if (item.pageNo() != null) {
                payload.put("pageNo", item.pageNo());
            }
            if (StringUtils.hasText(item.sectionTitle())) {
                payload.put("sectionTitle", item.sectionTitle().trim());
            }
            if (StringUtils.hasText(item.contentText())) {
                payload.put("contentText", item.contentText().trim());
            }
            if (StringUtils.hasText(item.keywords())) {
                payload.put("keywords", item.keywords().trim());
            }
            if (StringUtils.hasText(item.modelName())) {
                payload.put("modelName", item.modelName().trim());
            }

            Map<String, Object> point = new HashMap<>();
            point.put("id", item.segmentId());
            point.put("vector", item.vector());
            point.put("payload", payload);
            points.add(point);
        }

        try {
            buildRestClient().put()
                    .uri("/collections/{collectionName}/points?wait=true", resolveCollectionName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyApiKey(headers))
                    .body(Map.of("points", points))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw buildQdrantHttpException("写入向量数据失败", exception);
        } catch (ResourceAccessException exception) {
            throw buildQdrantAccessException("写入向量数据失败", exception);
        }
    }

    /**
     * 删除某个用户某份资料的全部向量。
     */
    @Override
    public void deleteMaterialSegments(Long userId, Long materialId) {
        if (!Boolean.TRUE.equals(qdrantProperties.getEnabled())) {
            return;
        }
        if (userId == null || materialId == null) {
            return;
        }

        try {
            buildRestClient().post()
                    .uri("/collections/{collectionName}/points/delete?wait=true", resolveCollectionName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyApiKey(headers))
                    .body(Map.of(
                            "filter", Map.of(
                                    "must", List.of(
                                            Map.of("key", "userId", "match", Map.of("value", userId)),
                                            Map.of("key", "materialId", "match", Map.of("value", materialId))
                                    )
                            )
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw buildQdrantHttpException("清理资料向量失败", exception);
        } catch (ResourceAccessException exception) {
            throw buildQdrantAccessException("清理资料向量失败", exception);
        }
    }

    /**
     * 根据查询向量检索相似资料片段。
     */
    @Override
    public List<RetrievedSegment> searchMaterialSegments(Long userId, Long materialId, List<Double> queryVector, int limit) {
        validateQdrantEnabled();
        if (userId == null || materialId == null) {
            throw new BusinessException("检索资料上下文缺少 userId 或 materialId");
        }
        if (queryVector == null || queryVector.isEmpty()) {
            throw new BusinessException("检索向量不能为空");
        }

        try {
            Map<String, Object> response = buildRestClient().post()
                    .uri("/collections/{collectionName}/points/query", resolveCollectionName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyApiKey(headers))
                    .body(Map.of(
                            "query", queryVector,
                            "limit", Math.max(1, limit),
                            "with_payload", true,
                            "filter", Map.of(
                                    "must", List.of(
                                            Map.of("key", "userId", "match", Map.of("value", userId)),
                                            Map.of("key", "materialId", "match", Map.of("value", materialId))
                                    )
                            )
                    ))
                    .retrieve()
                    .body(Map.class);

            Object resultObj = response == null ? null : response.get("result");
            List<?> points = extractPoints(resultObj);
            List<RetrievedSegment> segments = new ArrayList<>();
            for (Object item : points) {
                if (!(item instanceof Map<?, ?> pointMap)) {
                    continue;
                }
                Object payloadObj = pointMap.get("payload");
                if (!(payloadObj instanceof Map<?, ?> payload)) {
                    continue;
                }
                segments.add(new RetrievedSegment(
                        toLong(payload.get("segmentId")),
                        toInteger(payload.get("segmentNo")),
                        toInteger(payload.get("pageNo")),
                        toText(payload.get("sectionTitle")),
                        toText(payload.get("contentText")),
                        toText(payload.get("keywords")),
                        toDouble(pointMap.get("score"))
                ));
            }
            return segments;
        } catch (RestClientResponseException exception) {
            throw buildQdrantHttpException("检索资料上下文失败", exception);
        } catch (ResourceAccessException exception) {
            throw buildQdrantAccessException("检索资料上下文失败", exception);
        }
    }

    /**
     * 确保集合存在，并且向量维度与当前模型一致。
     */
    private void ensureCollection(int vectorSize) {
        Integer existingSize = getExistingCollectionVectorSize();
        if (existingSize == null) {
            createCollection(vectorSize);
            return;
        }
        if (existingSize != vectorSize) {
            throw new BusinessException(
                    "Qdrant 集合 " + resolveCollectionName() + " 的向量维度为 " + existingSize
                            + "，当前 Embedding 维度为 " + vectorSize
                            + "。请更换 collection-name 或清理后重建集合"
            );
        }
    }

    /**
     * 读取已有集合的向量维度；集合不存在时返回 null。
     */
    private Integer getExistingCollectionVectorSize() {
        try {
            Map<String, Object> response = buildRestClient().get()
                    .uri("/collections/{collectionName}", resolveCollectionName())
                    .headers(headers -> applyApiKey(headers))
                    .retrieve()
                    .body(Map.class);
            Object resultObj = response == null ? null : response.get("result");
            if (!(resultObj instanceof Map<?, ?> resultMap)) {
                return null;
            }
            Object configObj = resultMap.get("config");
            if (!(configObj instanceof Map<?, ?> configMap)) {
                return null;
            }
            Object paramsObj = configMap.get("params");
            if (!(paramsObj instanceof Map<?, ?> paramsMap)) {
                return null;
            }
            Object vectorsObj = paramsMap.get("vectors");
            if (vectorsObj instanceof Map<?, ?> vectorsMap) {
                return toInteger(vectorsMap.get("size"));
            }
            return toInteger(vectorsObj);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return null;
            }
            throw buildQdrantHttpException("读取 Qdrant 集合信息失败", exception);
        } catch (ResourceAccessException exception) {
            throw buildQdrantAccessException("读取 Qdrant 集合信息失败", exception);
        }
    }

    /**
     * 创建 Qdrant 集合。
     */
    private void createCollection(int vectorSize) {
        try {
            buildRestClient().put()
                    .uri("/collections/{collectionName}", resolveCollectionName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyApiKey(headers))
                    .body(Map.of(
                            "vectors", Map.of(
                                    "size", vectorSize,
                                    "distance", resolveDistance()
                            )
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw buildQdrantHttpException("创建 Qdrant 集合失败", exception);
        } catch (ResourceAccessException exception) {
            throw buildQdrantAccessException("创建 Qdrant 集合失败", exception);
        }
    }

    /**
     * 兼容 Qdrant 不同接口返回的 points 结构。
     */
    private List<?> extractPoints(Object resultObj) {
        if (resultObj instanceof Map<?, ?> resultMap) {
            Object pointsObj = resultMap.get("points");
            if (pointsObj instanceof List<?> points) {
                return points;
            }
        }
        if (resultObj instanceof List<?> resultList) {
            return resultList;
        }
        return List.of();
    }

    /**
     * 检查 Qdrant 是否启用且配置完整。
     */
    private void validateQdrantEnabled() {
        if (!Boolean.TRUE.equals(qdrantProperties.getEnabled())) {
            throw new BusinessException("Qdrant 未启用，请先安装并启动 Qdrant，然后把 app.qdrant.enabled 改为 true");
        }
        if (!StringUtils.hasText(qdrantProperties.getBaseUrl())) {
            throw new BusinessException("Qdrant Base URL 未配置");
        }
    }

    /**
     * 根据配置创建 RestClient。
     */
    private RestClient buildRestClient() {
        return RestClient.builder()
                .baseUrl(qdrantProperties.getBaseUrl().trim())
                .build();
    }

    /**
     * 如果配置了 API Key，就添加到 Qdrant 请求头。
     */
    private void applyApiKey(org.springframework.http.HttpHeaders headers) {
        if (StringUtils.hasText(qdrantProperties.getApiKey())) {
            headers.set("api-key", qdrantProperties.getApiKey().trim());
        }
    }

    /**
     * 解析集合名称。
     */
    private String resolveCollectionName() {
        if (!StringUtils.hasText(qdrantProperties.getCollectionName())) {
            throw new BusinessException("Qdrant collection-name 未配置");
        }
        return qdrantProperties.getCollectionName().trim();
    }

    /**
     * 解析向量距离算法，默认使用 Cosine。
     */
    private String resolveDistance() {
        return StringUtils.hasText(qdrantProperties.getDistance())
                ? qdrantProperties.getDistance().trim()
                : "Cosine";
    }

    /**
     * 将 Qdrant HTTP 错误转换为业务异常。
     */
    private BusinessException buildQdrantHttpException(String action, RestClientResponseException exception) {
        String responseBody = StringUtils.hasText(exception.getResponseBodyAsString())
                ? exception.getResponseBodyAsString()
                : exception.getStatusText();
        return new BusinessException(
                500,
                action + ": HTTP " + exception.getStatusCode().value() + " " + truncateMessage(responseBody, 220)
        );
    }

    /**
     * 将 Qdrant 连接失败转换为业务异常。
     */
    private BusinessException buildQdrantAccessException(String action, ResourceAccessException exception) {
        return new BusinessException(
                500,
                action + ": 无法连接到 Qdrant，请检查服务是否已启动" + buildCauseSuffix(exception)
        );
    }

    /**
     * 构造异常原因后缀。
     */
    private String buildCauseSuffix(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                return "（" + truncateMessage(current.getClass().getSimpleName() + ": " + current.getMessage(), 180) + "）";
            }
            current = current.getCause();
        }
        return "";
    }

    /**
     * 截断过长错误信息。
     */
    private String truncateMessage(String message, int maxLength) {
        if (!StringUtils.hasText(message)) {
            return "未知错误";
        }
        String normalized = message.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    /** 将数字对象转换成 Long。 */
    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    /** 将数字对象转换成 Integer。 */
    private Integer toInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /** 将数字对象转换成 Double。 */
    private Double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    /** 将任意对象转换成字符串。 */
    private String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
