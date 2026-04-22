package com.aiassistant.learning.dto.ai;

import lombok.Data;

/**
 * 摘要生成任务载荷。
 */
@Data
public class SummaryTaskPayload {

    /**
     * 资料 ID。
     */
    private Long materialId;

    /**
     * 模型名称。
     */
    private String modelName;

    /**
     * 摘要类型。
     */
    private String summaryType;

    /**
     * 是否保存为笔记。
     */
    private Boolean saveAsNote;

    /**
     * 模型温度参数。
     */
    private Double temperature;
}
