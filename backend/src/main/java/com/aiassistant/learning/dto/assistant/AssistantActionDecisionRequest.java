package com.aiassistant.learning.dto.assistant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 确认或取消指定版本的动作；任务参数只能来自服务端已保存的预览。 */
@Data
public class AssistantActionDecisionRequest {

    @NotNull(message = "动作版本不能为空")
    @Min(value = 1, message = "动作版本必须大于零")
    private Long revision;

    @NotBlank(message = "动作决定不能为空")
    @Pattern(regexp = "^(CONFIRM|CANCEL)$", message = "动作决定只允许 CONFIRM 或 CANCEL")
    private String decision;
}
