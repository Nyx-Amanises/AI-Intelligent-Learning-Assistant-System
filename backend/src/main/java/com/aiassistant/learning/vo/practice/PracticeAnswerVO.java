package com.aiassistant.learning.vo.practice;

import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 单题作答详情返回对象。
 *
 * <p>这里同时包含题目内容、用户答案、判分结果和简答题相关资料片段。</p>
 */
@Data
@Builder
public class PracticeAnswerVO {

    /**
     * 题目 ID。
     */
    private Long questionId;

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
     * 标准答案。
     */
    private String correctAnswer;

    /**
     * 参考答案。简答题页面可以用这个字段展示更友好的名称。
     */
    private String referenceAnswer;

    /**
     * 用户答案。
     */
    private String userAnswer;

    /**
     * 是否正确。
     */
    private Integer isCorrect;

    /**
     * 本题得分。
     */
    private Integer obtainedScore;

    /**
     * AI 给出的分数。客观题时通常与 obtainedScore 一致或为空。
     */
    private Integer aiScore;

    /**
     * 判分模式，例如 RULE、AI、AI_PENDING。
     */
    private String reviewMode;

    /**
     * 判分标签，例如“AI 判分：6/10”。
     */
    private String reviewLabel;

    /**
     * 判分说明。
     */
    private String reviewComment;

    /**
     * 答案解析。
     */
    private String answerAnalysis;

    /**
     * 简答题判分时参考的资料片段。
     */
    private List<RetrievedSegmentVO> sourceSegments;
}
