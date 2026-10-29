package com.aiassistant.learning.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgePointLabelsTest {
    @Test
    void splitsAllSupportedSeparatorsAndCountsEachLabelOnlyOnce() {
        assertEquals(List.of("算法", "算法复杂度", "HashMap", "事务", "索引", "C++", "C#", "锁"),
                KnowledgePointLabels.split(" 算法, 算法复杂度，HashMap、事务;索引；C++|C#/锁\r\n算法, ,"));
    }

    @Test
    void preservesCompleteLabelIdentityInsteadOfTokenizingOrMatchingSubstrings() {
        List<String> labels = KnowledgePointLabels.split("算法复杂度, HashMap,hashmap, C++");
        assertFalse(labels.contains("算法"));
        assertFalse(labels.contains("Hash"));
        assertEquals(List.of("算法复杂度", "HashMap", "hashmap", "C++"), labels);
    }

    @Test
    void leavesUnlabelledFallbackToTheCaller() {
        assertTrue(KnowledgePointLabels.split(null).isEmpty());
        assertTrue(KnowledgePointLabels.split(" \t ").isEmpty());
        assertTrue(KnowledgePointLabels.split(" ,，、;；|/\r\n ").isEmpty());
    }

    @Test
    void retainsLegacyLongLabelFallback() {
        String longLabel = "知".repeat(81);
        assertEquals(List.of(longLabel), KnowledgePointLabels.split(" " + longLabel + " "));
        assertEquals(List.of("算法"), KnowledgePointLabels.split(longLabel + "，算法"));
    }
}
