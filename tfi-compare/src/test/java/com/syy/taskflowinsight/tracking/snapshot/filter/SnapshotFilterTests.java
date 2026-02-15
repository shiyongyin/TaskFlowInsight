package com.syy.taskflowinsight.tracking.snapshot.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 快照过滤系统测试。
 * 覆盖 PathMatcher、FilterDecision、FilterReason。
 * 注意: PathLevelFilterEngine、DefaultExclusionEngine、UnifiedFilterEngine 构造器为 private。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Snapshot Filter — 快照过滤系统测试")
class SnapshotFilterTests {

    // ── PathMatcher ──

    @Nested
    @DisplayName("PathMatcher — 路径匹配")
    class PathMatcherTests {

        @Test
        @DisplayName("精确匹配 → true")
        void exactMatch_shouldReturnTrue() {
            assertThat(PathMatcher.matchGlob("user.name", "user.name")).isTrue();
        }

        @Test
        @DisplayName("通配符 * → 匹配单级")
        void singleWildcard_shouldMatchSingleLevel() {
            assertThat(PathMatcher.matchGlob("user.name", "user.*")).isTrue();
            assertThat(PathMatcher.matchGlob("user.address.city", "user.*")).isFalse();
        }

        @Test
        @DisplayName("通配符 ** → 匹配多级")
        void doubleWildcard_shouldMatchMultipleLevels() {
            assertThat(PathMatcher.matchGlob("user.address.city", "user.**")).isTrue();
        }

        @Test
        @DisplayName("不匹配 → false")
        void noMatch_shouldReturnFalse() {
            assertThat(PathMatcher.matchGlob("order.status", "user.*")).isFalse();
        }

        @Test
        @DisplayName("regex 匹配")
        void regexMatch_shouldWork() {
            assertThat(PathMatcher.matchRegex("user.name", "user\\..*")).isTrue();
            assertThat(PathMatcher.matchRegex("order.status", "user\\..*")).isFalse();
        }

        @Test
        @DisplayName("无效 regex → false (不抛异常)")
        void invalidRegex_shouldReturnFalse() {
            assertThat(PathMatcher.matchRegex("test", "[invalid")).isFalse();
        }

        @Test
        @DisplayName("null 输入 → false")
        void nullInput_shouldReturnFalse() {
            assertThat(PathMatcher.matchGlob(null, "pattern")).isFalse();
            assertThat(PathMatcher.matchGlob("path", null)).isFalse();
        }

        @Test
        @DisplayName("缓存可清除")
        void cache_shouldBeClearable() {
            PathMatcher.matchGlob("a.b", "a.*");
            assertThatCode(PathMatcher::clearCache).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("getCacheSize → 非负数")
        void cacheSize_shouldBeNonNegative() {
            assertThat(PathMatcher.getCacheSize()).isGreaterThanOrEqualTo(0);
        }
    }

    // ── FilterDecision ──

    @Nested
    @DisplayName("FilterDecision — 过滤决定")
    class FilterDecisionTests {

        @Test
        @DisplayName("include → shouldInclude=true, shouldExclude=false")
        void includeDecision_shouldBeCorrect() {
            FilterDecision decision = FilterDecision.include("test reason");
            assertThat(decision.shouldInclude()).isTrue();
            assertThat(decision.shouldExclude()).isFalse();
            assertThat(decision.getReason()).isEqualTo("test reason");
        }

        @Test
        @DisplayName("exclude → shouldExclude=true, shouldInclude=false")
        void excludeDecision_shouldBeCorrect() {
            FilterDecision decision = FilterDecision.exclude("excluded");
            assertThat(decision.shouldExclude()).isTrue();
            assertThat(decision.shouldInclude()).isFalse();
        }
    }

    // ── FilterReason ──

    @Nested
    @DisplayName("FilterReason — 过滤原因常量")
    class FilterReasonTests {

        @Test
        @DisplayName("所有原因常量非空")
        void allReasons_shouldBeNonBlank() {
            assertThat(FilterReason.INCLUDE_PATTERNS).isNotBlank();
            assertThat(FilterReason.EXCLUDE_PATTERNS).isNotBlank();
            assertThat(FilterReason.REGEX_EXCLUDES).isNotBlank();
            assertThat(FilterReason.EXCLUDE_PACKAGES).isNotBlank();
            assertThat(FilterReason.DEFAULT_EXCLUSIONS).isNotBlank();
            assertThat(FilterReason.DEFAULT_RETAIN).isNotBlank();
        }
    }
}
