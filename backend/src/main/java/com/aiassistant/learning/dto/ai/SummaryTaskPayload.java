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
     * 提交任务时创建的总结记录 ID；历史任务允许为空。
     */
    private Long recordId;

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

    public SummaryGenerateRequest toRequest() {
        SummaryGenerateRequest request = new SummaryGenerateRequest();
        request.setModelName(modelName);
        if (summaryType != null && !summaryType.isBlank()) {
            request.setSummaryType(summaryType);
        }
        if (saveAsNote != null) {
            request.setSaveAsNote(saveAsNote);
        }
        if (temperature != null) {
            request.setTemperature(temperature);
        }
        return request;
    }
}
