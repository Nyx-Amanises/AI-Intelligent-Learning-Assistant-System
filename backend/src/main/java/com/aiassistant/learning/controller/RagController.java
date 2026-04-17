package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.aiassistant.learning.vo.rag.RetrievalPreviewVO;
import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RetrievalService retrievalService;

    public RagController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping("/material/{materialId}/retrieve-preview")
    public ApiResponse<RetrievalPreviewVO> retrievePreview(
            @PathVariable Long materialId,
            @RequestParam String queryText,
            @RequestParam(required = false) Integer limit
    ) {
        Long userId = UserContext.getCurrentUserId();
        List<RetrievedSegment> segments = retrievalService.retrieveMaterialSegments(userId, materialId, queryText, limit);
        return ApiResponse.success(RetrievalPreviewVO.builder()
                .materialId(materialId)
                .queryText(queryText)
                .limit(limit == null || limit <= 0 ? segments.size() : limit)
                .hitCount(segments.size())
                .segments(segments.stream()
                        .map(item -> RetrievedSegmentVO.builder()
                                .segmentId(item.segmentId())
                                .segmentNo(item.segmentNo())
                                .pageNo(item.pageNo())
                                .sectionTitle(item.sectionTitle())
                                .contentText(item.contentText())
                                .keywords(item.keywords())
                                .score(item.score())
                                .build())
                        .toList())
                .build());
    }
}
