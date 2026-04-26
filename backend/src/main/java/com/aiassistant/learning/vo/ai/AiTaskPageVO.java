package com.aiassistant.learning.vo.ai;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * AI 任务列表项返回对象。
 */
@Data
@Builder
public class AiTaskPageVO {

    /**
     * 任务 ID。
     */
    private Long id;

    /**
     * 任务类型。
     */
    private String taskType;

    /**
     * 业务类型。
     */
    private String bizType;

    /**
     * 业务 ID。
     */
    private Long bizId;

    private String bizTitle;

    /**
     * 任务状态。
     */
    private String status;

    /**
     * 任务进度百分比。
     */
    private Integer progressRate;

    /**
     * 重试次数。
     */
    private Integer retryCount;

    /**
     * 优先级。
     */
    private Integer priority;

    /**
     * 模型名称。
     */
    private String modelName;

    /**
     * 错误信息。
     */
    private String errorMessage;

    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间。
     */
    private LocalDateTime finishedAt;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}
