package com.aiassistant.learning.mapper;

import com.aiassistant.learning.entity.MaterialSegment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 资料分段表数据库访问接口。
 *
 * <p>资料解析后会生成多条 MaterialSegment 记录，后续向量化和检索也会频繁查询它。</p>
 */
public interface MaterialSegmentMapper extends BaseMapper<MaterialSegment> {
}
