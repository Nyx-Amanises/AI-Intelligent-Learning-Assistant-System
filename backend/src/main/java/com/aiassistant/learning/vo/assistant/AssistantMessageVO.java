package com.aiassistant.learning.vo.assistant;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssistantMessageVO {

    private Long id;

    private String role;

    private String messageType;

    private String contentText;

    private String reasoningJson;

    private String toolPlanJson;

    private String modelName;

    private Integer tokenInput;

    private Integer tokenOutput;

    private LocalDateTime createdAt;
}
