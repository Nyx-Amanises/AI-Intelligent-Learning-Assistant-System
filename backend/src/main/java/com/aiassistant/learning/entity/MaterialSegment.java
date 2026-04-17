package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("material_segment")
public class MaterialSegment extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long materialId;

    private Integer segmentNo;

    private Integer pageNo;

    private String sectionTitle;

    private String contentText;

    private Integer tokenEstimate;

    private String keywords;

    private String embeddingStatus;

    @TableField(value = "embedding_model", select = false)
    private String embeddingModel;

    @TableField(value = "embedding_task_id", select = false)
    private Long embeddingTaskId;

    @TableField(value = "vector_id", select = false)
    private String vectorId;

    @TableField(value = "embedded_at", select = false)
    private LocalDateTime embeddedAt;
}
