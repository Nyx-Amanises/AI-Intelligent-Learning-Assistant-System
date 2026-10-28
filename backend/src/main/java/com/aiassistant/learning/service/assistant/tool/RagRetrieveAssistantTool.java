package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.RetrievalService.RetrievalHit;
import com.aiassistant.learning.service.RetrievalService.RetrievalResult;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.assistant.AssistantRagEvidenceVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * RAG 检索助手工具。
 *
 * <p>用于围绕当前资料召回相关片段，再让助手基于片段回答问题。工具结果是带编号的引用列表
 * （资料名、页码、段落都来自检索结果），前端据此展示「参考来源」，模型回答时用 [编号] 对应。</p>
 */
@Component
public class RagRetrieveAssistantTool extends AbstractAssistantTool {

    /** 默认召回片段数量。 */
    private static final int DEFAULT_LIMIT = 5;

    /** 工具摘要里每条片段展示的字数，完整正文在结果 JSON 中。 */
    private static final int SUMMARY_EXCERPT_LENGTH = 80;

    /**
     * 词面覆盖分达到这个值，认为片段与问题明显相关。
     *
     * <p>词面分是问题中的词在片段里出现的比例，与具体向量模型无关，适合作为判断依据是否充分的主信号。</p>
     */
    static final double MIN_LEXICAL_EVIDENCE = 0.15D;

    /**
     * 向量相似度达到这个值，也认为片段与问题相关（用于同义改写、字面不重合的问题）。
     *
     * <p>不同向量模型的相似度分布不同，这里取偏保守的值；Mock 向量不参与判断。</p>
     */
    static final double MIN_SEMANTIC_EVIDENCE = 0.45D;

    /** 资料检索服务。 */
    private final RetrievalService retrievalService;

    public RagRetrieveAssistantTool(RetrievalService retrievalService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.retrievalService = retrievalService;
    }

    /**
     * 工具名称。
     */
    @Override
    public String name() {
        return "rag.retrieve";
    }

    /**
     * 当前会话绑定资料且用户有问题文本时支持检索。
     */
    @Override
    public boolean supports(ToolContext context) {
        return AssistantToolSupport.resolveMaterialId(context.session()) != null
                && StringUtils.hasText(context.userMessage());
    }

    /**
     * 执行资料片段检索，返回带编号和出处的引用列表。
     */
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
            RetrievalResult retrieval = retrievalService.retrieve(
                    context.userId(),
                    materialId,
                    context.userMessage(),
                    DEFAULT_LIMIT
            );
            AssistantRagEvidenceVO evidence = buildEvidence(retrieval, context.userMessage());
            return success(name(), args, evidence, buildSummary(evidence), startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }

    /**
     * 把检索结果整理成引用列表，并判断依据是否充分。
     */
    static AssistantRagEvidenceVO buildEvidence(RetrievalResult retrieval, String queryText) {
        List<AssistantRagEvidenceVO.Citation> citations = new ArrayList<>();
        for (int index = 0; index < retrieval.hits().size(); index++) {
            RetrievalHit hit = retrieval.hits().get(index);
            RetrievedSegment segment = hit.segment();
            citations.add(AssistantRagEvidenceVO.Citation.builder()
                    .index(index + 1)
                    .segmentId(segment.segmentId())
                    .segmentNo(segment.segmentNo())
                    .pageNo(segment.pageNo())
                    .sectionTitle(segment.sectionTitle())
                    .locationText(formatLocation(segment, retrieval.materialTitle()))
                    .contentText(segment.contentText())
                    .score(hit.relevanceScore())
                    .semanticScore(hit.semanticScore())
                    .lexicalScore(hit.lexicalScore())
                    .build());
        }
        return AssistantRagEvidenceVO.builder()
                .materialId(retrieval.materialId())
                .materialTitle(retrieval.materialTitle())
                .queryText(queryText)
                .retrievalMode(retrieval.mode())
                .degraded(retrieval.degraded())
                .degradeReason(retrieval.degradeReason())
                .mockEmbedding(retrieval.mockEmbedding())
                .evidenceLevel(resolveEvidenceLevel(retrieval))
                .citations(citations)
                .build();
    }

