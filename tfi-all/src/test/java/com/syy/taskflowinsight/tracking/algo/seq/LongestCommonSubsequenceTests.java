package com.syy.taskflowinsight.tracking.algo.seq;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LCS（最长公共子序列）算法单元测试（M3补充）
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M3
 * @since 2025-10-05
 */
class LongestCommonSubsequenceTests {

    @Test
    void lcsLength_identicalSequences_shouldReturnFullLength() {
        LongestCommonSubsequence.Options opts = LongestCommonSubsequence.Options.defaults();
        int length = LongestCommonSubsequence.lcsLength("hello", "hello", opts);
        assertEquals(5, length, "Identical sequences should have LCS = full length");
    }

    @Test
    void lcsLength_emptySequences_shouldReturnZero() {
        LongestCommonSubsequence.Options opts = LongestCommonSubsequence.Options.defaults();

        assertEquals(0, LongestCommonSubsequence.lcsLength("", "hello", opts));
        assertEquals(0, LongestCommonSubsequence.lcsLength("hello", "", opts));
        assertEquals(0, LongestCommonSubsequence.lcsLength("", "", opts));
    }

    @Test
    void lcsLength_noCommonSubsequence_shouldReturnZero() {
        LongestCommonSubsequence.Options opts = LongestCommonSubsequence.Options.defaults();

        int length = LongestCommonSubsequence.lcsLength("abc", "def", opts);
        assertEquals(0, length, "No common subsequence should return 0");
    }

    @Test
    void lcsLength_partialMatch_shouldReturnCorrectLength() {
        LongestCommonSubsequence.Options opts = LongestCommonSubsequence.Options.defaults();

        // "ABCDGH" and "AEDFHR" -> LCS = "ADH" (length 3)
        assertEquals(3, LongestCommonSubsequence.lcsLength("ABCDGH", "AEDFHR", opts));

        // "AGGTAB" and "GXTXAYB" -> LCS = "GTAB" (length 4)
        assertEquals(4, LongestCommonSubsequence.lcsLength("AGGTAB", "GXTXAYB", opts));
    }

    @Test
    void lcsLength_caseSensitive_shouldRespectCase() {
        LongestCommonSubsequence.Options opts = LongestCommonSubsequence.Options.defaults();

        // "Hello" and "hello" -> LCS = "ello" (length 4)
        int length = LongestCommonSubsequence.lcsLength("Hello", "hello", opts);
        assertEquals(4, length, "Should be case-sensitive");
    }

    @Test
    void lcsLength_exceedsThreshold_shouldReturnNegativeOne() {
        LongestCommonSubsequence.Options opts = new LongestCommonSubsequence.Options(5);

        String small = "abc";
        String large = "abcdefghijklmnop"; // length 16, exceeds maxSize=5

        int length = LongestCommonSubsequence.lcsLength(small, large, opts);
        assertEquals(-1, length, "Should return -1 when exceeding threshold");
    }

    @Test
    void lcsLength_exactlyAtThreshold_shouldCompute() {
        LongestCommonSubsequence.Options opts = new LongestCommonSubsequence.Options(10);

        String a = "1234567890"; // exactly 10
        String b = "1357902468"; // exactly 10, different order

        int length = LongestCommonSubsequence.lcsLength(a, b, opts);
        assertTrue(length >= 0, "Should compute when at threshold");
        assertTrue(length <= 10, "LCS cannot exceed sequence length");
    }

    @Test
    void lcsLength_oneCharAboveThreshold_shouldDegrade() {
        LongestCommonSubsequence.Options opts = new LongestCommonSubsequence.Options(10);

        String a = "12345678901"; // 11 chars, exceeds threshold
        String b = "12345678902";

        int length = LongestCommonSubsequence.lcsLength(a, b, opts);
        assertEquals(-1, length, "Should degrade when one char above threshold");
    }

    @Test
    void lcsLength_defaultThreshold_shouldAllow300() {
        LongestCommonSubsequence.Options opts = LongestCommonSubsequence.Options.defaults();

        // Default maxSize is 300
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb1.append('a');
            sb2.append('a');
        }

