package com.aiassistant.learning.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class SummaryGenerateRequest {

    private String modelName;

    private String summaryType = "STANDARD";

    private Boolean saveAsNote = true;

    @Min(value = 0, message = "temperature 不能小于0")
    @Max(value = 2, message = "temperature 不能大于2")
    private Double temperature = 0.7;
}
