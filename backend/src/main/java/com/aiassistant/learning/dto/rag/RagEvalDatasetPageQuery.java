package com.aiassistant.learning.dto.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * RAG 评测集分页查询条件。
 */
@Data
public class RagEvalDatasetPageQuery {

    /** 当前页码，从 1 开始。 */
    @Min(value = 1, message = "页码最小为1")
    private Long current = 1L;

    /** 每页条数，后端限制最大 50 条，防止一次查太多。 */
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 50, message = "每页条数最大为50")
    private Long size = 10L;

    /** 按资料 ID 过滤评测集，可为空。 */
    private Long materialId;

    /** 按名称或说明模糊搜索，可为空。 */
    private String keyword;
}
