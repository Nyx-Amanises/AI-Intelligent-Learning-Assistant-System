package com.aiassistant.learning.vo.practice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PracticeSessionPageVO {

    private Long id;

    private String sessionName;

    private Integer totalQuestions;

    private Integer correctCount;

    private Integer obtainedScore;

    private BigDecimal accuracyRate;

    private String sessionStatus;

    private LocalDateTime submitTime;
}
