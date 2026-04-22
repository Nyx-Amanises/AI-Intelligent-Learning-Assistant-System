package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseLogicEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学习笔记实体。
 *
 * <p>对应数据库表 study_note，用于保存用户基于资料整理的笔记。
 * 当前资料板块尚未集中处理笔记业务，但实体和 Mapper 已经预留。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("study_note")
public class StudyNote extends BaseLogicEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 笔记主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 笔记所属用户 ID。
     */
    private Long userId;

    /**
     * 关联的资料 ID。
     */
    private Long materialId;

    /**
     * 笔记标题。
     */
    private String title;

    /**
     * 笔记类型，例如普通笔记、摘要笔记等。
     */
    private String noteType;

    /**
     * 笔记正文内容。
     */
    private String contentText;

    /**
     * 来源资料分段 ID 列表，可用逗号或 JSON 字符串保存。
     */
    private String sourceSegmentIds;

    /**
     * 是否收藏，通常 1 表示收藏，0 表示未收藏。
     */
    private Integer isFavorite;
}
