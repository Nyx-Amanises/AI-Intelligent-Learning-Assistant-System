package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RAG 评测运行明细实体，对应数据库表 rag_eval_run_item。
 *
 * <p>一次运行会对每条样本产生一条明细，记录实际检索结果和样本级指标。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rag_eval_run_item")
public class RagEvalRunItem extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属评测运行 ID。 */
    private Long runId;

    /** 所属评测集 ID。 */
    private Long datasetId;

    /** 对应的评测样本 ID。 */
    private Long sampleId;

    /** 记录所属用户 ID。 */
    private Long userId;

    /** 本条样本检索的资料 ID。 */
    private Long materialId;

    /** 本条样本的查询问题。 */
    private String queryText;

    /** JSON 字符串，保存期望命中的分段 ID 列表。 */
    private String expectedSegmentIds;

    /** JSON 字符串，保存期望命中的页码列表。 */
    private String expectedPageNos;

    /** JSON 字符串，保存实际检索到的分段 ID 列表。 */
    private String retrievedSegmentIds;

    /** JSON 字符串，保存实际检索到的页码列表。 */
    private String retrievedPageNos;

    /** 首次命中的排名，未命中时为空。 */
    private Integer hitRank;

    /** 命中排名的倒数，用于计算 MRR。 */
    private Double reciprocalRank;

    /** 当前样本的 Top 1 召回率。 */
    @TableField("recall_at_1")
    private Double recallAt1;

    /** 当前样本的 Top 3 召回率。 */
    @TableField("recall_at_3")
    private Double recallAt3;

    /** 当前样本的 Top 5 召回率。 */
    @TableField("recall_at_5")
    private Double recallAt5;

    /** 检索耗时，单位毫秒。 */
    private Long latencyMs;

    /** 当前样本评测失败时的错误信息。 */
    private String errorMessage;
}
