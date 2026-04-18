package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RagRetrieveAssistantTool extends AbstractAssistantTool {

    private static final int DEFAULT_LIMIT = 5;

    private final RetrievalService retrievalService;

    public RagRetrieveAssistantTool(RetrievalService retrievalService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.retrievalService = retrievalService;
    }

    @Override
    public String name() {
        return "rag.retrieve";
    }

    @Override
    public boolean supports(ToolContext context) {
        return AssistantToolSupport.resolveMaterialId(context.session()) != null
                && StringUtils.hasText(context.userMessage());
    }

    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime startedAt = LocalDateTime.now();
        Long materialId = AssistantToolSupport.resolveMaterialId(context.session());
        Map<String, Object> args = Map.of(
                "materialId", materialId,
                "queryText", context.userMessage(),
                "limit", DEFAULT_LIMIT
        );
        try {
            List<RetrievedSegment> segments = retrievalService.retrieveMaterialSegments(
                    context.userId(),
                    materialId,
                    context.userMessage(),
                    DEFAULT_LIMIT
            );
            List<RetrievedSegmentVO> segmentVOs = segments.stream()
                    .map(segment -> RetrievedSegmentVO.builder()
                            .segmentId(segment.segmentId())
                            .segmentNo(segment.segmentNo())
                            .pageNo(segment.pageNo())
                            .sectionTitle(segment.sectionTitle())
                            .contentText(segment.contentText())
                            .keywords(segment.keywords())
                            .score(segment.score())
                            .build())
                    .toList();
            StringBuilder summary = new StringBuilder("检索到以下资料依据：").append(System.lineSeparator());
            for (int index = 0; index < segmentVOs.size(); index++) {
                RetrievedSegmentVO segment = segmentVOs.get(index);
                summary.append(index + 1)
                        .append(". 第 ")
                        .append(segment.getPageNo() == null ? "--" : segment.getPageNo())
                        .append(" 页 · 段落#")
                        .append(segment.getSegmentNo() == null ? "--" : segment.getSegmentNo())
                        .append(" · ")
                        .append(AssistantToolSupport.abbreviate(segment.getContentText(), 140))
                        .append(System.lineSeparator());
            }
            if (segmentVOs.isEmpty()) {
                summary = new StringBuilder("当前资料中没有检索到足够相关的片段。");
            }
            return success(name(), args, segmentVOs, summary.toString().trim(), startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }
}
