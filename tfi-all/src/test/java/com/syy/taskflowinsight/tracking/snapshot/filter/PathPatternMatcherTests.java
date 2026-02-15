package com.syy.taskflowinsight.tracking.snapshot.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 路径模式匹配器测试
 *
 * 测试覆盖（25+用例）：
 * - 单层通配符（*）
 * - 跨层通配符（**）
 * - 数组索引通配符（[*]）
 * - 正则表达式
 * - Glob与Regex组合
 * - 非法正则容错
 * - 缓存性能验证
 *
 * @author TaskFlow Insight Team
 * @since 2025-10-09
 */
class PathPatternMatcherTests {

    @BeforeEach
    void setUp() {
        // 清空缓存，确保每个测试独立
        PathMatcher.clearCache();
    }

    @AfterEach
    void tearDown() {
        PathMatcher.clearCache();
    }

    // ========== 单层通配符（*）测试 ==========

    @Test
    void testSingleLevelWildcard_MatchesSingleSegment() {
        assertTrue(PathMatcher.matchGlob("order.name", "order.*"),
            "* should match single segment 'name'");
        assertTrue(PathMatcher.matchGlob("user.id", "*.id"),
            "* should match single segment 'user'");
    }

    @Test
    void testSingleLevelWildcard_DoesNotMatchMultipleSegments() {
        assertFalse(PathMatcher.matchGlob("order.items.name", "order.*"),
            "* should NOT match multiple segments 'items.name'");
    }

    @Test
    void testSingleLevelWildcard_MatchesEmpty() {
        // 注意：* 匹配零个或多个字符，所以可以匹配空段落
        // 这允许 "*password" 匹配 "password"（零长度前缀）
        assertTrue(PathMatcher.matchGlob("order.", "order.*"),
            "* should match empty segment (zero-length match)");
        assertTrue(PathMatcher.matchGlob("password", "*password"),
            "* should match zero-length prefix");
    }

    @Test
    void testSingleLevelWildcard_MultipleStar() {
        assertTrue(PathMatcher.matchGlob("order.items.name", "*.*.name"),
            "Multiple * should match multiple single segments");
        assertTrue(PathMatcher.matchGlob("a.b.c.d", "*.*.*.*"),
            "Multiple * should match exact segment count");
    }

    @Test
    void testSingleLevelWildcard_WithLiteralPrefix() {
        assertTrue(PathMatcher.matchGlob("order.totalAmount", "order.total*"),
            "* can match partial segment with prefix");
        assertTrue(PathMatcher.matchGlob("order.amountTotal", "order.*Total"),
            "* can match partial segment with suffix");
    }

    // ========== 跨层通配符（**）测试 ==========

    @Test
    void testMultiLevelWildcard_MatchesMultipleLevels() {
        assertTrue(PathMatcher.matchGlob("order.items.name", "order.**"),
            "** should match multiple levels 'items.name'");
        assertTrue(PathMatcher.matchGlob("order.items.details.price", "order.**"),
            "** should match any depth under 'order'");
    }

    @Test
    void testMultiLevelWildcard_MatchesZeroLevels() {
        assertTrue(PathMatcher.matchGlob("order", "order.**"),
            "** should match zero additional levels (just 'order')");
    }

    @Test
    void testMultiLevelWildcard_MiddlePosition() {
        assertTrue(PathMatcher.matchGlob("order.items.details.name", "order.**.name"),
            "** in middle should match 'items.details'");
        assertTrue(PathMatcher.matchGlob("order.name", "order.**.name"),
            "** in middle should match zero segments");
    }

    @Test
    void testMultiLevelWildcard_MultipleStarStar() {
        assertTrue(PathMatcher.matchGlob("a.b.c.d.e", "a.**.**.e"),
            "Multiple ** should match multiple segments");
    }

    @Test
    void testMultiLevelWildcard_WithSingleStar() {
        assertTrue(PathMatcher.matchGlob("order.items.name", "order.**.name"),
            "** combined with literal should work");
        assertTrue(PathMatcher.matchGlob("order.items.details.name", "order.*.*.name"),
            "* combined properly should work");
    }

    // ========== 数组索引通配符（[*]）测试 ==========

    @Test
    void testArrayWildcard_MatchesAnyIndex() {
        assertTrue(PathMatcher.matchGlob("items[0].id", "items[*].id"),
            "[*] should match index 0");
        assertTrue(PathMatcher.matchGlob("items[123].id", "items[*].id"),
            "[*] should match index 123");
    }

