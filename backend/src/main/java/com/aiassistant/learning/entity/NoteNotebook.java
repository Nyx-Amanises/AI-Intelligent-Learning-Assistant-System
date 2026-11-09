package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseLogicEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NoteNotebook extends BaseLogicEntity {
    private Long id;
    private Long userId;
    private String name;
    private String color;
    private Long noteCount;
}
