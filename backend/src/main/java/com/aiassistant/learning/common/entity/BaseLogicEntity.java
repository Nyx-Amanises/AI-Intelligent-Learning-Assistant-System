package com.aiassistant.learning.common.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLogicEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
