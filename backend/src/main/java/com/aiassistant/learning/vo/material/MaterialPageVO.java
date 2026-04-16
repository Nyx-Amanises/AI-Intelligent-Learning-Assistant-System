package com.aiassistant.learning.vo.material;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialPageVO {

    private Long id;

    private String title;

    private String materialType;

    private String parseStatus;

    private String summaryStatus;

    private Integer difficultyLevel;

    private String tags;

    private Integer totalCharacters;

    private LocalDateTime lastStudyTime;

    private LocalDateTime createdAt;
}
