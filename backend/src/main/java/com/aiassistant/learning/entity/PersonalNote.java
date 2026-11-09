package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseLogicEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PersonalNote extends BaseLogicEntity {
    private Long id;
    private Long userId;
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
}