        int length = LongestCommonSubsequence.lcsLength(sb1, sb2, opts);
        assertEquals(300, length, "Should handle default threshold of 300");
    }

    @Test
    void lcsLength_exceedsDefault300_shouldDegrade() {
        LongestCommonSubsequence.Options opts = LongestCommonSubsequence.Options.defaults();

        // Create 301-char strings (exceeds default maxSize=300)
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 301; i++) {
            sb1.append('a');
            sb2.append('b');
        }

        int length = LongestCommonSubsequence.lcsLength(sb1, sb2, opts);
        assertEquals(-1, length, "Should degrade when exceeding default 300");
    }

    @Test
    void lcsLength_unicodeCharacters_shouldHandleCorrectly() {
        LongestCommonSubsequence.Options opts = LongestCommonSubsequence.Options.defaults();

        // Chinese characters
        String a = "你好世界";
        String b = "你好中国";
        int length = LongestCommonSubsequence.lcsLength(a, b, opts);
        assertEquals(2, length, "Should handle Unicode: LCS = '你好'");

        // Emoji
        String c = "hello😀world";
        String d = "hello😁world";
        int length2 = LongestCommonSubsequence.lcsLength(c, d, opts);
        assertTrue(length2 >= 10, "Should handle emoji characters");
    }

    @Test
    void lcsLength_commutative_shouldBeCommutative() {
        LongestCommonSubsequence.Options opts = LongestCommonSubsequence.Options.defaults();

        String a = "ABCDGH";
        String b = "AEDFHR";

        int lengthAB = LongestCommonSubsequence.lcsLength(a, b, opts);
        int lengthBA = LongestCommonSubsequence.lcsLength(b, a, opts);

        assertEquals(lengthAB, lengthBA, "LCS length should be commutative");
    }

    @Test
    void lcsTable_identicalLists_shouldReturnFullMatchTable() {
        List<Integer> list1 = Arrays.asList(1, 2, 3);
        List<Integer> list2 = Arrays.asList(1, 2, 3);

        int[][] table = LongestCommonSubsequence.lcsTable(list1, list2);

        // Table should be (m+1) x (n+1) = 4x4
        assertEquals(4, table.length);
        assertEquals(4, table[0].length);

        // Final cell should contain full length
        assertEquals(3, table[3][3], "Identical lists should have LCS = list size");
    }

    @Test
    void lcsTable_emptyLists_shouldReturnZeroTable() {
        List<Integer> list1 = Arrays.asList();
        List<Integer> list2 = Arrays.asList(1, 2, 3);

        int[][] table = LongestCommonSubsequence.lcsTable(list1, list2);

        // Table should be (0+1) x (3+1) = 1x4
        assertEquals(1, table.length);
        assertEquals(4, table[0].length);

        // All values should be 0
        for (int j = 0; j < table[0].length; j++) {
            assertEquals(0, table[0][j]);
        }
    }

    @Test
    void lcsTable_partialMatch_shouldBuildCorrectTable() {
        List<String> list1 = Arrays.asList("a", "b", "c", "d");
        List<String> list2 = Arrays.asList("a", "c", "e", "d");

        int[][] table = LongestCommonSubsequence.lcsTable(list1, list2);

        // Table should be (4+1) x (4+1) = 5x5
        assertEquals(5, table.length);
        assertEquals(5, table[0].length);

        // LCS = "a", "c", "d" -> length 3
        assertEquals(3, table[4][4], "LCS length should be 3");
    }

    @Test
    void lcsTable_noMatch_shouldReturnZeroTable() {
        List<Integer> list1 = Arrays.asList(1, 2, 3);
        List<Integer> list2 = Arrays.asList(4, 5, 6);

        int[][] table = LongestCommonSubsequence.lcsTable(list1, list2);

        // Final cell should be 0 (no common elements)
        assertEquals(0, table[3][3], "No common elements should have LCS = 0");
    }

    @Test
    void lcsTable_nullElements_shouldHandleGracefully() {
        List<String> list1 = Arrays.asList("a", null, "c");
        List<String> list2 = Arrays.asList("a", null, "d");

        int[][] table = LongestCommonSubsequence.lcsTable(list1, list2);

        // Should handle null comparison correctly (Objects.equals)
        // Table size is (m+1) x (n+1) = 4x4 (not 3x3)
        assertEquals(4, table.length);
        assertEquals(4, table[0].length);

        // LCS should include "a" and null
        assertEquals(2, table[3][3], "Should match 'a' and null");
    }

    @Test
    void lcsTable_largeList_shouldNotThrow() {
        // Create 100-element lists (well within default 300 threshold)
        List<Integer> list1 = new java.util.ArrayList<>();
        List<Integer> list2 = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            list1.add(i);
            list2.add(i);
        }

        int[][] table = LongestCommonSubsequence.lcsTable(list1, list2);

        assertEquals(101, table.length);
        assertEquals(101, table[0].length);
        assertEquals(100, table[100][100], "Full match should have LCS = 100");
    }

    @Test
    void options_defaults_shouldReturn300() {
        LongestCommonSubsequence.Options opts = LongestCommonSubsequence.Options.defaults();
        assertEquals(300, opts.maxSize(), "Default maxSize should be 300");
    }

    @Test
    void options_customThreshold_shouldBeRespected() {
        LongestCommonSubsequence.Options opts = new LongestCommonSubsequence.Options(100);
        assertEquals(100, opts.maxSize(), "Custom maxSize should be respected");
    }
}
