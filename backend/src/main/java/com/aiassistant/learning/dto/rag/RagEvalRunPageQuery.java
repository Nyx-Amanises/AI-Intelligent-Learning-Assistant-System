package com.aiassistant.learning.dto.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class RagEvalRunPageQuery {
    @Min(1)
    private Long current = 1L;
    @Min(1)
    @Max(50)
    private Long size = 10L;
}
