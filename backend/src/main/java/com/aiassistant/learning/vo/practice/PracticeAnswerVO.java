package com.aiassistant.learning.vo.practice;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PracticeAnswerVO {

    private Long questionId;

    private String stemText;

    private String correctAnswer;

    private String userAnswer;

    private Integer isCorrect;

    private Integer obtainedScore;

    private String answerAnalysis;
}
