package com.aiassistant.learning.dto.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.Data;

@Data
public class RagEvalRunRequest {

    @Min(value = 1, message = "检索条数最小为1")
    @Max(value = 20, message = "检索条数最大为20")
    private Integer limit = 5;

    private List<Long> sampleIds;
}
