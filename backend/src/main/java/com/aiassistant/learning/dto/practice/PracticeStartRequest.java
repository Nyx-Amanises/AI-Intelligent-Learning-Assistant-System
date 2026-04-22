package com.aiassistant.learning.dto.practice;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 开始练习请求参数。
 */
@Data
public class PracticeStartRequest {

    /**
     * 要练习的题集 ID，不能为空。
     */
    @NotNull(message = "题集ID不能为空")
    private Long questionSetId;
}
