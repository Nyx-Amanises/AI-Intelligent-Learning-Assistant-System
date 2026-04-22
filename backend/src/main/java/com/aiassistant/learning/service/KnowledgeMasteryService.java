package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.mastery.KnowledgeMasteryQuery;
import com.aiassistant.learning.vo.mastery.KnowledgeMasteryOverviewVO;

/**
 * 知识掌握度业务接口。
 */
public interface KnowledgeMasteryService {

    /**
     * 按知识点聚合当前用户的练习表现。
     *
     * @param userId 当前登录用户 ID
     * @param query 查询条件
     * @return 知识掌握度总览
     */
    KnowledgeMasteryOverviewVO overview(Long userId, KnowledgeMasteryQuery query);
}
