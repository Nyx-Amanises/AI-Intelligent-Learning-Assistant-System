package com.aiassistant.learning.dto.resource;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResourceBookmarkRequest {
    @NotNull(message = "请选择是否收藏")
    private Boolean bookmarked;
}
