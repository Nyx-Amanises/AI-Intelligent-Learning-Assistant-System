package com.aiassistant.learning.vo.question;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionItemVO {

    private Long id;

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
