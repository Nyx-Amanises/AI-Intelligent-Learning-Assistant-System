package com.aiassistant.learning.dto.practice;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
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

    /** 可选专项练习范围；不传时练习整套题。 */
    @Size(min = 1, max = 100, message = "专项练习题目数量必须在1至100之间")
    private List<@NotNull(message = "题目ID不能为空") @Positive(message = "题目ID必须为正整数") Long> questionIds;
}
