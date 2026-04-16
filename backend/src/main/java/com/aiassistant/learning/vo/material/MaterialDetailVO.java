package com.aiassistant.learning.vo.material;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialDetailVO {

    private Long id;

    private String title;

    private String materialType;

    private String sourceType;

    private String fileName;

    private String fileUrl;

    private Long fileSize;

    private String parseStatus;

    private String summaryStatus;

    private Integer difficultyLevel;

    private String tags;

    private Integer totalPages;

    private Integer totalCharacters;

    private LocalDateTime lastStudyTime;

    private LocalDateTime createdAt;

    private List<MaterialSegmentVO> segments;
}
