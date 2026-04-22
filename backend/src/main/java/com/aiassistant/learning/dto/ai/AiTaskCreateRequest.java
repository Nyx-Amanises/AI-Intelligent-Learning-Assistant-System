package com.aiassistant.learning.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 通用 AI 任务创建请求。
 *
 * <p>适合前端或内部工具直接创建一条任务记录，具体任务参数放在 payloadJson 中。</p>
 */
@Data
public class AiTaskCreateRequest {

    /**
     * 任务类型，不能为空。
     */
    @NotBlank(message = "taskType is required")
    private String taskType;

    /**
     * 业务类型，例如 MATERIAL、PRACTICE。
     */
    private String bizType;

    /**
     * 业务 ID，例如资料 ID 或练习会话 ID。
     */
    private Long bizId;

    /**
     * 任务优先级。
     */
    private Integer priority;

    /**
     * 指定模型名称。
     */
    private String modelName;

    /**
     * 任务载荷 JSON。
     */
    private String payloadJson;
}
