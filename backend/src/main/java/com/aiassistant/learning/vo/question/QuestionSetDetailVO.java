package com.aiassistant.learning.vo.question;

import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionSetDetailVO {

    private Long id;

    private Long materialId;

    private String title;

    private Integer questionCount;

    private Integer totalScore;

    private Integer difficultyLevel;

    private String status;

    private LocalDateTime createdAt;

    private List<RetrievedSegmentVO> sourceSegments;

    private List<QuestionItemVO> questions;
}
