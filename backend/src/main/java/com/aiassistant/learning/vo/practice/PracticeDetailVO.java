package com.aiassistant.learning.vo.practice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PracticeDetailVO {

    private Long sessionId;

    private Long questionSetId;

    private String sessionName;

    private Integer totalQuestions;

    private Integer correctCount;

    private Integer totalScore;

    private Integer obtainedScore;

    private BigDecimal accuracyRate;

    private String sessionStatus;

    private LocalDateTime startTime;

    private LocalDateTime submitTime;

    private List<PracticeAnswerVO> answers;
}
