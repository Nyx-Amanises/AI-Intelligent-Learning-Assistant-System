package com.aiassistant.learning.vo.analytics;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * 掌握度等级分布返回对象。
 */
@Data
@Builder
public class MasteryDistributionVO {

    /**
     * 等级编码，例如 MASTERED、GOOD、WEAK、RISK。
     */
    private String level;

    /**
     * 等级中文标签。
     */
    private String label;

    /**
     * 该等级下的知识点数量。
     */
    private Integer count;

    /**
     * 该等级占全部知识点的比例。
     */
    private BigDecimal percent;
}
