package com.aiassistant.learning.dto.resource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResourcePageQuery {
    @Size(max = 100, message = "关键词不能超过100个字符")
    private String keyword;
    @Size(max = 100, message = "学科不能超过100个字符")
    private String subject;
    @Pattern(regexp = "TEXTBOOK|COURSE_PACK|REVIEW_PACK|KNOWLEDGE_CARD", message = "资源类别不正确")
    private String resourceKind;
    @Pattern(regexp = "OPEN_RESOURCE|CATALOG_ONLY", message = "资源访问方式不正确")
    private String accessMode;
    @NotNull
    private Boolean bookmarkedOnly = false;
    @NotNull
    @Min(1)
    @Max(1000000)
    private Long current = 1L;
    @NotNull
    @Min(1)
    @Max(50)
    private Long size = 12L;
}
