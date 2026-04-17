package com.aiassistant.learning.vo.practice;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PracticeAnswerVO {

    private Long questionId;

    private String questionType;

    private String stemText;

    private String optionA;

    private String optionB;

    private String optionC;

    private String optionD;

    private String correctAnswer;

    private String referenceAnswer;

    private String userAnswer;

    private Integer isCorrect;

    private Integer obtainedScore;

    private Integer aiScore;

    private String reviewMode;

    private String reviewLabel;

    private String reviewComment;

    private String answerAnalysis;
}
