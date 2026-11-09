package com.aiassistant.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("study_resource")
public class StudyResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String summary;
    private String subject;
    private String knowledgePoints;
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
    private String contentText;
    private String status;
    private String reviewNote;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