    @Test
    void testArrayWildcard_DoesNotMatchNonNumeric() {
        assertFalse(PathMatcher.matchGlob("items[abc].id", "items[*].id"),
            "[*] should NOT match non-numeric index");
        assertFalse(PathMatcher.matchGlob("items[].id", "items[*].id"),
            "[*] should NOT match empty brackets");
    }

    @Test
    void testArrayWildcard_MultipleArrays() {
        assertTrue(PathMatcher.matchGlob("matrix[0][1].value", "matrix[*][*].value"),
            "Multiple [*] should match nested arrays");
    }

    @Test
    void testArrayWildcard_CombinedWithOtherWildcards() {
        assertTrue(PathMatcher.matchGlob("order.items[0].name", "order.items[*].*"),
            "[*] combined with * should work");
        assertTrue(PathMatcher.matchGlob("order.items[5].details.price", "order.**.items[*].**.price"),
            "[*] combined with ** should work");
    }

    @Test
    void testArrayWildcard_ExactSyntax() {
        // [*] should match exactly "[digit+]"
        assertTrue(PathMatcher.matchGlob("arr[99]", "arr[*]"),
            "[*] should match complete array access");
        assertFalse(PathMatcher.matchGlob("arr[*]", "arr[*]"),
            "[*] should NOT match literal [*]");
    }

    // ========== 正则表达式测试 ==========

    @Test
    void testRegex_BasicPattern() {
        assertTrue(PathMatcher.matchRegex("debug_001", "^debug_\\d{3}$"),
            "Regex should match digit pattern");
        assertTrue(PathMatcher.matchRegex("test_abc", "^test_[a-z]+$"),
            "Regex should match character class");
    }

    @Test
    void testRegex_ComplexPattern() {
        assertTrue(PathMatcher.matchRegex("user.email", "^user\\.(email|phone)$"),
            "Regex should support alternation");
        assertTrue(PathMatcher.matchRegex("item[0].price", "^item\\[\\d+\\]\\.price$"),
            "Regex should match escaped brackets");
    }

    @Test
    void testRegex_CaseSensitive() {
        assertTrue(PathMatcher.matchRegex("OrderId", "^Order[A-Z][a-z]$"),
            "Regex should be case-sensitive by default");
        assertFalse(PathMatcher.matchRegex("orderid", "^Order[A-Z][a-z]$"),
            "Regex should NOT match different case");
    }

    @Test
    void testRegex_SpecialCharacters() {
        assertTrue(PathMatcher.matchRegex("price$amount", "^price\\$amount$"),
            "Regex should handle escaped special chars");
    }

    @Test
    void testRegex_EmptyAndNull() {
        assertFalse(PathMatcher.matchRegex("", "^.+$"),
            "Empty path should not match non-empty pattern");
        assertFalse(PathMatcher.matchRegex(null, "^test$"),
            "Null path should return false");
        assertFalse(PathMatcher.matchRegex("test", null),
            "Null regex should return false");
    }

    // ========== Glob与Regex组合（PathLevelFilterEngine）测试 ==========

    @Test
    void testPathLevelEngine_IncludeWhitelist() {
        Set<String> includes = Set.of("order.**", "user.id");

        assertTrue(PathLevelFilterEngine.matchesIncludePatterns("order.items", includes),
            "Should match include pattern order.**");
        assertTrue(PathLevelFilterEngine.matchesIncludePatterns("user.id", includes),
            "Should match exact include pattern");
        assertFalse(PathLevelFilterEngine.matchesIncludePatterns("product.name", includes),
            "Should NOT match non-included pattern");
    }

    @Test
    void testPathLevelEngine_ExcludeGlobAndRegex() {
        Set<String> globExcludes = Set.of("*.internal.*", "debug.**");
        Set<String> regexExcludes = Set.of("^temp_\\d+$", "^cache.*");

        assertTrue(PathLevelFilterEngine.shouldIgnoreByPath("order.internal.cache", globExcludes, null),
            "Should match Glob exclude pattern");
        assertTrue(PathLevelFilterEngine.shouldIgnoreByPath("debug.logs.error", globExcludes, null),
            "Should match Glob ** pattern");
        assertTrue(PathLevelFilterEngine.shouldIgnoreByPath("temp_123", null, regexExcludes),
            "Should match Regex exclude pattern");
        assertTrue(PathLevelFilterEngine.shouldIgnoreByPath("cacheData", null, regexExcludes),
            "Should match Regex prefix pattern");
    }

