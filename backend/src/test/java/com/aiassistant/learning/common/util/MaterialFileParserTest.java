package com.aiassistant.learning.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiassistant.learning.common.exception.BusinessException;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MaterialFileParserTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "GBK", "GB18030"})
    void decodesChineseTextWithStrictUtf8AndLegacyFallback(String encoding) throws Exception {
        String text = "第一章 中文学习资料\r\n编码保留：知识点、练习题。";
        Path path = Files.write(directory.resolve("learning.txt"), text.getBytes(Charset.forName(encoding)));

        var parsed = MaterialFileParser.parse(path, "TXT");

        assertEquals(text.replace("\r\n", "\n"), parsed.text());
        assertNull(parsed.totalPages());
        assertEquals(List.of(new MaterialFileParser.ParsedPage(null, parsed.text())), parsed.pages());
        assertFalse(parsed.text().contains("�"));
    }

    @Test
    void gb18030FourByteCharactersAreNotReplacedOrRejectedAsGbk() throws Exception {
        String text = "扩展汉字：\uD840\uDC00，继续学习";
        assertFalse(Charset.forName("GBK").newEncoder().canEncode(text));
        Path path = Files.write(directory.resolve("four-byte.txt"), text.getBytes(Charset.forName("GB18030")));

        assertEquals(text, MaterialFileParser.parse(path, "TXT").text());
    }

    @Test
    void utf8BomIsRemovedBeforeReturningTextForChunkingAndHashing() throws Exception {
        byte[] text = "第一章\n中文内容".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[text.length + 3];
        bytes[0] = (byte) 0xef; bytes[1] = (byte) 0xbb; bytes[2] = (byte) 0xbf;
        System.arraycopy(text, 0, bytes, 3, text.length);
        Path path = Files.write(directory.resolve("bom.txt"), bytes);

        var parsed = MaterialFileParser.parse(path, "TEXT");

        assertEquals("第一章\n中文内容", parsed.text());
        assertEquals(MaterialTextChunker.contentHash("第一章\n中文内容"), MaterialTextChunker.contentHash(parsed.text()));
    }

    @Test
    void malformedUtf8BomFileCannotSilentlyFallBackToAnotherEncoding() throws Exception {
        Path path = Files.write(directory.resolve("broken-bom.txt"),
                new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf, (byte) 0xd6, (byte) 0xd0});

        BusinessException error = assertThrows(BusinessException.class, () -> MaterialFileParser.parse(path, "TXT"));

        assertTrue(error.getMessage().contains("编码"));
    }

    @Test
    void bytesInvalidInBothEncodingsAreRejectedInsteadOfCreatingReplacementText() throws Exception {
        Path path = Files.write(directory.resolve("broken.txt"), new byte[]{(byte) 0x81});

        BusinessException error = assertThrows(BusinessException.class, () -> MaterialFileParser.parse(path, "TXT"));

        assertTrue(error.getMessage().contains("编码"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \r\n\t", "\uFEFF", "\u200B\u00A0"})
    void emptyOrInvisibleOnlyTextNeverSucceeds(String text) throws Exception {
        Path path = Files.writeString(directory.resolve("empty.txt"), text, StandardCharsets.UTF_8);

        assertThrows(BusinessException.class, () -> MaterialFileParser.parse(path, "TXT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-16LE", "UTF-16BE"})
    void unsupportedUtf16HasAnActionableEncodingError(String encoding) throws Exception {
        byte[] content = "中文文档".getBytes(Charset.forName(encoding));
        byte[] bytes = new byte[content.length + 2];
        bytes[0] = (byte) ("UTF-16LE".equals(encoding) ? 0xff : 0xfe);
        bytes[1] = (byte) ("UTF-16LE".equals(encoding) ? 0xfe : 0xff);
        System.arraycopy(content, 0, bytes, 2, content.length);
        Path path = Files.write(directory.resolve("utf16.txt"), bytes);

        BusinessException error = assertThrows(BusinessException.class, () -> MaterialFileParser.parse(path, "TXT"));

        assertTrue(error.getMessage().contains("UTF-16"));
        assertTrue(error.getMessage().contains("另存为 UTF-8"));
    }

    @Test
    void binaryContentCannotMasqueradeAsUtf8Text() throws Exception {
        Path path = Files.write(directory.resolve("binary.txt"), new byte[]{'A', 0, 'B', 1, 2, 'C'});

        BusinessException error = assertThrows(BusinessException.class, () -> MaterialFileParser.parse(path, "TXT"));

        assertTrue(error.getMessage().contains("二进制"));
    }

    @Test
    void ordinaryTextStartingWithExecutableMagicLettersIsStillText() throws Exception {
        String text = "MZ 是可执行文件的标记，但这一行只是学习资料。";
        Path path = Files.writeString(directory.resolve("magic-letters.txt"), text, StandardCharsets.UTF_8);

        assertEquals(text, MaterialFileParser.parse(path, "TXT").text());
    }

    @Test
    void pdfRenamedAsTxtIsRejectedInsteadOfParsingItsAsciiHeader() throws Exception {
        Path path = directory.resolve("renamed.txt");
        try (PDDocument document = new PDDocument()) {
            addTextPage(document, "A PDF document");
            document.save(path.toFile());
        }

        BusinessException error = assertThrows(BusinessException.class, () -> MaterialFileParser.parse(path, "TXT"));

        assertTrue(error.getMessage().contains("二进制"));
    }

    @Test
    void imageOnlyPdfExplainsTheMissingTextLayerAndOcrConversion() throws Exception {
        Path path = directory.resolve("scan.pdf");
        try (PDDocument document = new PDDocument()) {
            addImagePage(document);
            document.save(path.toFile());
        }

        BusinessException error = assertThrows(BusinessException.class, () -> MaterialFileParser.parse(path, "PDF"));

        assertTrue(error.getMessage().contains("文字层"));
        assertTrue(error.getMessage().contains("OCR"));
    }

    @Test
    void textPdfKeepsPhysicalPageNumbersIncludingAnEmptyImagePage() throws Exception {
        Path path = directory.resolve("mixed.pdf");
        try (PDDocument document = new PDDocument()) {
            addTextPage(document, "First page text.");
            addImagePage(document);
            addTextPage(document, "Third page text.");
            document.save(path.toFile());
        }

        var parsed = MaterialFileParser.parse(path, "PDF");

        assertEquals(3, parsed.totalPages());
        assertEquals("First page text.\nThird page text.", parsed.text());
        assertEquals(List.of(1, 2, 3), parsed.pages().stream().map(MaterialFileParser.ParsedPage::pageNo).toList());
        assertEquals("", parsed.pages().get(1).text());
        assertEquals("Third page text.", parsed.pages().get(2).text());
    }

    @Test
    void blankPdfNeverProducesASuccessWithZeroSegments() throws Exception {
        Path path = directory.resolve("blank.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(path.toFile());
        }

        assertThrows(BusinessException.class, () -> MaterialFileParser.parse(path, "PDF"));
    }

    @Test
    void whitespaceOnlyPdfPageIsReturnedAsEmptyAndKeepsItsPageNumber() throws Exception {
        Path path = directory.resolve("whitespace.pdf");
        try (PDDocument document = new PDDocument()) {
            addTextPage(document, "Visible text.");
            addTextPage(document, "   ");
            document.save(path.toFile());
        }

        var parsed = MaterialFileParser.parse(path, "PDF");

        assertEquals(2, parsed.totalPages());
        assertEquals("Visible text.", parsed.text());
        assertEquals(new MaterialFileParser.ParsedPage(2, ""), parsed.pages().get(1));
    }

    @Test
    void docxExtractsParagraphTextWithoutInventingPageNumbers() throws Exception {
        Path path = directory.resolve("paragraphs.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(path)) {
            document.createParagraph().createRun().setText("第一段中文内容");
            document.createParagraph();
            document.createParagraph().createRun().setText("第二段练习题");
            document.write(output);
        }

        var parsed = MaterialFileParser.parse(path, "DOCX");

        assertEquals("第一段中文内容\n第二段练习题", parsed.text());
        assertNull(parsed.totalPages());
        assertEquals(List.of(new MaterialFileParser.ParsedPage(null, parsed.text())), parsed.pages());
    }

    @Test
    void emptyWordDocumentIsRejectedWithAClearMessage() throws Exception {
        Path path = directory.resolve("empty.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(path)) {
            document.createParagraph().createRun().setText(" \t");
            document.write(output);
        }

        BusinessException error = assertThrows(BusinessException.class, () -> MaterialFileParser.parse(path, "DOCX"));

        assertTrue(error.getMessage().contains("没有可解析"));
    }

    private void addTextPage(PDDocument document, String text) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(50, 700);
            content.showText(text);
            content.endText();
        }
    }

    private void addImagePage(PDDocument document) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        BufferedImage scan = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[400];
        Arrays.fill(pixels, 0xffffff);
        scan.setRGB(0, 0, 20, 20, pixels, 0, 20);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.drawImage(LosslessFactory.createFromImage(document, scan), 50, 50, 100, 100);
        }
    }
}
