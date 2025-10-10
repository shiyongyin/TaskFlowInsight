package com.syy.taskflowinsight.tracking.algo.edit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Levenshtein编辑距离算法单元测试（M3补充）
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M3
 * @since 2025-10-05
 */
class LevenshteinEditDistanceTests {

    @Test
    void distance_identicalStrings_shouldReturnZero() {
        LevenshteinEditDistance.Options opts = LevenshteinEditDistance.Options.defaults();
        int distance = LevenshteinEditDistance.distance("hello", "hello", opts);
        assertEquals(0, distance, "Identical strings should have distance 0");
    }

    @Test
    void distance_emptyStrings_shouldReturnCorrectDistance() {
        LevenshteinEditDistance.Options opts = LevenshteinEditDistance.Options.defaults();

        // Empty to non-empty
        assertEquals(5, LevenshteinEditDistance.distance("", "hello", opts));
        // Non-empty to empty
        assertEquals(5, LevenshteinEditDistance.distance("hello", "", opts));
        // Both empty
        assertEquals(0, LevenshteinEditDistance.distance("", "", opts));
    }

    @Test
    void distance_nullStrings_shouldReturnMaxValue() {
        LevenshteinEditDistance.Options opts = LevenshteinEditDistance.Options.defaults();

        assertEquals(Integer.MAX_VALUE, LevenshteinEditDistance.distance(null, "hello", opts));
        assertEquals(Integer.MAX_VALUE, LevenshteinEditDistance.distance("hello", null, opts));
        assertEquals(Integer.MAX_VALUE, LevenshteinEditDistance.distance(null, null, opts));
    }

    @Test
    void distance_singleCharacterDifference_shouldReturnOne() {
        LevenshteinEditDistance.Options opts = LevenshteinEditDistance.Options.defaults();

        // Substitution
        assertEquals(1, LevenshteinEditDistance.distance("cat", "bat", opts));
        // Insertion
        assertEquals(1, LevenshteinEditDistance.distance("cat", "cats", opts));
        // Deletion
        assertEquals(1, LevenshteinEditDistance.distance("cats", "cat", opts));
    }

    @Test
    void distance_multipleOperations_shouldReturnCorrectDistance() {
        LevenshteinEditDistance.Options opts = LevenshteinEditDistance.Options.defaults();

        // kitten -> sitting: 3 operations (substitute k→s, substitute e→i, insert g)
        assertEquals(3, LevenshteinEditDistance.distance("kitten", "sitting", opts));

        // Saturday -> Sunday: 3 operations
        assertEquals(3, LevenshteinEditDistance.distance("Saturday", "Sunday", opts));

        // rosettacode -> raisethysword: 8 operations
        assertEquals(8, LevenshteinEditDistance.distance("rosettacode", "raisethysword", opts));
    }

    @Test
    void distance_caseInsensitive_shouldBeCaseSensitive() {
        LevenshteinEditDistance.Options opts = LevenshteinEditDistance.Options.defaults();

        // Algorithm is case-sensitive by design
        int distance = LevenshteinEditDistance.distance("Hello", "hello", opts);
        assertEquals(1, distance, "Should count case difference as 1 edit");
    }

    @Test
    void distance_exceedsThreshold_shouldReturnMaxValue() {
        // Create options with small threshold
        LevenshteinEditDistance.Options opts = new LevenshteinEditDistance.Options(5);

        String small = "abc";
        String large = "abcdefghijklmnop"; // length 16, exceeds maxSize=5

        int distance = LevenshteinEditDistance.distance(small, large, opts);
        assertEquals(Integer.MAX_VALUE, distance, "Should return MAX_VALUE when exceeding threshold");
    }

    @Test
    void distance_exactlyAtThreshold_shouldCompute() {
        // Create options with threshold=10
        LevenshteinEditDistance.Options opts = new LevenshteinEditDistance.Options(10);

        String a = "1234567890"; // exactly 10
        String b = "1234567899"; // exactly 10, 1 difference

        int distance = LevenshteinEditDistance.distance(a, b, opts);
        assertEquals(1, distance, "Should compute when at threshold");
    }

    @Test
    void distance_oneCharAboveThreshold_shouldDegrade() {
        LevenshteinEditDistance.Options opts = new LevenshteinEditDistance.Options(10);

        String a = "12345678901"; // 11 chars, exceeds threshold
        String b = "12345678902";

        int distance = LevenshteinEditDistance.distance(a, b, opts);
        assertEquals(Integer.MAX_VALUE, distance, "Should degrade when one char above threshold");
    }

    @Test
    void distance_defaultThreshold_shouldAllow500() {
        LevenshteinEditDistance.Options opts = LevenshteinEditDistance.Options.defaults();

        // Default maxSize is 500
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb1.append('a');
            sb2.append('a');
        }

        int distance = LevenshteinEditDistance.distance(sb1, sb2, opts);
        assertEquals(0, distance, "Should handle default threshold of 500");
    }

    @Test
    void distance_exceedsDefault500_shouldDegrade() {
        LevenshteinEditDistance.Options opts = LevenshteinEditDistance.Options.defaults();

        // Create 501-char strings (exceeds default maxSize=500)
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            sb1.append('a');
            sb2.append('b');
        }

        int distance = LevenshteinEditDistance.distance(sb1, sb2, opts);
        assertEquals(Integer.MAX_VALUE, distance, "Should degrade when exceeding default 500");
    }

    @Test
    void distance_unicodeCharacters_shouldHandleCorrectly() {
        LevenshteinEditDistance.Options opts = LevenshteinEditDistance.Options.defaults();

        // Chinese characters
        String a = "你好世界";
        String b = "你好世";
        assertEquals(1, LevenshteinEditDistance.distance(a, b, opts));

        // Emoji
        String c = "hello😀";
        String d = "hello😁";
        assertEquals(1, LevenshteinEditDistance.distance(c, d, opts));
    }

    @Test
    void distance_symmetry_shouldBeSymmetric() {
        LevenshteinEditDistance.Options opts = LevenshteinEditDistance.Options.defaults();

        String a = "kitten";
        String b = "sitting";

        int distAB = LevenshteinEditDistance.distance(a, b, opts);
        int distBA = LevenshteinEditDistance.distance(b, a, opts);

        assertEquals(distAB, distBA, "Distance should be symmetric");
    }

    @Test
    void distance_triangleInequality_shouldHold() {
        LevenshteinEditDistance.Options opts = LevenshteinEditDistance.Options.defaults();

        String a = "abc";
        String b = "adc";
        String c = "aec";

        int distAB = LevenshteinEditDistance.distance(a, b, opts);
        int distBC = LevenshteinEditDistance.distance(b, c, opts);
        int distAC = LevenshteinEditDistance.distance(a, c, opts);

        assertTrue(distAC <= distAB + distBC, "Triangle inequality should hold");
    }

    @Test
    void options_defaults_shouldReturn500() {
        LevenshteinEditDistance.Options opts = LevenshteinEditDistance.Options.defaults();
        assertEquals(500, opts.maxSize(), "Default maxSize should be 500");
    }

    @Test
    void options_customThreshold_shouldBeRespected() {
        LevenshteinEditDistance.Options opts = new LevenshteinEditDistance.Options(100);
        assertEquals(100, opts.maxSize(), "Custom maxSize should be respected");
    }
}
