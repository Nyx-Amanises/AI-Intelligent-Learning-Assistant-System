package com.aiassistant.learning.dto.practice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * 提交练习请求参数。
 *
 * <p>前端提交一次练习时，需要告诉后端是哪一次练习会话，
 * 并提交每道题的答案。</p>
 */
@Data
public class PracticeSubmitRequest {

    /**
     * 练习会话 ID，不能为空。
     */
    @NotNull(message = "练习会话ID不能为空")
    private Long sessionId;

    /**
     * 答案列表，不能为空。
     *
     * <p>{@link Valid} 会继续校验列表中每个 PracticeAnswerRequest 对象。</p>
     */
    @Valid
    @NotEmpty(message = "答案列表不能为空")
    private List<PracticeAnswerRequest> answers;
}
