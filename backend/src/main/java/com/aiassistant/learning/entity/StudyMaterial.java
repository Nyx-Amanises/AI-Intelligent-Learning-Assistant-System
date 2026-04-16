package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseLogicEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("study_material")
public class StudyMaterial extends BaseLogicEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String materialType;

    private String sourceType;

    private String fileName;

    private String fileUrl;

    private Long fileSize;

    private String coverUrl;

    private String parseStatus;

    private String summaryStatus;

    private Integer difficultyLevel;

    private String tags;

    private Integer totalPages;

    private Integer totalCharacters;

    private LocalDateTime lastStudyTime;
}
