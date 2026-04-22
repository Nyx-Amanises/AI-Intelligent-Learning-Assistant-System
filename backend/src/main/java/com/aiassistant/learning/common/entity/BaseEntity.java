package com.aiassistant.learning.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 基础实体父类。
 *
 * <p>多个数据库表都会有创建时间和更新时间，所以抽到父类里复用。
 * 子类继承它后，就不需要在每个实体里重复声明这两个字段。</p>
 */
@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 记录创建时间。
     *
     * <p>{@link FieldFill#INSERT} 表示新增数据时由 MyBatis-Plus 自动填充。</p>
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间。
     *
     * <p>{@link FieldFill#INSERT_UPDATE} 表示新增和更新数据时都会自动填充。</p>
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
