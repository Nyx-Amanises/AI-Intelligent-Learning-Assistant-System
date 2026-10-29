package com.aiassistant.learning.common.util;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/** 题集来源快照的存取。读取失败只声明依据不可用，禁止用新的检索结果补齐历史。 */
public final class QuestionSetSources {
    public static final String SNAPSHOT = "GENERATION_SNAPSHOT";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    private QuestionSetSources() { }

    public static String encode(ObjectMapper mapper, List<RetrievedSegmentVO> segments) {
        try {
            return mapper.writeValueAsString(segments);
        } catch (Exception exception) {
            throw new BusinessException("保存出题依据失败，题集未发布");
        }
    }

    public static List<RetrievedSegmentVO> decode(ObjectMapper mapper, String snapshot) {
        if (!StringUtils.hasText(snapshot)) return List.of();
        try {
            JsonNode root = mapper.readTree(snapshot);
            if (root == null || !root.isArray() || root.isEmpty()) return List.of();
            List<RetrievedSegmentVO> sources = new ArrayList<>();
            for (JsonNode item : root) {
                if (!item.isObject() || !item.path("contentText").isTextual()
                        || !StringUtils.hasText(item.path("contentText").asText())) return List.of();
                sources.add(RetrievedSegmentVO.builder()
                        .segmentId(item.path("segmentId").isIntegralNumber() ? item.get("segmentId").longValue() : null)
                        .segmentNo(item.path("segmentNo").isIntegralNumber() ? item.get("segmentNo").intValue() : null)
                        .pageNo(item.path("pageNo").isIntegralNumber() ? item.get("pageNo").intValue() : null)
                        .sectionTitle(text(item, "sectionTitle"))
                        .contentText(item.get("contentText").asText())
                        .keywords(text(item, "keywords"))
                        .score(item.path("score").isNumber() ? item.get("score").doubleValue() : null)
                        .build());
            }
            return List.copyOf(sources);
        } catch (Exception exception) {
            return List.of();
        }
    }

    public static String mode(List<RetrievedSegmentVO> segments) {
        return segments.isEmpty() ? UNAVAILABLE : SNAPSHOT;
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.get(field).asText() : null;
    }
}
