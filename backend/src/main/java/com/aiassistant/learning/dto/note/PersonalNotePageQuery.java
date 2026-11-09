package com.aiassistant.learning.dto.note;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PersonalNotePageQuery {
    @Size(max = 200)
    private String keyword;
    @Min(0)
    private Long notebookId;
    @Pattern(regexp = "GENERAL|MATERIAL|RESOURCE|QUESTION")
    private String sourceType;
    @Pattern(regexp = "READING|QUESTION")
    private String sourceGroup;
    @Min(1)
    private Long sourceId;
    private Boolean favorite;
    @NotNull @Min(1) @Max(1000000)
    private Long pageNum = 1L;
    @NotNull @Min(1) @Max(100)
    private Long pageSize = 20L;
}
