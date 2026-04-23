package com.aiassistant.learning.dto.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 RAG 评测集的请求参数。
 *
 * <p>评测集相当于一组“问题 + 期望命中段落”的集合，用来衡量 RAG 检索质量。</p>
 */
@Data
public class RagEvalDatasetCreateRequest {

    /** 评测集绑定的资料 ID。 */
    @NotNull(message = "资料ID不能为空")
    private Long materialId;

    /** 评测集名称，展示给用户区分不同实验。 */
    @NotBlank(message = "评测集名称不能为空")
    @Size(max = 120, message = "评测集名称不能超过120个字符")
    private String name;

    /** 评测集说明，例如数据来源、评测目的。 */
    @Size(max = 500, message = "评测集说明不能超过500个字符")
    private String description;
}
