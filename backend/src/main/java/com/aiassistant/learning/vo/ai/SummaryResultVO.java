package com.aiassistant.learning.vo.ai;

import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SummaryResultVO {

    private Long materialId;

    private Long recordId;

    private Long noteId;

    private String modelName;

    private String summaryType;

    private String summaryText;

    private List<RetrievedSegmentVO> sourceSegments;

    private LocalDateTime createdAt;
}
