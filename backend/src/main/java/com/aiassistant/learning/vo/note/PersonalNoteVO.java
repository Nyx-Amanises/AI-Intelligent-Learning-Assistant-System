package com.aiassistant.learning.vo.note;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PersonalNoteVO {
    private Long id;
    private String clientKey;
    private Long notebookId;
    private String notebookName;
    private String title;
    private String contentJson;
    private String contentText;
    private String sourceType;
    private Long sourceId;
    private Long practiceSessionId;
    private Integer pageNumber;
    private String sourceTitle;
    private String sourceExcerpt;
    private Boolean favorite;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
