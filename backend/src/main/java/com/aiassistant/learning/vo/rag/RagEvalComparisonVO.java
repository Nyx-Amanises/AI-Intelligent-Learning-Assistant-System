package com.aiassistant.learning.vo.rag;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagEvalComparisonVO {
    private List<RagEvalRunVO> runs;
    private boolean comparable;
    private List<String> reasons;
}
