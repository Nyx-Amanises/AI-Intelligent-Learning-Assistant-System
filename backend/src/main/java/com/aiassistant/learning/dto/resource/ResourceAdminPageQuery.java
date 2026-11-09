package com.aiassistant.learning.dto.resource;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceAdminPageQuery extends ResourcePageQuery {
    @Pattern(regexp = "PUBLISHED|WITHDRAWN|DRAFT", message = "资源状态不正确")
    private String status;
}
