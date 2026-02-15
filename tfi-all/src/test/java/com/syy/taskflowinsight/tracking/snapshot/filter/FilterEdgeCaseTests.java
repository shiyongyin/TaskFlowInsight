package com.syy.taskflowinsight.tracking.snapshot.filter;

import com.syy.taskflowinsight.annotation.DiffIgnore;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 过滤边界用例测试
 *
 * 测试覆盖（12用例）：
 * - Null值输入处理
 * - 空集合处理
 * - 特殊字符路径
 * - Unicode路径名称
 * - 极长路径
 * - 重复模式
 * - 大小写敏感性
 * - 空字符串模式
 * - 非法正则表达式
 * - 循环引用对象
 * - 极深层级对象
 * - 线程安全（并发访问）
 *
 * @author TaskFlow Insight Team
 * @since 2025-10-09
 */
class FilterEdgeCaseTests {

    private ObjectSnapshotDeep snapshotDeep;
    private SnapshotConfig config;

    static class TestModel {
        private String normalField;
        @DiffIgnore
        private String ignoredField;
        private static final Logger log = LoggerFactory.getLogger(TestModel.class);
        private transient String transientField;
    }

    @BeforeEach
    void setUp() {
        config = new SnapshotConfig();
        config.setEnableDeep(true);
        config.setMaxDepth(5);
        snapshotDeep = new ObjectSnapshotDeep(config);
        ObjectSnapshotDeep.resetMetrics();
    }

    // ========== Edge Case 1: Null Inputs ==========

    @Test
    void testNullPathPattern() {
        TestModel model = new TestModel();
        model.normalField = "value";

        // Null patterns should be handled gracefully (no NPE)
        Set<String> includePatterns = new HashSet<>();
        includePatterns.add(null);
        config.setIncludePatterns(new ArrayList<>(includePatterns));

        assertDoesNotThrow(() -> {
            Map<String, Object> result = snapshotDeep.captureDeep(
                model, 5, Collections.emptySet(), Collections.emptySet()
            );
            // Should not throw, null patterns should be skipped
        });
    }

    @Test
    void testNullFieldInUnifiedEngine() throws NoSuchFieldException {
        Field field = TestModel.class.getDeclaredField("normalField");

        // Null parameters should return default behavior (retain)
        FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
            TestModel.class,
            field,
            "path",
            null,  // null includes
            null,  // null excludes
            null,  // null regex
            null,  // null packages
            false
        );

