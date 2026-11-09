package com.aiassistant.learning.vo.resource;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourceVO {
    private Long id;
    private String title;
    private String summary;
    private String subject;
    private List<String> knowledgePoints;
    private Integer difficultyLevel;
    private Integer estimatedMinutes;
    private String resourceType;
    private String resourceKind;
    private String accessMode;
    private String isbn;
    private String publisher;
    private String educationLevel;
    private Integer publicationYear;
    private String languageCode;
    private String coverageNote;
    private String downloadUrl;
    private String coverUrl;
    private String documentUrl;
    private Integer pageCount;
    private String sourceName;
    private String sourceUrl;
    private String author;
    private String licenseCode;
    private String licenseUrl;
    private String licenseNote;
    private String status;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String contentText;
    private boolean bookmarked;
    private Long materialId;
    private String recommendationReason;
}
