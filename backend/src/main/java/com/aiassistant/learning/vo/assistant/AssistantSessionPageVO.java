package com.aiassistant.learning.vo.assistant;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssistantSessionPageVO {

    private Long id;

    private String title;

    private String status;

    private Integer pinned;

    private String currentContextType;

    private Long currentContextId;

    private String lastMessagePreview;

    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;
}
