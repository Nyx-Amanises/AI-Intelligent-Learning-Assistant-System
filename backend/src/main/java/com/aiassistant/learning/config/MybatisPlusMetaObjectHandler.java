package com.aiassistant.learning.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * MyBatis-Plus 自动填充处理器。
 *
 * <p>实体字段上声明了自动填充规则后，真正填什么值由这个类决定。
 * 例如新增数据时自动写入 createdAt、updatedAt，避免每个业务方法重复赋值。</p>
 */
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    /**
     * 新增数据时触发。
     *
     * @param metaObject 当前正在插入的实体对象元信息
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    /**
     * 更新数据时触发。
     *
     * @param metaObject 当前正在更新的实体对象元信息
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