    @Test
    void testPathLevelEngine_CombinedGlobAndRegex() {
        Set<String> globExcludes = Set.of("*.password");
        Set<String> regexExcludes = Set.of("^.*secret.*$");

        assertTrue(PathLevelFilterEngine.shouldIgnoreByPath("user.password", globExcludes, regexExcludes),
            "Should match Glob pattern");
        assertTrue(PathLevelFilterEngine.shouldIgnoreByPath("api.secretKey", globExcludes, regexExcludes),
            "Should match Regex pattern");
        assertFalse(PathLevelFilterEngine.shouldIgnoreByPath("user.email", globExcludes, regexExcludes),
            "Should NOT match either pattern");
    }

    // ========== 非法正则容错测试 ==========

    @Test
    void testInvalidRegex_ReturnsFalseWithoutException() {
        String invalidRegex = "[unclosed";

        assertDoesNotThrow(() -> PathMatcher.matchRegex("test", invalidRegex),
            "Invalid regex should not throw exception");
        assertFalse(PathMatcher.matchRegex("test", invalidRegex),
            "Invalid regex should return false");
    }

    @Test
    void testInvalidRegex_CachesNullPattern() {
        String invalidRegex = "(?invalid)";

        // First call
        assertFalse(PathMatcher.matchRegex("test", invalidRegex),
            "First call with invalid regex should return false");

        // Second call should use cached null
        assertFalse(PathMatcher.matchRegex("test", invalidRegex),
            "Second call should use cached result");

        // Verify pattern is cached (even if null)
        assertTrue(PathMatcher.getCacheSize() > 0,
            "Invalid pattern should be cached");
    }

    // ========== 缓存性能测试 ==========

    @Test
    void testCache_PatternReuse() {
        String pattern = "order.items[*].price";

        // First call compiles pattern
        PathMatcher.matchGlob("order.items[0].price", pattern);
        int cacheAfterFirst = PathMatcher.getCacheSize();
        assertTrue(cacheAfterFirst > 0, "Pattern should be cached after first use");

        // Second call reuses pattern
        PathMatcher.matchGlob("order.items[1].price", pattern);
        int cacheAfterSecond = PathMatcher.getCacheSize();
        assertEquals(cacheAfterFirst, cacheAfterSecond,
            "Cache size should remain same when pattern is reused");
    }

    @Test
    void testCache_HitRateSimulation() {
        // Simulate high cache hit scenario
        String[] patterns = {"order.*", "user.**", "items[*].id"};
        String[] paths = {"order.name", "order.id", "user.profile", "user.email", "items[0].id", "items[1].id"};

        int totalMatches = 0;
        for (String path : paths) {
            for (String pattern : patterns) {
                PathMatcher.matchGlob(path, pattern);
                totalMatches++;
            }
        }

        // With 3 patterns and 6 paths, we have 18 matches
        // Only 3 unique patterns should be cached
        int cacheSize = PathMatcher.getCacheSize();
        assertTrue(cacheSize <= patterns.length,
            String.format("Cache should contain at most %d patterns, but has %d",
                patterns.length, cacheSize));

        // Cache hit rate = (18 - 3) / 18 = 83.3% (approximate)
        // This validates that caching works for repeated patterns
    }

    // ========== 边界条件测试 ==========

    @Test
    void testNullAndEmptyInputs() {
        assertFalse(PathMatcher.matchGlob(null, "pattern"),
            "Null path should return false");
        assertFalse(PathMatcher.matchGlob("path", null),
            "Null pattern should return false");
        assertFalse(PathMatcher.matchGlob(null, null),
            "Both null should return false");

        assertTrue(PathMatcher.matchGlob("", ""),
            "Both empty should match");
    }

    @Test
    void testSpecialGlobPatterns() {
        assertTrue(PathMatcher.matchGlob("anything", "**"),
            "** alone should match anything");
        assertTrue(PathMatcher.matchGlob("anything.nested", "**"),
            "** alone should match nested paths");
        assertTrue(PathMatcher.matchGlob("any", "*"),
            "* alone should match single segment");
        assertFalse(PathMatcher.matchGlob("any.nested", "*"),
            "* alone should NOT match nested paths");
    }
}
