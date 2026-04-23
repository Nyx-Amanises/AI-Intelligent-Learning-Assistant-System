package com.aiassistant.learning.dto.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.Data;

/**
 * 启动 RAG 评测运行的请求参数。
 */
@Data
public class RagEvalRunRequest {

    /** 每个问题最多取回的检索结果数量。 */
    @Min(value = 1, message = "检索条数最小为1")
    @Max(value = 20, message = "检索条数最大为20")
    private Integer limit = 5;

    /** 指定只评测部分样本；为空时评测整个评测集。 */
    private List<Long> sampleIds;
}
