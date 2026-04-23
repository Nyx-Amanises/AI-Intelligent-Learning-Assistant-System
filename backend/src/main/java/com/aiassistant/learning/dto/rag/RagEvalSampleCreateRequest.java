package com.aiassistant.learning.dto.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 创建单条 RAG 评测样本的请求参数。
 *
 * <p>一条样本通常表示：用户会问什么问题，以及理想情况下检索应该命中哪些段落或页码。</p>
 */
@Data
public class RagEvalSampleCreateRequest {

    /** 样本实际检索的资料 ID；为空时默认使用评测集绑定的资料。 */
    private Long materialId;

    /** 测试问题，也就是要送入 RAG 检索的查询文本。 */
    @NotBlank(message = "查询问题不能为空")
    @Size(max = 500, message = "查询问题不能超过500个字符")
    private String queryText;

    /** 期望命中的资料分段 ID 列表，用于计算段落级召回。 */
    private List<Long> expectedSegmentIds;

    /** 期望命中的页码列表，用于没有段落 ID 时做页级命中判断。 */
    private List<Integer> expectedPageNos;

    /** 期望答案关键词，主要用于人工查看样本，不直接参与当前指标计算。 */
    @Size(max = 500, message = "期望关键词不能超过500个字符")
    private String expectedKeywords;

    /** 样本标签，例如“定义题”“案例题”“CMRC2018”。 */
    @Size(max = 80, message = "标签不能超过80个字符")
    private String tag;

    /** 样本难度，1 最简单，5 最难。 */
    @Min(value = 1, message = "难度最小为1")
    @Max(value = 5, message = "难度最大为5")
    private Integer difficulty;

    /** 样本来源类型，例如 HUMAN、IMPORTED。 */
    @Size(max = 30, message = "来源类型不能超过30个字符")
    private String sourceType;

    /** 样本备注，用于记录人工说明或原始数据来源。 */
    @Size(max = 500, message = "备注不能超过500个字符")
    private String note;
}
