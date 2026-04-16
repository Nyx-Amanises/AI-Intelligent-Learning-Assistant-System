package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseLogicEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("study_note")
public class StudyNote extends BaseLogicEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long materialId;

    private String title;

    private String noteType;

    private String contentText;

    private String sourceSegmentIds;

    private Integer isFavorite;
}
