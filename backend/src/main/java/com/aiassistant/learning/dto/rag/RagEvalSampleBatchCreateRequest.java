package com.aiassistant.learning.dto.rag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 批量创建 RAG 评测样本的请求体。
 */
@Data
public class RagEvalSampleBatchCreateRequest {

    /** 待导入的样本列表，每条样本都需要通过自己的字段校验。 */
    @Valid
    @NotEmpty(message = "评测样本不能为空")
    @Size(max = 200, message = "一次最多导入200条评测样本")
    private List<RagEvalSampleCreateRequest> samples;
}
