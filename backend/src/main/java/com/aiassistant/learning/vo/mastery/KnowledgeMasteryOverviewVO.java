package com.aiassistant.learning.vo.mastery;

import com.aiassistant.learning.vo.page.PageVO;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeMasteryOverviewVO {

    private Integer totalKnowledgePoints;

    private Integer totalAttempts;

    private Integer wrongAttempts;

    private Integer masteredCount;

    private Integer goodCount;

    private Integer weakCount;

    private Integer riskCount;

    private Integer averageMasteryPercent;

    private List<KnowledgeMasteryItemVO> weakestPoints;

    private PageVO<KnowledgeMasteryItemVO> page;
}
