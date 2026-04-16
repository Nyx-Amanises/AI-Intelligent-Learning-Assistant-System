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
@TableName("question_set")
public class QuestionSet extends BaseLogicEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long materialId;

    private String title;

    private String sourceType;

    private Integer questionCount;

    private Integer totalScore;

    private Integer difficultyLevel;

    private String status;
}
