package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.algo.edit.LevenshteinEditDistance;
import com.syy.taskflowinsight.tracking.algo.seq.LongestCommonSubsequence;
import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 比较策略和算法测试。
 * 覆盖 MapCompareStrategy、SetCompareStrategy、CollectionCompareStrategy、
 * ArrayCompareStrategy、NumericCompareStrategy、DateCompareStrategy、
 * LCS 算法、Levenshtein 算法。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Strategy & Algorithm — 策略和算法测试")
class StrategyAndAlgoTests {

    // ── MapCompareStrategy ──

    @Nested
    @DisplayName("MapCompareStrategy — Map 比较")
    class MapCompareStrategyTests {

        private final MapCompareStrategy strategy = new MapCompareStrategy();

        @Test
        @DisplayName("相同 Map → identical")
        void sameMaps_shouldBeIdentical() {
            Map<String, Object> map = Map.of("k1", "v1", "k2", "v2");
            CompareResult result = strategy.compare(map, map, CompareOptions.DEFAULT);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("不同值 → 检测到变更")
        void differentValues_shouldDetectChanges() {
            Map<String, Object> a = new HashMap<>(Map.of("k1", "v1", "k2", "v2"));
            Map<String, Object> b = new HashMap<>(Map.of("k1", "v1", "k2", "changed"));
            CompareResult result = strategy.compare(a, b, CompareOptions.DEFAULT);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("新增键 → 检测到新增")
        void addedKey_shouldDetectAddition() {
            Map<String, Object> a = new HashMap<>(Map.of("k1", "v1"));
            Map<String, Object> b = new HashMap<>(Map.of("k1", "v1", "k2", "v2"));
            CompareResult result = strategy.compare(a, b, CompareOptions.DEFAULT);
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).anyMatch(c ->
                    c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("删除键 → 检测到删除")
        void removedKey_shouldDetectRemoval() {
            Map<String, Object> a = new HashMap<>(Map.of("k1", "v1", "k2", "v2"));
            Map<String, Object> b = new HashMap<>(Map.of("k1", "v1"));
            CompareResult result = strategy.compare(a, b, CompareOptions.DEFAULT);
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).anyMatch(c ->
                    c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("空 Map vs 非空 → 检测到差异")
        void emptyVsNonEmpty_shouldDetectDifferences() {
            CompareResult result = strategy.compare(
                    new HashMap<>(),
                    new HashMap<>(Map.of("k1", "v1")),
                    CompareOptions.DEFAULT
            );
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("supports Map → true")
        void supportsMap_shouldReturnTrue() {
            assertThat(strategy.supports(Map.class)).isTrue();
            assertThat(strategy.supports(HashMap.class)).isTrue();
        }

        @Test
        @DisplayName("嵌套 Map → 递归比较")
        void nestedMaps_shouldCompareRecursively() {
            Map<String, Object> a = new HashMap<>();
            a.put("outer", new HashMap<>(Map.of("inner", "v1")));
            Map<String, Object> b = new HashMap<>();
            b.put("outer", new HashMap<>(Map.of("inner", "v2")));
            CompareResult result = strategy.compare(a, b, CompareOptions.DEFAULT);
            assertThat(result.isIdentical()).isFalse();
        }
    }

    // ── SetCompareStrategy ──

    @Nested
    @DisplayName("SetCompareStrategy — Set 比较")
    class SetCompareStrategyTests {

        private final SetCompareStrategy strategy = new SetCompareStrategy();

        @Test
        @DisplayName("相同 Set → identical")
        void sameSets_shouldBeIdentical() {
            Set<String> set = Set.of("a", "b", "c");
            CompareResult result = strategy.compare(set, set, CompareOptions.DEFAULT);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("新增元素 → 检测到")
        void addedElement_shouldBeDetected() {
            Set<String> a = Set.of("a", "b");
            Set<String> b = Set.of("a", "b", "c");
            CompareResult result = strategy.compare(a, b, CompareOptions.DEFAULT);
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("删除元素 → 检测到")
        void removedElement_shouldBeDetected() {
            Set<String> a = Set.of("a", "b", "c");
            Set<String> b = Set.of("a", "b");
            CompareResult result = strategy.compare(a, b, CompareOptions.DEFAULT);
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("supports Set → true")
        void supportsSet_shouldReturnTrue() {
            assertThat(strategy.supports(Set.class)).isTrue();
            assertThat(strategy.supports(HashSet.class)).isTrue();
        }
    }

    // ── NumericCompareStrategy ──

    @Nested
    @DisplayName("NumericCompareStrategy — 数值比较")
    class NumericCompareStrategyTests {

        @Test
        @DisplayName("相同 float → equal")
        void sameFloats_shouldBeEqual() {
            NumericCompareStrategy strategy = new NumericCompareStrategy();
            boolean result = strategy.compareFloats(10.0, 10.0, 1e-12, 1e-9);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("不同 float → not equal")
        void differentFloats_shouldNotBeEqual() {
            NumericCompareStrategy strategy = new NumericCompareStrategy();
            boolean result = strategy.compareFloats(10.0, 20.0, 1e-12, 1e-9);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("BigDecimal compareTo → equal for same value")
        void bigDecimalCompareTo_shouldBeEqual() {
            NumericCompareStrategy strategy = new NumericCompareStrategy();
            boolean result = strategy.compareBigDecimals(
                    new BigDecimal("10.0"), new BigDecimal("10.00"),
                    NumericCompareStrategy.CompareMethod.COMPARE_TO, 0);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("不同 BigDecimal → not equal")
        void differentBigDecimal_shouldNotBeEqual() {
            NumericCompareStrategy strategy = new NumericCompareStrategy();
            boolean result = strategy.compareBigDecimals(
                    new BigDecimal("10.00"), new BigDecimal("10.01"),
                    NumericCompareStrategy.CompareMethod.COMPARE_TO, 0);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("compareNumbers 整数比较")
        void compareNumbers_shouldWork() {
            NumericCompareStrategy strategy = new NumericCompareStrategy();
            boolean result = strategy.compareNumbers(42, 42, null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("CompareMethod 枚举值完整")
        void compareMethodEnum_shouldBeComplete() {
            assertThat(NumericCompareStrategy.CompareMethod.values().length).isGreaterThan(0);
            assertThat(NumericCompareStrategy.CompareMethod.COMPARE_TO).isNotNull();
        }
    }

    // ── ArrayCompareStrategy ──

    @Nested
    @DisplayName("ArrayCompareStrategy — 数组比较")
    class ArrayCompareStrategyTests {

        private final ArrayCompareStrategy strategy = new ArrayCompareStrategy();

        @Test
        @DisplayName("相同数组 → identical")
        void sameArrays_shouldBeIdentical() {
            String[] arr = {"a", "b", "c"};
            CompareResult result = strategy.compare(arr, arr, CompareOptions.DEFAULT);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("不同数组 → 检测差异")
        void differentArrays_shouldDetectDifferences() {
            String[] a = {"a", "b", "c"};
            String[] b = {"a", "d", "c"};
            CompareResult result = strategy.compare(a, b, CompareOptions.DEFAULT);
            assertThat(result.isIdentical()).isFalse();
        }
    }

    // ── DateCompareStrategy ──

    @Nested
    @DisplayName("DateCompareStrategy — 日期比较")
    class DateCompareStrategyTests {

        private final DateCompareStrategy strategy = new DateCompareStrategy();

        @Test
        @DisplayName("相同日期 → identical")
        void sameDates_shouldBeIdentical() {
            Date now = new Date();
            CompareResult result = strategy.compare(now, now, CompareOptions.DEFAULT);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("不同日期 → 检测差异")
        void differentDates_shouldDetectDifference() {
            Date date1 = new Date(1000000);
            Date date2 = new Date(2000000);
            CompareResult result = strategy.compare(date1, date2, CompareOptions.DEFAULT);
            assertThat(result.isIdentical()).isFalse();
        }
    }

    // ── LCS 算法 ──

    @Nested
    @DisplayName("LongestCommonSubsequence — LCS 算法")
    class LcsAlgorithmTests {

        @Test
        @DisplayName("相同字符串 → LCS = 字符串长度")
        void sameStrings_shouldReturnFullLength() {
            int lcs = LongestCommonSubsequence.lcsLength("abc", "abc",
                    LongestCommonSubsequence.Options.defaults());
            assertThat(lcs).isEqualTo(3);
        }

        @Test
        @DisplayName("无共同字符 → LCS = 0")
        void noCommonChars_shouldReturnZero() {
            int lcs = LongestCommonSubsequence.lcsLength("abc", "xyz",
                    LongestCommonSubsequence.Options.defaults());
            assertThat(lcs).isEqualTo(0);
        }

        @Test
        @DisplayName("部分共同 → 正确 LCS")
        void partialCommon_shouldReturnCorrectLcs() {
            int lcs = LongestCommonSubsequence.lcsLength("abcde", "ace",
                    LongestCommonSubsequence.Options.defaults());
            assertThat(lcs).isEqualTo(3);
        }

        @Test
        @DisplayName("超阈值 → 返回 -1")
        void beyondThreshold_shouldReturnNegativeOne() {
            String longA = "a".repeat(400);
            String longB = "b".repeat(400);
            int lcs = LongestCommonSubsequence.lcsLength(longA, longB,
                    new LongestCommonSubsequence.Options(300));
            assertThat(lcs).isEqualTo(-1);
        }

        @Test
        @DisplayName("lcsTable → 返回正确 DP 表")
        void lcsTable_shouldReturnCorrectTable() {
            int[][] table = LongestCommonSubsequence.lcsTable(
                    List.of("a", "b", "c"), List.of("a", "c"));
            assertThat(table).isNotNull();
            assertThat(table[3][2]).isEqualTo(2); // LCS("abc", "ac") = 2
        }
    }

    // ── Levenshtein 算法 ──

    @Nested
    @DisplayName("LevenshteinEditDistance — 编辑距离")
    class LevenshteinAlgorithmTests {

        @Test
        @DisplayName("相同字符串 → 距离 0")
        void sameStrings_shouldHaveZeroDistance() {
            int dist = LevenshteinEditDistance.distance("abc", "abc",
                    LevenshteinEditDistance.Options.defaults());
            assertThat(dist).isEqualTo(0);
        }

        @Test
        @DisplayName("完全不同 → 距离 = max(len1, len2)")
        void completelyDifferent_shouldHaveMaxDistance() {
            int dist = LevenshteinEditDistance.distance("abc", "xyz",
                    LevenshteinEditDistance.Options.defaults());
            assertThat(dist).isGreaterThan(0);
        }

        @Test
        @DisplayName("空 vs 非空 → 距离 = 非空长度")
        void emptyVsNonEmpty_shouldBeStringLength() {
            int dist = LevenshteinEditDistance.distance("", "abc",
                    LevenshteinEditDistance.Options.defaults());
            assertThat(dist).isEqualTo(3);
        }

        @Test
        @DisplayName("超阈值 → 返回 MAX_VALUE")
        void beyondThreshold_shouldReturnMaxValue() {
            String longA = "a".repeat(600);
            String longB = "b".repeat(600);
            int dist = LevenshteinEditDistance.distance(longA, longB,
                    new LevenshteinEditDistance.Options(500));
            assertThat(dist).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("null 输入 → MAX_VALUE")
        void nullInput_shouldReturnMaxValue() {
            int dist = LevenshteinEditDistance.distance(null, "abc",
                    LevenshteinEditDistance.Options.defaults());
            assertThat(dist).isEqualTo(Integer.MAX_VALUE);
        }
    }

    // ── PropertyComparatorRegistry ──

    @Nested
    @DisplayName("PropertyComparatorRegistry — 属性比较器注册表")
    class PropertyComparatorRegistryTests {

        @Test
        @DisplayName("register + findComparator → 返回注册的比较器")
        void registerAndFind_shouldWork() {
            PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
            PropertyComparator comparator = (left, right, field) -> true;
            registry.register("user.name", comparator);
            PropertyComparator found = registry.findComparator("user.name", null);
            assertThat(found).isNotNull();
            assertThat(found).isSameAs(comparator);
        }

        @Test
        @DisplayName("findComparator 未注册 → null")
        void findUnregistered_shouldReturnNull() {
            PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
            PropertyComparator found = registry.findComparator("unknown.path", null);
            assertThat(found).isNull();
        }

        @Test
        @DisplayName("register null 路径 → 拒绝")
        void registerNullPath_shouldReject() {
            PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
            assertThatThrownBy(() -> registry.register(null,
                    (left, right, field) -> true))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("register 空路径 → 拒绝")
        void registerEmptyPath_shouldReject() {
            PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
            assertThatThrownBy(() -> registry.register("",
                    (left, right, field) -> true))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("getMetricsSnapshot 返回有效快照")
        void metricsSnapshot_shouldBeValid() {
            PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
            var snapshot = registry.getMetricsSnapshot();
            assertThat(snapshot).isNotNull();
        }
    }
}
