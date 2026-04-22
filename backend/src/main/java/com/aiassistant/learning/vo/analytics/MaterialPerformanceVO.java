package com.aiassistant.learning.vo.analytics;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * 按学习资料统计的练习表现。
 */
@Data
@Builder
public class MaterialPerformanceVO {

    /**
     * 资料 ID。
     */
    private Long materialId;

    /**
     * 资料标题。
     */
    private String materialTitle;

    /**
     * 该资料相关练习次数。
     */
    private Integer practiceCount;

    /**
     * 作答次数。
     */
    private Integer attemptCount;

    /**
     * 错误次数。
     */
    private Integer wrongCount;

    /**
     * 正确率。
     */
    private BigDecimal accuracyRate;

    /**
     * 得分率。
     */
    private BigDecimal scoreRate;
}
