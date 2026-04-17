package com.aiassistant.learning.vo.rag;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetrievalPreviewVO {

    private Long materialId;

    private String queryText;

    private Integer limit;

    private Integer hitCount;

    private List<RetrievedSegmentVO> segments;
}
