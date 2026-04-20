package com.aiassistant.learning.dto.rag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class RagEvalSampleBatchCreateRequest {

    @Valid
    @NotEmpty(message = "评测样本不能为空")
    @Size(max = 200, message = "一次最多导入200条评测样本")
    private List<RagEvalSampleCreateRequest> samples;
}
