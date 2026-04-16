package com.aiassistant.learning.vo.material;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialSegmentVO {

    private Long id;

    private Integer segmentNo;

    private Integer pageNo;

    private String sectionTitle;

    private String contentText;
}