        assertTrue(decision.shouldInclude(), "Null patterns should default to include");
    }

    // ========== Edge Case 2: Empty Collections ==========

    @Test
    void testEmptyPatternCollections() throws NoSuchFieldException {
        Field field = TestModel.class.getDeclaredField("normalField");

        // Empty collections should behave same as null (default include)
        FilterDecision decision = UnifiedFilterEngine.shouldIgnore(
            TestModel.class,
            field,
            "path",
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptyList(),
            false
        );

        assertTrue(decision.shouldInclude(), "Empty pattern sets should default to include");
        assertEquals(FilterReason.DEFAULT_RETAIN, decision.getReason());
    }

    // ========== Edge Case 3: Special Characters in Paths ==========

    @Test
    void testSpecialCharactersInPath() {
        String pathWithSpecialChars = "order.$price.amount";
        String pattern = "order.$price.*";

        // Special chars should be escaped in Glob → Regex conversion
        boolean matches = PathMatcher.matchGlob(pathWithSpecialChars, pattern);

        assertTrue(matches, "Glob should handle special chars via escaping");
    }

    @Test
    void testBracketsInPath() {
        String path = "items[0].name";
        String pattern = "items[*].name";

        boolean matches = PathMatcher.matchGlob(path, pattern);

        assertTrue(matches, "[*] should match array indices");
    }

    // ========== Edge Case 4: Unicode Paths ==========

    @Test
    void testUnicodeFieldNames() {
        String unicodePath = "订单.用户名";
        String unicodePattern = "订单.*";

        boolean matches = PathMatcher.matchGlob(unicodePath, unicodePattern);

        assertTrue(matches, "Glob should support Unicode field names");
    }

    // ========== Edge Case 5: Extreme Path Length ==========

    @Test
    void testVeryLongPath() {
        // Generate path with 50 segments
        StringBuilder longPath = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            if (i > 0) longPath.append(".");
            longPath.append("segment").append(i);
        }

        String pattern = "segment0.**";

        boolean matches = PathMatcher.matchGlob(longPath.toString(), pattern);

        assertTrue(matches, "Glob should handle very long paths");
    }

    // ========== Edge Case 6: Duplicate Patterns ==========

    @Test
    void testDuplicatePatterns() {
        config.setExcludePatterns(List.of("*.password", "*.password", "*.password"));

        TestModel model = new TestModel();
        model.normalField = "value";

        // Duplicate patterns should not cause issues
        Map<String, Object> result = snapshotDeep.captureDeep(
            model, 5, Collections.emptySet(), Collections.emptySet()
        );

        assertNotNull(result, "Duplicate patterns should be handled");
    }

    // ========== Edge Case 7: Case Sensitivity ==========

    @Test
    void testCaseSensitiveMatching() {
        String path = "User.Password";
        String lowerPattern = "user.password";
        String exactPattern = "User.Password";

        // Glob matching is case-sensitive
        assertFalse(PathMatcher.matchGlob(path, lowerPattern),
            "Glob should be case-sensitive (lowercase pattern)");

        assertTrue(PathMatcher.matchGlob(path, exactPattern),
            "Glob should match exact case");
    }

    // ========== Edge Case 8: Empty String Pattern ==========

    @Test
    void testEmptyStringPattern() {
        String emptyPath = "";
        String emptyPattern = "";

        boolean matches = PathMatcher.matchGlob(emptyPath, emptyPattern);

        assertTrue(matches, "Empty path should match empty pattern");
    }

    @Test
    void testEmptyPathWithNonEmptyPattern() {
        String emptyPath = "";
        String pattern = "*.field";

        boolean matches = PathMatcher.matchGlob(emptyPath, pattern);

        assertFalse(matches, "Empty path should not match non-empty pattern");
    }

    // ========== Edge Case 9: Invalid Regex ==========

    @Test
    void testInvalidRegexHandling() {
        String invalidRegex = "[unclosed";

        // Invalid regex should not throw, should return false
        assertDoesNotThrow(() -> {
            boolean result = PathMatcher.matchRegex("test", invalidRegex);
            assertFalse(result, "Invalid regex should return false");
        });
    }

    // ========== Edge Case 10: Circular Reference Objects ==========

    static class CircularA {
        String name;
        CircularB ref;
    }

    static class CircularB {
        String name;
        CircularA ref;
    }

    @Test
    void testCircularReferenceFiltering() {
        CircularA a = new CircularA();
        a.name = "A";
        CircularB b = new CircularB();
        b.name = "B";
        a.ref = b;
        b.ref = a;

        config.setDefaultExclusionsEnabled(true);

        // Should handle circular references without infinite loop
        assertDoesNotThrow(() -> {
            Map<String, Object> result = snapshotDeep.captureDeep(
                a, 5, Collections.emptySet(), Collections.emptySet()
            );
            assertNotNull(result, "Circular reference should be detected");
        });
    }

    // ========== Edge Case 11: Extreme Depth ==========

    static class DeepNested {
        String value;
        DeepNested child;
    }

    @Test
    void testExtremeDepthFiltering() {
        // Create 20-level deep object
        DeepNested root = new DeepNested();
        DeepNested current = root;
        for (int i = 0; i < 19; i++) {
            current.value = "level" + i;
            current.child = new DeepNested();
            current = current.child;
        }
        current.value = "leaf";

        config.setMaxDepth(10);  // Limit depth to 10

        Map<String, Object> result = snapshotDeep.captureDeep(
            root, 10, Collections.emptySet(), Collections.emptySet()
        );

        // Should stop at depth limit, not traverse all 20 levels
        assertNotNull(result);
        assertTrue(result.size() < 20, "Should respect maxDepth limit");
    }

    // ========== Edge Case 12: Thread Safety (Concurrent Access) ==========

    @Test
    void testConcurrentPatternMatching() throws InterruptedException {
        // PathMatcher uses ConcurrentHashMap, should be thread-safe
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            int threadId = i;
            Thread t = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    String path = "thread" + threadId + ".field" + j;
                    String pattern = "thread" + threadId + ".*";
                    boolean result = PathMatcher.matchGlob(path, pattern);
                    assertTrue(result, "Concurrent matching should work");
                }
            });
            threads.add(t);
            t.start();
        }

        // Wait for all threads
        for (Thread t : threads) {
            t.join();
        }

        // No ConcurrentModificationException should occur
        assertTrue(PathMatcher.getCacheSize() > 0, "Cache should contain patterns from all threads");
    }
}
