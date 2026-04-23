package com.aiassistant.learning.dto.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 更新单条 RAG 评测样本的请求参数。
 */
@Data
public class RagEvalSampleUpdateRequest {

    /** 样本实际检索的资料 ID；为空时沿用评测集绑定资料。 */
    private Long materialId;

    /** 更新后的测试问题。 */
    @NotBlank(message = "查询问题不能为空")
    @Size(max = 500, message = "查询问题不能超过500个字符")
    private String queryText;

    /** 更新后的期望命中分段 ID 列表。 */
    private List<Long> expectedSegmentIds;

    /** 更新后的期望命中页码列表。 */
    private List<Integer> expectedPageNos;

    /** 更新后的期望关键词。 */
    @Size(max = 500, message = "期望关键词不能超过500个字符")
    private String expectedKeywords;

    /** 更新后的样本标签。 */
    @Size(max = 80, message = "标签不能超过80个字符")
    private String tag;

    /** 更新后的难度，1 到 5。 */
    @Min(value = 1, message = "难度最小为1")
    @Max(value = 5, message = "难度最大为5")
    private Integer difficulty;

    /** 更新后的样本来源类型。 */
    @Size(max = 30, message = "来源类型不能超过30个字符")
    private String sourceType;

    /** 更新后的备注。 */
    @Size(max = 500, message = "备注不能超过500个字符")
    private String note;
}
