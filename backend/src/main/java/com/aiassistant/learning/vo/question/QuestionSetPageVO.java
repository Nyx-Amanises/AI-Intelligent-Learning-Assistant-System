package com.aiassistant.learning.vo.question;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 题集列表项返回对象。
 */
@Data
@Builder
public class QuestionSetPageVO {

    /** 生成时固定的内容来源：MOCK（模拟数据）、LIVE（真实模型）、UNKNOWN（历史来源未知）。 */
    private String generationMode;

    /**
     * 题集 ID。
     */
    private Long id;

    /**
     * 关联资料 ID。
     */
    private Long materialId;

    /**
     * 题集标题。
     */
    private String title;

    /**
     * 题目数量。
     */
    private Integer questionCount;

    /**
     * 总分。
     */
    private Integer totalScore;

    /**
     * 难度等级。
     */
    private Integer difficultyLevel;

    /**
     * 题集状态。
     */
    private String status;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}
