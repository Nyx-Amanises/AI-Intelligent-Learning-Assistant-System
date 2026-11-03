package com.aiassistant.learning.common.util;

import com.aiassistant.learning.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/** 只提取原始文件文本和页码；分块、持久化及解析状态由调用方负责。 */
public final class MaterialFileParser {
    private static final Charset GB18030 = Charset.forName("GB18030");

    private MaterialFileParser() {
    }

    public static ParsedFile parse(Path path, String materialType) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new BusinessException(404, "原始文件不存在");
        }
        String type = materialType == null ? "" : materialType.trim().toUpperCase(Locale.ROOT);
        try {
            return switch (type) {
                case "TXT", "TEXT" -> parseText(path);
                case "PDF" -> parsePdf(path);
                case "DOCX" -> parseDocx(path);
                default -> throw new BusinessException("暂不支持解析该文件类型");
            };
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(type + " 文件无法解析，请确认文件格式正确、未损坏且未加密");
        }
    }

    private static ParsedFile parseText(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (startsWith(bytes, 0xff, 0xfe) || startsWith(bytes, 0xfe, 0xff)
                || startsWith(bytes, 0x00, 0x00, 0xfe, 0xff)) {
            throw new BusinessException("暂不支持 UTF-16/UTF-32 文本，请另存为 UTF-8 后重新上传");
        }
        if (startsWith(bytes, 0x50, 0x4b, 0x03, 0x04) || startsWith(bytes, 0x25, 0x50, 0x44, 0x46)
                || startsWith(bytes, 0x89, 0x50, 0x4e, 0x47) || startsWith(bytes, 0xff, 0xd8, 0xff)
                || startsWith(bytes, 0x47, 0x49, 0x46, 0x38)) {
            throw new BusinessException("TXT 文件包含二进制文件内容，请使用正确的文件格式上传");
        }
        boolean utf8Bom = startsWith(bytes, 0xef, 0xbb, 0xbf);
        String text;
        try {
            text = decode(bytes, utf8Bom ? 3 : 0, StandardCharsets.UTF_8);
        } catch (CharacterCodingException invalidUtf8) {
            if (utf8Bom) {
                throw invalidTextEncoding();
            }
            try {
                // GB18030 兼容 GBK，包括 GBK 无法表示的四字节字符；两次解码都不允许替换坏字节。
                text = decode(bytes, 0, GB18030);
            } catch (CharacterCodingException invalidLegacyEncoding) {
                throw invalidTextEncoding();
            }
        }
        if (text.codePoints().anyMatch(value -> value == 0xfffd
                || (Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t' && value != '\f'))) {
            throw new BusinessException("TXT 文件包含二进制控制字符或无效文字，请另存为 UTF-8 后重新上传");
        }
        return unpaged(text, "TXT 文件没有可解析的文字内容");
    }

    private static String decode(byte[] bytes, int offset, Charset charset) throws CharacterCodingException {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
    }

    private static BusinessException invalidTextEncoding() {
        return new BusinessException("TXT 文件编码无法识别或内容已损坏，请另存为 UTF-8（或 GB18030/GBK）后重新上传");
    }

    private static ParsedFile parsePdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<ParsedPage> pages = new ArrayList<>();
            List<String> textPages = new ArrayList<>();
            for (int pageNo = 1; pageNo <= document.getNumberOfPages(); pageNo++) {
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                String pageText = MaterialTextChunker.normalize(stripper.getText(document));
                // 空白、格式控制字符不能作为分块内容，仍保留这页的物理页码。
                if (!hasVisibleText(pageText)) pageText = "";
                pages.add(new ParsedPage(pageNo, pageText));
                if (!pageText.isEmpty()) textPages.add(pageText);
            }
            if (textPages.isEmpty()) {
                throw new BusinessException("PDF 未提取到文字层，可能是扫描件或图片 PDF。请先进行 OCR 转换为可搜索 PDF 或文本后重新上传");
            }
            return new ParsedFile(String.join("\n", textPages), document.getNumberOfPages(), pages);
        }
    }

    private static ParsedFile parseDocx(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path); XWPFDocument document = new XWPFDocument(input)) {
            List<String> paragraphs = document.getParagraphs().stream()
                    .map(paragraph -> MaterialTextChunker.normalize(paragraph.getText()))
                    .filter(MaterialFileParser::hasVisibleText).toList();
            return unpaged(String.join("\n", paragraphs), "Word 文件没有可解析的段落文字，请确认文档包含正文，图片内容需先进行 OCR 转换");
        }
    }

    private static ParsedFile unpaged(String text, String emptyMessage) {
        String normalized = MaterialTextChunker.normalize(text);
        if (!hasVisibleText(normalized)) throw new BusinessException(emptyMessage);
        return new ParsedFile(normalized, null, List.of(new ParsedPage(null, normalized)));
    }

    private static boolean hasVisibleText(String value) {
        return value != null && value.codePoints().anyMatch(character -> !Character.isWhitespace(character)
                && !Character.isSpaceChar(character) && Character.getType(character) != Character.FORMAT
                && !Character.isISOControl(character));
    }

    private static boolean startsWith(byte[] value, int... prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (Byte.toUnsignedInt(value[index]) != prefix[index]) return false;
        }
        return true;
    }

    public record ParsedFile(String text, Integer totalPages, List<ParsedPage> pages) {
        public ParsedFile {
            pages = List.copyOf(pages);
        }
    }

    public record ParsedPage(Integer pageNo, String text) {
    }
}
