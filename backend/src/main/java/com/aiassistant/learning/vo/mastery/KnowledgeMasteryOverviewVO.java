package com.aiassistant.learning.vo.mastery;

import com.aiassistant.learning.vo.page.PageVO;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 知识掌握度总览返回对象。
 */
@Data
@Builder
public class KnowledgeMasteryOverviewVO {

    /**
     * 知识点总数。
     */
    private Integer totalKnowledgePoints;

    /**
     * 总作答次数。
     */
    private Integer totalAttempts;

    /**
     * 错误作答次数。
     */
    private Integer wrongAttempts;

    /**
     * 已掌握知识点数量。
     */
    private Integer masteredCount;

    /**
     * 基本掌握知识点数量。
     */
    private Integer goodCount;

    /**
     * 待巩固知识点数量。
     */
    private Integer weakCount;

    /**
     * 薄弱知识点数量。
     */
    private Integer riskCount;

    /**
     * 平均掌握度百分比。
     */
    private Integer averageMasteryPercent;

    /**
     * 最薄弱的前几个知识点。
     */
    private List<KnowledgeMasteryItemVO> weakestPoints;

    /**
     * 分页后的知识点掌握度列表。
     */
    private PageVO<KnowledgeMasteryItemVO> page;
}
