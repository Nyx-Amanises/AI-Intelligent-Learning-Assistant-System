package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.mastery.KnowledgeMasteryQuery;
import com.aiassistant.learning.vo.mastery.KnowledgeMasteryOverviewVO;

public interface KnowledgeMasteryService {

    KnowledgeMasteryOverviewVO overview(Long userId, KnowledgeMasteryQuery query);
}
