package com.aiassistant.learning.dto.wrongquestion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 错题本分页查询参数。
 */
@Data
public class WrongQuestionPageQuery {

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
     * 按资料 ID 筛选错题。
     */
    private Long materialId;

    /**
     * 按题型筛选错题。
     */
    private String questionType;

    /**
     * 关键词，可匹配题干、知识点、资料标题、题集标题和练习名称。
     */
    private String keyword;
}
