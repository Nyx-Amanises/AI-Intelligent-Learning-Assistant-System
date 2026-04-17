package com.aiassistant.learning.vo.practice;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PracticeReviewStatusVO {

    private Long sessionId;

    private Boolean completed;

    private Boolean pending;
}
