package com.aiassistant.learning.vo.question;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionSetPageVO {

    private Long id;

    private String title;

    private Integer questionCount;

    private Integer totalScore;

    private Integer difficultyLevel;

    private String status;

    private LocalDateTime createdAt;
}
