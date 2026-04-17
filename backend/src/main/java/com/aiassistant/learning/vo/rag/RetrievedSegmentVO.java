package com.aiassistant.learning.vo.rag;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetrievedSegmentVO {

    private Long segmentId;

    private Integer segmentNo;

    private Integer pageNo;

    private String sectionTitle;

    private String contentText;

    private String keywords;

    private Double score;
}