    /**
     * 判断检索依据是否充分。
     *
     * <ul>
     *   <li>没有任何片段：NONE；</li>
     *   <li>任一片段词面覆盖分达标，或（非 Mock 时）向量相似度达标：SUFFICIENT；</li>
     *   <li>否则：WEAK，片段与问题相关度都较低，资料里可能没有直接答案。</li>
     * </ul>
     */
    static String resolveEvidenceLevel(RetrievalResult retrieval) {
        if (retrieval.hits().isEmpty()) {
            return AssistantRagEvidenceVO.EVIDENCE_NONE;
        }
        boolean lexicallySupported = retrieval.hits().stream()
                .anyMatch(hit -> hit.lexicalScore() >= MIN_LEXICAL_EVIDENCE);
        boolean semanticallySupported = !retrieval.mockEmbedding() && retrieval.hits().stream()
                .map(RetrievalHit::semanticScore)
                .filter(Objects::nonNull)
                .anyMatch(score -> score >= MIN_SEMANTIC_EVIDENCE);
        return lexicallySupported || semanticallySupported
                ? AssistantRagEvidenceVO.EVIDENCE_SUFFICIENT
                : AssistantRagEvidenceVO.EVIDENCE_WEAK;
    }

    /**
     * 生成出处描述。PDF 等资料的章节标题里通常已经带了页码和段号，这里避免重复。
     */
    static String formatLocation(RetrievedSegment segment, String materialTitle) {
        String sectionTitle = StringUtils.hasText(segment.sectionTitle()) ? segment.sectionTitle().trim() : "";
        List<String> parts = new ArrayList<>();
        if (segment.pageNo() != null && !sectionTitle.replace(" ", "").contains("第" + segment.pageNo() + "页")) {
            parts.add("第 " + segment.pageNo() + " 页");
        }
        if (segment.segmentNo() != null && !sectionTitle.replace(" ", "").contains("第" + segment.segmentNo() + "段")) {
            parts.add("第 " + segment.segmentNo() + " 段");
        }
        if (StringUtils.hasText(sectionTitle) && !sectionTitle.equals(materialTitle)) {
            parts.add(sectionTitle);
        }
        return parts.isEmpty() ? "资料片段" : String.join(" · ", parts);
    }

    /**
     * 给用户看的简短摘要；不调用模型时（例如 Mock 模式）它也会作为兜底回复的一部分。
     */
    private String buildSummary(AssistantRagEvidenceVO evidence) {
        String material = StringUtils.hasText(evidence.getMaterialTitle()) ? "《" + evidence.getMaterialTitle() + "》" : "当前资料";
        if (evidence.getCitations().isEmpty()) {
            return "在" + material + "中没有检索到相关片段，资料里可能没有这部分内容。";
        }
        StringBuilder summary = new StringBuilder("在")
                .append(material)
                .append("中检索到 ")
                .append(evidence.getCitations().size())
                .append(" 条相关片段：");
        for (AssistantRagEvidenceVO.Citation citation : evidence.getCitations()) {
            summary.append(System.lineSeparator())
                    .append('[').append(citation.getIndex()).append("] ")
                    .append(citation.getLocationText())
                    .append("：")
                    .append(AssistantToolSupport.abbreviate(citation.getContentText(), SUMMARY_EXCERPT_LENGTH));
        }
        if (Boolean.TRUE.equals(evidence.getDegraded())) {
            summary.append(System.lineSeparator()).append("（").append(evidence.getDegradeReason()).append("）");
        }
        if (AssistantRagEvidenceVO.EVIDENCE_WEAK.equals(evidence.getEvidenceLevel())) {
            summary.append(System.lineSeparator()).append("（这些片段与问题的相关度较低，资料中可能没有直接答案）");
        }
        return summary.toString();
    }
}
