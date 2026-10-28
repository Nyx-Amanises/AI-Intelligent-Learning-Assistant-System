package com.aiassistant.learning.common.util;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.entity.MaterialSegment;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** 知识点范围的校验、候选整理和资料依据选择，不依赖外部模型或向量服务。 */
public final class QuestionKnowledgeScope {
    public static final int MAX_POINTS = 5;
    public static final int MAX_POINT_LENGTH = 80;
    private static final int MAX_EXCERPT_LENGTH = 2200;
    private static final Pattern LOCATION = Pattern.compile("^(?:第\\s*\\d+\\s*[页段]\\s*[·|｜:：-]?\\s*)+");
    private static final Pattern HEADING = Pattern.compile("^(?:#{1,6}\\s+|\\d+(?:\\.\\d+)*[.、．\\s]+|[一二三四五六七八九十]+[、．.])(.+)$");

    private QuestionKnowledgeScope() { }

    /** 空数组表示整份资料；拒绝无效标签，避免悄悄退回全范围。 */
    public static List<String> normalize(List<String> requested, int questionCount) {
        if (requested == null || requested.isEmpty()) return List.of();
        if (requested.size() > MAX_POINTS) throw new BusinessException("每次最多选择5个知识点");
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : requested) {
            String point = clean(value);
            if (!StringUtils.hasText(point)) throw new BusinessException("知识点不能为空");
            if (point.length() > MAX_POINT_LENGTH) throw new BusinessException("单个知识点不能超过80个字符");
            if (!point.codePoints().anyMatch(Character::isLetterOrDigit)) {
                throw new BusinessException("请输入有效的知识点名称");
            }
            unique.putIfAbsent(key(point), point);
        }
        if (unique.size() > questionCount) throw new BusinessException("题目数量不能少于所选知识点数量");
        return List.copyOf(unique.values());
    }

    /** 由章节、关键词及正文标题提取候选，不把整段正文当知识点。 */
    public static List<String> candidates(List<MaterialSegment> segments) {
        Map<String, String> result = new LinkedHashMap<>();
        for (MaterialSegment segment : segments) {
            if (segment == null || !StringUtils.hasText(segment.getContentText())) continue;
            addCandidate(result, LOCATION.matcher(clean(segment.getSectionTitle())).replaceFirst(""));
            if (StringUtils.hasText(segment.getKeywords())) {
                for (String keyword : segment.getKeywords().split("[,，、;；\\n]")) {
                    if (find(segment.getContentText(), keyword) != null) addCandidate(result, keyword);
                }
            }
            segment.getContentText().lines().limit(80).forEach(line -> {
                Matcher matcher = HEADING.matcher(line.strip());
                if (matcher.matches()) addCandidate(result, matcher.group(1));
            });
            if (result.size() >= 100) break;
        }
        return result.values().stream().limit(100).toList();
    }

    private static void addCandidate(Map<String, String> result, String candidate) {
        String point = clean(candidate);
        if (point.length() < 2 || point.length() > MAX_POINT_LENGTH || point.matches(".*[{};；=].*")) return;
        if (!point.codePoints().anyMatch(Character::isLetterOrDigit)) return;
        result.putIfAbsent(key(point), point);
    }

    /** 每个知识点保留一个独立证据摘录，长资料截取命中位置，避免后面的考点被截掉。 */
    public static List<MaterialSegment> selectSegments(List<MaterialSegment> segments, List<String> points) {
        if (points.isEmpty()) return List.of();
        List<SearchableSegment> searchable = segments.stream()
                .filter(segment -> segment != null && StringUtils.hasText(segment.getContentText()))
                .map(segment -> new SearchableSegment(segment, key(segment.getSectionTitle()), searchText(segment.getContentText())))
                .toList();
        List<MaterialSegment> selected = new ArrayList<>();
        for (String point : points) {
            MaterialSegment best = null;
            Match bestMatch = null;
            int bestScore = -1;
            String expected = key(point);
            for (SearchableSegment candidate : searchable) {
                boolean titleMatch = candidate.title().contains(expected);
                Match match = candidate.content().find(expected);
                boolean textMatch = match != null;
                int score = (titleMatch ? 2 : 0) + (textMatch ? 1 : 0);
                if (score > 0 && score > bestScore) { best = candidate.segment(); bestMatch = match; bestScore = score; }
            }
            if (best == null) {
                throw new BusinessException("资料中未找到知识点“" + point + "”的依据，请使用资料中的名称或更换资料");
            }
            MaterialSegment excerpt = new MaterialSegment();
            excerpt.setId(best.getId());
            excerpt.setMaterialId(best.getMaterialId());
            excerpt.setSegmentNo(best.getSegmentNo());
            excerpt.setPageNo(best.getPageNo());
            excerpt.setSectionTitle(best.getSectionTitle());
            excerpt.setKeywords(point);
            String content = best.getContentText();
            Match match = bestMatch;
            int start = match == null ? 0 : Math.max(0, match.start() - 500);
            int end = Math.min(content.length(), start + MAX_EXCERPT_LENGTH);
            if (match != null && match.end() > end) {
                start = Math.max(0, match.end() - MAX_EXCERPT_LENGTH);
                end = Math.min(content.length(), start + MAX_EXCERPT_LENGTH);
            }
            excerpt.setContentText((start > 0 ? "…" : "") + content.substring(start, end) + (end < content.length() ? "…" : ""));
            selected.add(excerpt);
        }
        return List.copyOf(selected);
    }

    /** 标签匹配忽略空白及大小写，但保留 C++、C# 等名称中的标点。 */
    private static Match find(String source, String point) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(point)) return null;
        return searchText(source).find(key(point));
    }

    private static SearchText searchText(String source) {
        StringBuilder compact = new StringBuilder();
        List<Integer> positions = new ArrayList<>();
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (Character.isWhitespace(character) || Character.isSpaceChar(character)) continue;
            String part = Normalizer.normalize(String.valueOf(character), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
            for (int count = 0; count < part.length(); count++) {
                if (!Character.isWhitespace(part.charAt(count)) && !Character.isSpaceChar(part.charAt(count))) {
                    compact.append(part.charAt(count));
                    positions.add(index);
                }
            }
        }
        return new SearchText(compact.toString(), positions);
    }

    private static String clean(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ");
    }

    private static String key(String value) {
        return clean(value).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private record Match(int start, int end) { }

    private record SearchableSegment(MaterialSegment segment, String title, SearchText content) { }

    private record SearchText(String compact, List<Integer> positions) {
        private Match find(String expected) {
            int found = compact.indexOf(expected);
            if (found < 0 || expected.isEmpty()) return null;
            return new Match(positions.get(found), positions.get(found + expected.length() - 1) + 1);
        }
    }
}
