package com.aiassistant.learning.vo.ai;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SummaryHistoryVO {

    private Long recordId;

    private Long materialId;

    private String materialTitle;

    private Long noteId;

    private String modelName;

    private String summaryType;

    private String summaryText;

    private LocalDateTime createdAt;
}
