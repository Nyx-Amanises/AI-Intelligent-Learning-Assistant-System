package com.aiassistant.learning.vo.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 薄弱知识点返回对象。
 */
@Data
@Builder
public class WeakKnowledgePointVO {

    /**
     * 知识点名称。
     */
    private String knowledgePoint;

    /**
     * 关联资料 ID。
     */
    private Long materialId;

    /**
     * 关联资料标题。
     */
    private String materialTitle;

    /**
     * 该知识点作答次数。
     */
    private Integer attemptCount;

    /**
     * 错误次数。
     */
    private Integer wrongCount;

    /**
     * 掌握度百分比。
     */
    private Integer masteryPercent;

    /**
     * 得分率。
     */
    private BigDecimal scoreRate;

    /**
     * 最近练习时间。
     */
    private LocalDateTime lastPracticeTime;
}
