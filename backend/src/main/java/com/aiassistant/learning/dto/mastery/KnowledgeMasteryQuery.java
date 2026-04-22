package com.aiassistant.learning.dto.mastery;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 知识掌握度查询参数。
 */
@Data
public class KnowledgeMasteryQuery {

    /**
     * 当前页码，从 1 开始。
     */
    @Min(value = 1, message = "页码最小为1")
    private Long current = 1L;

    /**
     * 每页条数，最大 50。
     */
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 50, message = "每页条数最大为50")
    private Long size = 10L;

    /**
     * 按资料 ID 筛选。
     */
    private Long materialId;

    /**
     * 按题型筛选。
     */
    private String questionType;

    /**
     * 按掌握等级筛选，例如 MASTERED、GOOD、WEAK、RISK。
     */
    private String masteryLevel;

    /**
     * 关键词，可匹配知识点、资料标题或建议文案。
     */
    private String keyword;
}
