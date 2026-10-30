package com.aiassistant.learning.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.util.MaterialTextChunker.Chunk;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MaterialTextChunkerTest {

    @Test
    void longSingleLineRespectsWindowAndExactOverlapWithoutLosingText() {
        String text = "0123456789abcdefghij".repeat(90);
        List<Chunk> chunks = MaterialTextChunker.split(text, null, MaterialTextChunker.options(200, 37));

        StringBuilder rebuilt = new StringBuilder(chunks.get(0).contentText());
        for (int index = 0; index < chunks.size(); index++) {
            String current = chunks.get(index).contentText();
            assertTrue(current.length() <= 200);
            if (index > 0) {
                String previous = chunks.get(index - 1).contentText();
                assertEquals(previous.substring(previous.length() - 37), current.substring(0, 37));
                rebuilt.append(current.substring(37));
            }
        }
        assertEquals(text, rebuilt.toString());
    }

    @Test
    void zeroOverlapDoesNotRepeatAnySourceCharacters() {
        String text = "正文内容".repeat(501);
        List<Chunk> chunks = MaterialTextChunker.split(text, 3, MaterialTextChunker.options(400, 0));

        assertEquals(text, String.join("", chunks.stream().map(Chunk::contentText).toList()));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.pageNo() == 3));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.contentText().length() <= 400));
    }

    @Test
    void headingsAndPagesArePreservedAndOverlapDoesNotCrossChapters() {
        String text = "第一章 细胞的结构\n" + "细胞膜和细胞壁。".repeat(60)
                + "\n第二章 细胞的代谢\n" + "酶和光合作用。".repeat(60);
        List<Chunk> chunks = MaterialTextChunker.split(text, 2, MaterialTextChunker.options(200, 40));

        assertTrue(chunks.get(0).sectionTitle().endsWith("第一章 细胞的结构"));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.pageNo() == 2));
        List<Chunk> secondChapter = chunks.stream().filter(chunk -> chunk.sectionTitle().endsWith("第二章 细胞的代谢")).toList();
        assertFalse(secondChapter.isEmpty());
        assertTrue(secondChapter.get(0).contentText().startsWith("第二章 细胞的代谢"));
        assertFalse(secondChapter.stream().anyMatch(chunk -> chunk.contentText().contains("细胞膜")));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.contentText().length() <= 200));
    }

    @Test
    void supplementaryCharactersAreNotSplitIntoInvalidSurrogateHalves() {
        String text = "𠀀".repeat(260);
        List<Chunk> chunks = MaterialTextChunker.split(text, null, MaterialTextChunker.options(201, 13));
        for (Chunk chunk : chunks) {
            assertTrue(chunk.contentText().length() <= 201);
            assertFalse(Character.isLowSurrogate(chunk.contentText().charAt(0)));
            assertFalse(Character.isHighSurrogate(chunk.contentText().charAt(chunk.contentText().length() - 1)));
        }
    }

    @ParameterizedTest
    @CsvSource({"199,0", "4001,0", "900,-1", "900,501", "200,200", "200,300"})
    void invalidLimitsAreRejected(int size, int overlap) {
        assertThrows(BusinessException.class, () -> MaterialTextChunker.options(size, overlap));
    }

    @Test
    void fingerprintTracksTheSourceIndependentlyOfWindowSettings() {
        assertEquals(MaterialTextChunker.contentHash("正文\n下一行"), MaterialTextChunker.contentHash("正文\r\n下一行"));
        assertEquals(64, MaterialTextChunker.contentHash("正文").length());
        assertEquals(new MaterialTextChunker.Options(900, 140), MaterialTextChunker.options(null, null));
    }
}
