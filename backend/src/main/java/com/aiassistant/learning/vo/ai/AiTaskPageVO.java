package com.aiassistant.learning.vo.ai;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiTaskPageVO {

    private Long id;

    private String taskType;

    private String bizType;

    private Long bizId;

    private String status;

    private Integer progressRate;

    private Integer retryCount;

    private Integer priority;

    private String modelName;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
}
