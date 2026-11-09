package com.aiassistant.learning.dto.note;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PersonalNoteWriteRequest {
    @Size(max = 200)
    private String title = "";
    @NotNull @Size(max = 200000)
    private String contentJson;
    @NotNull @Size(max = 100000)
    private String contentText = "";
    @Min(0)
    private Long notebookId;
    @Pattern(regexp = "GENERAL|MATERIAL|RESOURCE|QUESTION")
    private String sourceType;
    @Min(1)
    private Long sourceId;
    @Min(1)
    private Long practiceSessionId;
    @Min(1)
    private Integer pageNumber;
    @Size(max = 200)
    private String sourceTitle;
    @Size(max = 2000)
    private String sourceExcerpt;
    @NotNull
    private Boolean favorite = false;
}
