package com.aiassistant.learning.vo.wrongquestion;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WrongQuestionVO {

    private Long answerId;

    private Long sessionId;

    private Long questionId;

    private Long questionSetId;

    private Long materialId;

    private String materialTitle;

    private String questionSetTitle;

    private String sessionName;

    private String questionType;

    private String stemText;

    private String optionA;

    private String optionB;

    private String optionC;

    private String optionD;

    private String correctAnswer;

    private String userAnswer;

    private Integer isCorrect;

    private Integer obtainedScore;

    private Integer fullScore;

    private String reviewMode;

    private String reviewComment;

    private String answerAnalysis;

    private String knowledgePoint;

    private Integer difficultyLevel;

    private LocalDateTime answerTime;

    private LocalDateTime createdAt;
}
