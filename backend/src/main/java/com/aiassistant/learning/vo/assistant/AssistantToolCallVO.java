package com.aiassistant.learning.vo.assistant;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssistantToolCallVO {

    private Long id;

    private Long messageId;

    private String toolName;

    private String toolArgsJson;

    private String toolResultJson;

    private String status;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
}
