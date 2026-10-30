package com.aiassistant.learning.common.util;

import com.aiassistant.learning.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.util.StringUtils;

/** 严格限制长度的字符窗口分块，在章节边界重置重叠，保留页码和章节标题。 */
public final class MaterialTextChunker {

    public static final String STRATEGY = "CHAR_WINDOW_V2";
    public static final int DEFAULT_CHUNK_SIZE = 900;
    public static final int DEFAULT_CHUNK_OVERLAP = 140;

    private MaterialTextChunker() {
    }

    public static Options options(Integer chunkSize, Integer chunkOverlap) {
        int size = chunkSize == null ? DEFAULT_CHUNK_SIZE : chunkSize;
        int overlap = chunkOverlap == null ? DEFAULT_CHUNK_OVERLAP : chunkOverlap;
        if (size < 200 || size > 4000) {
            throw new BusinessException("分块大小必须在200到4000个字符之间");
        }
        if (overlap < 0 || overlap > 500 || overlap >= size) {
            throw new BusinessException("重叠字符数必须在0到500之间，且小于分块大小");
        }
        return new Options(size, overlap);
    }

    public static List<Chunk> split(String text, Integer pageNo, Options options) {
        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("解析结果为空，无法生成分段");
        }
        // 字符位置基于同一份规范化原文，超长单行也会按窗口切开。
        List<Section> sections = new ArrayList<>();
        int sectionStart = 0;
        int offset = 0;
        String currentHeading = null;
        for (String line : normalized.split("\n", -1)) {
            String heading = detectHeading(line);
            if (heading != null) {
                if (offset > sectionStart && StringUtils.hasText(normalized.substring(sectionStart, offset))) {
                    sections.add(new Section(sectionStart, offset, currentHeading));
                    sectionStart = offset;
                }
                currentHeading = heading;
            }
            offset += line.length() + 1;
        }
        sections.add(new Section(sectionStart, normalized.length(), currentHeading));

        List<Chunk> result = new ArrayList<>();
        for (Section section : sections) {
            int start = section.start();
            while (start < section.end()) {
                int end = Math.min(start + options.chunkSize(), section.end());
                if (end < section.end()) {
                    // 优先完整行，但不能为了完整段落突破字符上限。
                    int newline = normalized.lastIndexOf('\n', end - 1);
                    if (newline > start + options.chunkSize() / 2) {
                        end = newline + 1;
                    }
                    if (Character.isHighSurrogate(normalized.charAt(end - 1))
                            && Character.isLowSurrogate(normalized.charAt(end))) {
                        end--;
                    }
                }
                String content = normalized.substring(start, end).trim();
                if (StringUtils.hasText(content)) {
                    String location = pageNo == null ? "第" + (result.size() + 1) + "段"
                            : "第" + pageNo + "页 · 第" + (result.size() + 1) + "段";
                    String heading = section.heading() == null ? firstLineTitle(content) : section.heading();
                    result.add(new Chunk(content, pageNo, location + (heading == null ? "" : " · " + heading)));
                }
                if (end == section.end()) {
                    break;
                }
                // start + 1 保证即使几乎全重叠仍前进；跳过低代理项会减小而不会放大重叠。
                int nextStart = Math.max(start + 1, end - options.chunkOverlap());
                if (Character.isLowSurrogate(normalized.charAt(nextStart))
                        && Character.isHighSurrogate(normalized.charAt(nextStart - 1))) {
                    nextStart++;
                }
                start = nextStart;
            }
        }
        return List.copyOf(result);
    }

    public static String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    public static String contentHash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalize(text).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String detectHeading(String line) {
        String trimmed = line.trim();
        if (trimmed.length() < 2 || trimmed.length() > 48) {
            return null;
        }
        boolean heading = trimmed.matches("^(第[一二三四五六七八九十0-9]+[章节部分篇]|[一二三四五六七八九十]+、|\\d+(\\.\\d+)+\\s*.*|\\d+\\.\\s*.+).*$")
                || trimmed.matches("^[（(]?[一二三四五六七八九十0-9]+[）)]\\s*.+$")
                || trimmed.matches("^[A-Z][A-Za-z0-9\\s,-]{2,40}$")
                || (trimmed.length() >= 4 && trimmed.length() <= 18 && !trimmed.contains("：") && !trimmed.contains(":"));
        return heading ? trimTitle(trimmed) : null;
    }

    private static String firstLineTitle(String content) {
        return content.lines().filter(StringUtils::hasText).findFirst().map(MaterialTextChunker::trimTitle).orElse(null);
    }

    private static String trimTitle(String title) {
        String normalized = title.trim().replaceAll("\\s+", " ");
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }

    public record Options(int chunkSize, int chunkOverlap) {
    }

    public record Chunk(String contentText, Integer pageNo, String sectionTitle) {
    }

    private record Section(int start, int end, String heading) {
    }
}
