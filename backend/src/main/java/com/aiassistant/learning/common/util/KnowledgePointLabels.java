package com.aiassistant.learning.common.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.util.StringUtils;

/** 统一题目知识点标签的拆分口径，供掌握度、学习分析和错题筛选复用。 */
public final class KnowledgePointLabels {
    private KnowledgePointLabels() { }

    /**
     * 按中英文逗号、顿号、分号、竖线、斜杠和换行拆分，保留原文大小写及首次出现顺序。
     * 空值或只有分隔符返回空列表；不进行分词或子串匹配。
     * 兼容历史统计对长标签的处理：优先使用不超过 80 字的标签，全为长标签时保留原文。
     */
    public static List<String> split(String raw) {
        if (!StringUtils.hasText(raw)) return List.of();
        Set<String> labels = new LinkedHashSet<>();
        boolean hasLabel = false;
        for (String part : raw.split("[,，、;；|/\\r\\n]")) {
            String label = part.trim();
            if (!StringUtils.hasText(label)) continue;
            hasLabel = true;
            if (label.length() <= 80) labels.add(label);
        }
        if (labels.isEmpty()) return hasLabel ? List.of(raw.trim()) : List.of();
        return List.copyOf(labels);
    }
}
