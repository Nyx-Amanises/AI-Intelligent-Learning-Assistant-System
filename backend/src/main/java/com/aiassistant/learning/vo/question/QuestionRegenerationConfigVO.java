package com.aiassistant.learning.vo.question;

import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionRegenerationConfigVO {
    private Long sourceQuestionSetId;
    private Long materialId;
    private String title;
    /** SAVED 为原始设置；LEGACY_REVIEW 仅题型数量和难度可信，须确认范围与模型。 */
    private String parameterMode;
    private String notice;
    private QuestionGenerateRequest request;
}
