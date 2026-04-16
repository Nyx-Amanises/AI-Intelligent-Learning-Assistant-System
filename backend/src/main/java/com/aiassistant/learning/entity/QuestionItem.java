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
@TableName("question_item")
public class QuestionItem extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionSetId;

    private String questionType;

    private String stemText;

    private String optionA;

    private String optionB;

    private String optionC;

    private String optionD;

    private String correctAnswer;

    private String answerAnalysis;

    private String knowledgePoint;

    private Integer difficultyLevel;

    private Integer score;

    private Integer sortNo;
}
