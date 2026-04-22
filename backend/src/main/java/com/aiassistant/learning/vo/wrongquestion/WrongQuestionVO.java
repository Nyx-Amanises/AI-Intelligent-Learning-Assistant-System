package com.aiassistant.learning.vo.wrongquestion;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 错题本展示对象。
 *
 * <p>它把练习答案、题目、题集和资料信息合并到一起，
 * 方便前端一次性展示错题详情。</p>
 */
@Data
@Builder
public class WrongQuestionVO {

    /**
     * 练习答案记录 ID，也是错题本操作的主要 ID。
     */
    private Long answerId;

    /**
     * 练习会话 ID。
     */
    private Long sessionId;

    /**
     * 题目 ID。
     */
    private Long questionId;

    /**
     * 题集 ID。
     */
    private Long questionSetId;

    /**
     * 关联资料 ID。
     */
    private Long materialId;

    /**
     * 资料标题。
     */
    private String materialTitle;

    /**
     * 题集标题。
     */
    private String questionSetTitle;

    /**
     * 练习名称。
     */
    private String sessionName;

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
     * 用户答案。
     */
    private String userAnswer;

    /**
     * 是否正确。
     */
    private Integer isCorrect;

    /**
     * 本题获得分数。
     */
    private Integer obtainedScore;

    /**
     * 本题满分。
     */
    private Integer fullScore;

    /**
     * 判分模式，例如 RULE、AI。
     */
    private String reviewMode;

    /**
     * 判分说明。
     */
    private String reviewComment;

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
     * 作答时间。
     */
    private LocalDateTime answerTime;

    /**
     * 作答记录创建时间。
     */
    private LocalDateTime createdAt;
}
