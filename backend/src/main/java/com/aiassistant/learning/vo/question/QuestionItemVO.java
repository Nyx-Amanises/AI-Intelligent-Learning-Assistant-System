package com.aiassistant.learning.vo.question;

import lombok.Builder;
import lombok.Data;

/**
 * 题目返回对象。
 */
@Data
@Builder
public class QuestionItemVO {

    /**
     * 题目 ID。
     */
    private Long id;

    /**
     * 题型。
     */
    private String questionType;

    /**
     * 题干。
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
     * 正确答案或参考答案。
     */
    private String correctAnswer;

    /**
     * 答案解析。
     */
    private String answerAnalysis;

    /**
     * 知识点。
     */
    private String knowledgePoint;

    /**
     * 难度等级。
     */
    private Integer difficultyLevel;

    /**
     * 分值。
     */
    private Integer score;

    /**
     * 排序号。
     */
    private Integer sortNo;
}
