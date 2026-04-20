package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rag_eval_run_item")
public class RagEvalRunItem extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long datasetId;

    private Long sampleId;

    private Long userId;

    private Long materialId;

    private String queryText;

    private String expectedSegmentIds;

    private String expectedPageNos;

    private String retrievedSegmentIds;

    private String retrievedPageNos;

    private Integer hitRank;

    private Double reciprocalRank;

    @TableField("recall_at_1")
    private Double recallAt1;

    @TableField("recall_at_3")
    private Double recallAt3;

    @TableField("recall_at_5")
    private Double recallAt5;

    private Long latencyMs;

    private String errorMessage;
}
