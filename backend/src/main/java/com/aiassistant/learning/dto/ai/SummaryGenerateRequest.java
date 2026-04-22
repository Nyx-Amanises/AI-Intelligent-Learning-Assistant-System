package com.aiassistant.learning.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 摘要生成请求参数。
 */
@Data
public class SummaryGenerateRequest {

    /**
     * 指定模型名称，空值时使用默认模型。
     */
    private String modelName;

    /**
     * 摘要类型，例如 STANDARD。
     */
    private String summaryType = "STANDARD";

    /**
     * 是否把摘要保存为学习笔记。
     */
    private Boolean saveAsNote = true;

    /**
     * 模型温度参数，值越高输出越发散。
     */
    @Min(value = 0, message = "temperature 不能小于0")
    @Max(value = 2, message = "temperature 不能大于2")
    private Double temperature = 0.7;
}
