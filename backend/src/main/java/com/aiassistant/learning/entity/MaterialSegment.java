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
}
