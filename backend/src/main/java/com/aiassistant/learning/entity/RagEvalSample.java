package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RAG 评测样本实体，对应数据库表 rag_eval_sample。
 *
 * <p>一条样本保存一个查询问题，以及人工标注的相关段落或页码。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rag_eval_sample")
public class RagEvalSample extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属评测集 ID。 */
    private Long datasetId;

    /** 样本所属用户 ID。 */
    private Long userId;

    /** 样本实际检索的学习资料 ID。 */
    private Long materialId;

    /** 测试问题文本。 */
    private String queryText;

    /** JSON 字符串，保存期望命中的分段 ID 列表。 */
    private String expectedSegmentIds;

    /** JSON 字符串，保存期望命中的页码列表。 */
    private String expectedPageNos;

    /** 期望答案关键词，主要用于人工检查。 */
    private String expectedKeywords;

    /** 样本标签。 */
    private String tag;

    /** 样本难度，通常为 1 到 5。 */
    private Integer difficulty;

    /** 样本来源类型，例如 HUMAN、IMPORTED。 */
    private String sourceType;

    /** 样本备注。 */
    private String note;
}
