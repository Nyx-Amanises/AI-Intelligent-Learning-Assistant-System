package com.aiassistant.learning.vo.assistant;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssistantSessionDetailVO {

    private Long id;

    private String title;

    private String status;

    private Integer pinned;

    private String currentContextType;

    private Long currentContextId;

    private Long currentMaterialId;

    private Long currentQuestionSetId;

    private Long currentPracticeSessionId;

    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<AssistantMessageVO> messages;

    private List<AssistantToolCallVO> recentToolCalls;
}
