package com.aiassistant.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("study_resource_user")
public class StudyResourceUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long resourceId;
    private Long materialId;
    private Boolean bookmarked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
