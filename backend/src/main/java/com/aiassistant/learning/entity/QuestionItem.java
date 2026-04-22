package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目实体。
 *
 * <p>对应数据库表 question_item。每条记录表示题集中的一道题，
 * 包含题干、选项、参考答案、解析、知识点和分值等信息。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_item")
public class QuestionItem extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属题集 ID。
     */
    private Long questionSetId;

    /**
     * 题型，例如 SINGLE、MULTIPLE、JUDGE、SHORT。
     */
    private String questionType;

    /**
     * 题干文本。
     */
    private String stemText;

    /**
     * A 选项。
     */
    private String optionA;

    /**
     * B 选项。
     */
    private String optionB;

    /**
     * C 选项。
     */
    private String optionC;

    /**
     * D 选项。
     */
    private String optionD;

    /**
     * 标准答案或参考答案。
     */
    private String correctAnswer;

    /**
     * 答案解析。
     */
    private String answerAnalysis;

    /**
     * 关联知识点。
     */
    private String knowledgePoint;

    /**
     * 题目难度等级。
     */
    private Integer difficultyLevel;

    /**
     * 题目分值。
     */
    private Integer score;

    /**
     * 题目排序号，用于保持题集内题目顺序。
     */
    private Integer sortNo;
}
