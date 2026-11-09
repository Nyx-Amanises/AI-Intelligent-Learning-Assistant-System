package com.aiassistant.learning.dto.note;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

@Data
public class PersonalNoteUpdateRequest {
    @NotNull @Min(1)
    private Integer version;
    @Size(max = 200)
    private String title;
    @Size(max = 200000)
    private String contentJson;
    @Size(max = 100000)
    private String contentText;
    @Min(0)
    private Long notebookId;
    @JsonIgnore @Setter(AccessLevel.NONE)
    private boolean notebookIdProvided;
    private Boolean favorite;
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

    public void setNotebookId(Long value) {
        notebookId = value;
        notebookIdProvided = true;
    }
}
