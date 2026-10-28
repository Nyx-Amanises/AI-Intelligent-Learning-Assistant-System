package com.aiassistant.learning.vo.assistant;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 助手资料问答的检索依据，作为 rag.retrieve 工具的结果 JSON 保存。
 *
 * <p>出处（资料名、页码、段落）全部来自检索结果本身，前端据此展示「参考来源」，
 * 不依赖模型在回答里自己写出处，避免出处被编造。</p>
 */
@Data
@Builder
public class AssistantRagEvidenceVO {

    /** 依据充分：至少有一条片段与问题明显相关。 */
    public static final String EVIDENCE_SUFFICIENT = "SUFFICIENT";
    /** 依据偏弱：有片段，但与问题的相关度都较低。 */
    public static final String EVIDENCE_WEAK = "WEAK";
    /** 没有检索到任何片段。 */
    public static final String EVIDENCE_NONE = "NONE";

    /** 资料 ID。 */
    private Long materialId;

    /** 资料标题。 */
    private String materialTitle;

    /** 实际用于检索的问题。 */
    private String queryText;

    /** 检索方式：VECTOR 或 TEXT。 */
    private String retrievalMode;

    /** 是否降级为数据库文本检索。 */
    private Boolean degraded;

    /** 降级原因。 */
    private String degradeReason;

    /** 是否使用 Mock 演示向量。 */
    private Boolean mockEmbedding;

    /** 依据充分程度：SUFFICIENT / WEAK / NONE。 */
    private String evidenceLevel;

    /** 按编号排列的引用片段，编号与回答中的 [n] 对应。 */
    private List<Citation> citations;

    /**
     * 单条引用片段。
     */
    @Data
    @Builder
    public static class Citation {

        /** 引用编号，从 1 开始。 */
        private Integer index;

        /** 资料分段 ID。 */
        private Long segmentId;

        /** 分段序号。 */
        private Integer segmentNo;

        /** 页码，非 PDF 资料可能为空。 */
        private Integer pageNo;

        /** 章节标题。 */
        private String sectionTitle;

        /** 给人看的出处描述，例如“第 3 页 · 第 12 段 · 细胞膜的结构”。 */
        private String locationText;

        /** 分段正文。 */
        private String contentText;

        /** 综合得分。 */
        private Double score;

        /** 向量相似度，文本检索时为空。 */
        private Double semanticScore;

        /** 词面覆盖分。 */
        private Double lexicalScore;
    }
}
