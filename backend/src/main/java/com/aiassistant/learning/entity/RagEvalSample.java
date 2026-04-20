package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rag_eval_sample")
public class RagEvalSample extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private Long userId;

    private Long materialId;

    private String queryText;

    private String expectedSegmentIds;

    private String expectedPageNos;

    private String expectedKeywords;

    private String tag;

    private Integer difficulty;

    private String sourceType;

    private String note;
}
