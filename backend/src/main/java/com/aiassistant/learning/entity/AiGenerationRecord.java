package com.aiassistant.learning.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI 生成记录实体。
 *
 * <p>对应数据库表 ai_generation_record，用于保存 AI 摘要、题目生成等操作的输入、
 * 输出、模型名称和执行状态，方便后续查看历史和排查问题。</p>
 */
@Data
@TableName("ai_generation_record")
public class AiGenerationRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 生成记录主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 关联资料 ID。
     */
    private Long materialId;

    /**
     * 如果生成结果保存为笔记，这里记录笔记 ID。
     */
    private Long noteId;

    /**
     * 任务类型，例如 SUMMARY。
     */
    private String taskType;

    /**
     * 摘要类型，例如 STANDARD、MINDMAP。
     */
    private String summaryType;

    /**
     * 使用的模型名称。
     */
    private String modelName;

    /**
     * 发送给 AI 的系统提示词。
     */
    private String promptText;

    /**
     * 发送给 AI 的输入文本。
     */
    private String inputText;

    /**
     * AI 返回文本。
     */
    private String outputText;

    /**
     * 生成状态，例如 SUCCESS、FAILED。
     */
    private String status;

    /**
     * 失败错误信息。
     */
    private String errorMessage;

    /**
     * 估算或实际使用的 token 数。
     */
    private Integer tokenUsed;

    /**
     * 响应耗时，单位毫秒。
     */
    private Integer responseTimeMs;

    /**
     * 创建时间。
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
