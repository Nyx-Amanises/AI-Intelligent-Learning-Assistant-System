package com.aiassistant.learning.dto.ai;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 重新生成由用户审阅完整设置后提交，旧配置缺失时必须显式确认。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionRegenerateRequest extends QuestionGenerateRequest {
    private Boolean configurationReviewed;

    public QuestionRegenerateRequest() {
        // 重新生成必须显式确认难度，不能继承普通生成的默认值悄悄改变原配置。
        setDifficultyLevel(null);
    }
}
