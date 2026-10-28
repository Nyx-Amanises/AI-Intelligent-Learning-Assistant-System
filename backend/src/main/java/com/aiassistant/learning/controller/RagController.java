package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.RetrievalService.RetrievalResult;
import com.aiassistant.learning.vo.rag.RetrievalPreviewVO;
import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    /** RAG 检索业务服务，负责向量召回和重排序。 */
    private final RetrievalService retrievalService;

    public RagController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    /**
     * 预览某份资料在指定问题下的 RAG 检索结果。
     *
     * <p>这个接口常用于调试：可以看到问题会命中哪些资料分段、每个分段的得分，
     * 以及本次是向量检索还是降级后的数据库文本检索。</p>
     */
    @GetMapping("/material/{materialId}/retrieve-preview")
    public ApiResponse<RetrievalPreviewVO> retrievePreview(
            @PathVariable Long materialId,
            @RequestParam String queryText,
            @RequestParam(required = false) Integer limit
    ) {
        Long userId = UserContext.getCurrentUserId();
        RetrievalResult result = retrievalService.retrieve(userId, materialId, queryText, limit);
        return ApiResponse.success(RetrievalPreviewVO.builder()
                .materialId(materialId)
                .materialTitle(result.materialTitle())
                .queryText(queryText)
                .limit(limit == null || limit <= 0 ? result.hits().size() : limit)
                .hitCount(result.hits().size())
                .retrievalMode(result.mode())
                .degraded(result.degraded())
                .degradeReason(result.degradeReason())
                .mockEmbedding(result.mockEmbedding())
                .segments(result.hits().stream()
                        .map(hit -> RetrievedSegmentVO.builder()
                                .segmentId(hit.segment().segmentId())
                                .segmentNo(hit.segment().segmentNo())
                                .pageNo(hit.segment().pageNo())
                                .sectionTitle(hit.segment().sectionTitle())
                                .contentText(hit.segment().contentText())
                                .keywords(hit.segment().keywords())
                                .score(hit.relevanceScore())
                                .semanticScore(hit.semanticScore())
                                .lexicalScore(hit.lexicalScore())
                                .build())
                        .toList())
                .build());
    }
}
