package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.resource.ResourceAdminPageQuery;
import com.aiassistant.learning.dto.resource.ResourcePageQuery;
import com.aiassistant.learning.dto.resource.ResourceStatusRequest;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.resource.ResourceVO;
import java.util.List;

public interface StudyResourceService {
    PageVO<ResourceVO> page(Long userId, ResourcePageQuery query);
    ResourceVO detail(Long userId, Long resourceId);
    void bookmark(Long userId, Long resourceId, boolean bookmarked);
    Long importMaterial(Long userId, Long resourceId);
    List<ResourceVO> recommendations(Long userId, String knowledgePoint, int limit);
    PageVO<ResourceVO> adminPage(Long userId, ResourceAdminPageQuery query);
    ResourceVO adminDetail(Long userId, Long resourceId);
    void updateStatus(Long userId, Long resourceId, ResourceStatusRequest request);
}
