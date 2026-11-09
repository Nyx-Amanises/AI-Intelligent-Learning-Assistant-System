package com.aiassistant.learning.vo.note;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class NotebookVO {
    private Long id;
    private String name;
    private String color;
    private Long noteCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
