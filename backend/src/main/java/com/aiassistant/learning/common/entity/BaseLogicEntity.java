package com.aiassistant.learning.common.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 带逻辑删除字段的基础实体父类。
 *
 * <p>逻辑删除的意思是：删除数据时不真正从数据库移除，而是把 deleted 字段改成已删除状态。
 * 这样后续仍然可以追溯历史数据，也能降低误删带来的风险。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLogicEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 逻辑删除标记。
     *
     * <p>{@link TableLogic} 会让 MyBatis-Plus 在删除时自动更新该字段，
     * 并在普通查询时默认过滤已删除的数据。</p>
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
