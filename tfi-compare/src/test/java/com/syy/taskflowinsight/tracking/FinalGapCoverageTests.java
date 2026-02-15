package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy;
import com.syy.taskflowinsight.metrics.AsyncMetricsCollector;
import com.syy.taskflowinsight.tracking.precision.PrecisionMetrics;
import com.syy.taskflowinsight.tracking.summary.CollectionSummary;
import com.syy.taskflowinsight.tracking.summary.SummaryInfo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 剩余类最终覆盖测试。
 * 针对 CollectionSummary、PrecisionMetrics、NumericCompareStrategy、
 * AsyncMetricsCollector、ChangeTracker、LevenshteinListStrategy 的未覆盖路径。
 *
 * @since 3.0.0
 */
@DisplayName("Final Gap Coverage — 最终缺口覆盖测试")
class FinalGapCoverageTests {

    @AfterEach
    void tearDown() {
        ChangeTracker.clearAllTracking();
    }

    // ── CollectionSummary ──

    @Nested
    @DisplayName("CollectionSummary — 集合摘要")
    class CollectionSummaryTests {

        @Test
        @DisplayName("shouldSummarize — 大集合返回 true")
        void shouldSummarize_largeCollection() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(10);
            summary.setEnabled(true);
            List<Integer> large = new ArrayList<>();
            for (int i = 0; i < 50; i++) large.add(i);
            assertThat(summary.shouldSummarize(large)).isTrue();
        }

        @Test
        @DisplayName("shouldSummarize — 小集合返回 false")
        void shouldSummarize_smallCollection() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(100);
            assertThat(summary.shouldSummarize(List.of(1, 2, 3))).isFalse();
        }

        @Test
        @DisplayName("shouldSummarize — null 返回 false")
        void shouldSummarize_null() {
            CollectionSummary summary = new CollectionSummary();
            assertThat(summary.shouldSummarize(null)).isFalse();
        }

        @Test
        @DisplayName("shouldSummarize — disabled 返回 false")
        void shouldSummarize_disabled() {
            CollectionSummary summary = new CollectionSummary();
            summary.setEnabled(false);
            assertThat(summary.shouldSummarize(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11))).isFalse();
        }

        @Test
        @DisplayName("summarize — null 返回 empty")
        void summarize_null() {
            CollectionSummary summary = new CollectionSummary();
            SummaryInfo info = summary.summarize(null);
            assertThat(info).isNotNull();
            assertThat(info.getType()).isEqualTo("empty");
        }

        @Test
        @DisplayName("summarize — 小 List")
        void summarize_smallList() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(100);
            summary.setMaxExamples(5);
            SummaryInfo info = summary.summarize(List.of(1, 2, 3, "a", "b"));
            assertThat(info).isNotNull();
            assertThat(info.getSize()).isEqualTo(5);
            assertThat(info.getExamples()).isNotEmpty();
        }

        @Test
        @DisplayName("summarize — 大 List 触发降级")
        void summarize_largeList() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(10);
            summary.setMaxExamples(5);
            List<Integer> large = new ArrayList<>();
            for (int i = 0; i < 150; i++) large.add(i);
            SummaryInfo info = summary.summarize(large);
            assertThat(info).isNotNull();
            assertThat(info.getSize()).isEqualTo(150);
            assertThat(info.isTruncated()).isTrue();
        }

        @Test
        @DisplayName("summarize — 数值 List 计算统计")
        void summarize_numericList() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(10);
            summary.setMaxExamples(10);
            // 大 List 触发 isNumericCollection 分支（maxProcess = min(150, 20) = 20）
            List<Double> nums = new ArrayList<>();
            for (int i = 0; i < 150; i++) nums.add((double) i);
            SummaryInfo info = summary.summarize(nums);
            assertThat(info).isNotNull();
            assertThat(info.getStatistics()).isNotNull();
            assertThat(info.getStatistics().getMin()).isEqualTo(0.0);
            assertThat(info.getStatistics().getMax()).isEqualTo(19.0); // 仅处理前 20 个
        }

        @Test
        @DisplayName("summarize — Map")
        void summarize_map() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(100);
            Map<String, Integer> map = Map.of("a", 1, "b", 2, "c", 3);
            SummaryInfo info = summary.summarize(map);
            assertThat(info).isNotNull();
            assertThat(info.getType()).isEqualTo("Map");
            assertThat(info.getMapExamples()).isNotEmpty();
        }

        @Test
        @DisplayName("summarize — 大 Map")
        void summarize_largeMap() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(10);
            Map<String, Integer> map = new HashMap<>();
            for (int i = 0; i < 50; i++) map.put("k" + i, i);
            SummaryInfo info = summary.summarize(map);
            assertThat(info).isNotNull();
            assertThat(info.getKeyTypeDistribution()).isNotNull();
            assertThat(info.getValueTypeDistribution()).isNotNull();
        }

        @Test
        @DisplayName("summarize — 数组")
        void summarize_array() {
            CollectionSummary summary = new CollectionSummary();
            int[] arr = {1, 2, 3, 4, 5};
            SummaryInfo info = summary.summarize(arr);
            assertThat(info).isNotNull();
            assertThat(info.getType()).isEqualTo("int[]");
            assertThat(info.getSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("summarize — 数值数组统计")
        void summarize_numericArray() {
            CollectionSummary summary = new CollectionSummary();
            double[] arr = {1.0, 2.0, 3.0};
            SummaryInfo info = summary.summarize(arr);
            assertThat(info).isNotNull();
            assertThat(info.getStatistics()).isNotNull();
        }

        @Test
        @DisplayName("summarize — 不支持类型")
        void summarize_unsupported() {
            CollectionSummary summary = new CollectionSummary();
            SummaryInfo info = summary.summarize("not a collection");
            assertThat(info).isNotNull();
            assertThat(info.getFeatures()).contains("unsupported");
        }

        @Test
        @DisplayName("summarize — Set 特征")
        void summarize_set() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(10);
            summary.setMaxExamples(5);
            Set<Integer> largeSet = new HashSet<>();
            for (int i = 0; i < 50; i++) largeSet.add(i);
            SummaryInfo info = summary.summarize(largeSet);
            assertThat(info).isNotNull();
            assertThat(info.getFeatures()).isNotNull();
            assertThat(info.getFeatures()).contains("unique");
        }

        @Test
        @DisplayName("getInstance — 非 Spring 环境")
        void getInstance() {
            CollectionSummary instance = CollectionSummary.getInstance();
            assertThat(instance).isNotNull();
        }
    }

    // ── PrecisionMetrics ──

    @Nested
    @DisplayName("PrecisionMetrics — 精度指标")
    class PrecisionMetricsTests {

        @Test
        @DisplayName("recordNumericComparison 无参")
        void recordNumericComparison() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordNumericComparison();
            var snap = m.getSnapshot();
            assertThat(snap.numericComparisonCount).isEqualTo(1);
        }

        @Test
        @DisplayName("recordNumericComparison 带类型")
        void recordNumericComparisonWithType() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordNumericComparison("float");
            var snap = m.getSnapshot();
            assertThat(snap.numericComparisonCount).isEqualTo(1);
        }

        @Test
        @DisplayName("recordDateTimeComparison")
        void recordDateTimeComparison() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordDateTimeComparison();
            m.recordDateTimeComparison("LocalDate");
            var snap = m.getSnapshot();
            assertThat(snap.dateTimeComparisonCount).isEqualTo(2);
        }

        @Test
        @DisplayName("recordToleranceHit — absolute")
        void recordToleranceHit_absolute() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordToleranceHit("absolute", 0.001);
            var snap = m.getSnapshot();
            assertThat(snap.toleranceHitCount).isEqualTo(1);
            assertThat(snap.absoluteToleranceHits).isEqualTo(1);
        }

        @Test
        @DisplayName("recordToleranceHit — relative")
        void recordToleranceHit_relative() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordToleranceHit("relative", 0.0001);
            var snap = m.getSnapshot();
            assertThat(snap.relativeToleranceHits).isEqualTo(1);
        }

        @Test
        @DisplayName("recordToleranceHit — date")
        void recordToleranceHit_date() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordToleranceHit("date", 100);
            var snap = m.getSnapshot();
            assertThat(snap.dateToleranceHits).isEqualTo(1);
        }

        @Test
        @DisplayName("recordBigDecimalComparison")
        void recordBigDecimalComparison() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordBigDecimalComparison("compareTo");
            var snap = m.getSnapshot();
            assertThat(snap.bigDecimalComparisonCount).isEqualTo(1);
        }

        @Test
        @DisplayName("recordCalculationTime")
        void recordCalculationTime() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordCalculationTime(500_000);
            var snap = m.getSnapshot();
            assertThat(snap.calculationCount).isEqualTo(1);
            assertThat(snap.totalCalculationTimeNanos).isEqualTo(500_000);
        }

        @Test
        @DisplayName("recordCacheHit / recordCacheMiss")
        void recordCache() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordCacheHit();
            m.recordCacheHit();
            m.recordCacheMiss();
            var snap = m.getSnapshot();
            assertThat(snap.precisionCacheHitCount).isEqualTo(2);
            assertThat(snap.precisionCacheMissCount).isEqualTo(1);
            assertThat(snap.getCacheHitRate()).isEqualTo(2.0 / 3.0);
        }

        @Test
        @DisplayName("getSnapshot — 平均计算时间")
        void snapshotAverageTime() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordCalculationTime(1000);
            m.recordCalculationTime(2000);
            var snap = m.getSnapshot();
            assertThat(snap.getAverageCalculationTimeMicros()).isEqualTo(1.5);
        }

        @Test
        @DisplayName("getSnapshot — 容差命中率")
        void snapshotToleranceHitRate() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordNumericComparison();
            m.recordToleranceHit("absolute", 0.001);
            var snap = m.getSnapshot();
            assertThat(snap.getToleranceHitRate()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("reset")
        void reset() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordNumericComparison();
            m.recordCacheHit();
            m.reset();
            var snap = m.getSnapshot();
            assertThat(snap.numericComparisonCount).isEqualTo(0);
            assertThat(snap.precisionCacheHitCount).isEqualTo(0);
        }

        @Test
        @DisplayName("logSummary — 不抛异常")
        void logSummary() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordNumericComparison();
            assertThatCode(() -> m.logSummary()).doesNotThrowAnyException();
        }
    }

    // ── NumericCompareStrategy ──

    @Nested
    @DisplayName("NumericCompareStrategy — 数值比较")
    class NumericCompareStrategyTests {

        private final Field dummyField = String.class.getDeclaredFields()[0];

        @Test
        @DisplayName("compareFloats — NaN 与 NaN 相等")
        void compareFloats_nanNan() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareFloats(Double.NaN, Double.NaN, dummyField)).isTrue();
        }

        @Test
        @DisplayName("compareFloats — 无穷大")
        void compareFloats_infinity() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareFloats(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, dummyField)).isTrue();
            assertThat(s.compareFloats(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, dummyField)).isTrue();
            assertThat(s.compareFloats(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, dummyField)).isFalse();
        }

        @Test
        @DisplayName("compareFloats — NaN 与普通值不等")
        void compareFloats_nanVsNormal() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareFloats(Double.NaN, 1.0, dummyField)).isFalse();
        }

        @Test
        @DisplayName("compareFloats — 绝对容差")
        void compareFloats_absoluteTolerance() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareFloats(0.0, 1e-13, 1e-12, 1e-9)).isTrue();
        }

        @Test
        @DisplayName("compareFloats — 相对容差")
        void compareFloats_relativeTolerance() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareFloats(1000.0, 1000.0 + 1e-6, 1e-12, 1e-9)).isTrue();
        }

        @Test
        @DisplayName("compareFloats — 不等")
        void compareFloats_notEqual() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareFloats(1.0, 2.0, dummyField)).isFalse();
        }

        @Test
        @DisplayName("compareBigDecimals — 双 null")
        void compareBigDecimals_bothNull() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareBigDecimals(null, null, dummyField)).isTrue();
        }

        @Test
        @DisplayName("compareBigDecimals — 单 null")
        void compareBigDecimals_oneNull() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareBigDecimals(BigDecimal.ONE, null, dummyField)).isFalse();
        }

        @Test
        @DisplayName("compareBigDecimals — COMPARE_TO")
        void compareBigDecimals_compareTo() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareBigDecimals(
                    BigDecimal.valueOf(1.0),
                    BigDecimal.valueOf(1.00),
                    NumericCompareStrategy.CompareMethod.COMPARE_TO,
                    0)).isTrue();
        }

        @Test
        @DisplayName("compareBigDecimals — EQUALS 不同 scale")
        void compareBigDecimals_equals() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            // 1.0 (scale=1) vs 1.00 (scale=2) — equals 严格比较 scale，故不等
            assertThat(s.compareBigDecimals(
                    new BigDecimal("1.0"),
                    new BigDecimal("1.00"),
                    NumericCompareStrategy.CompareMethod.EQUALS,
                    0)).isFalse();
        }

        @Test
        @DisplayName("compareBigDecimals — WITH_TOLERANCE")
        void compareBigDecimals_tolerance() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareBigDecimals(
                    BigDecimal.valueOf(1.0),
                    BigDecimal.valueOf(1.001),
                    NumericCompareStrategy.CompareMethod.WITH_TOLERANCE,
                    0.01)).isTrue();
        }

        @Test
        @DisplayName("compareBigDecimals — COMPARE_TO 带容差")
        void compareBigDecimals_compareToWithTolerance() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareBigDecimals(
                    BigDecimal.valueOf(1.0),
                    BigDecimal.valueOf(1.0001),
                    NumericCompareStrategy.CompareMethod.COMPARE_TO,
                    0.001)).isTrue();
        }

        @Test
        @DisplayName("compareNumbers — BigDecimal")
        void compareNumbers_bigDecimal() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareNumbers(BigDecimal.ONE, BigDecimal.ONE, dummyField)).isTrue();
        }

        @Test
        @DisplayName("compareNumbers — Integer")
        void compareNumbers_integer() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareNumbers(1, 1, dummyField)).isTrue();
        }

        @Test
        @DisplayName("compareNumbers — 双 null")
        void compareNumbers_bothNull() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareNumbers(null, null, dummyField)).isTrue();
        }

        @Test
        @DisplayName("isNumericType")
        void isNumericType() {
            assertThat(NumericCompareStrategy.isNumericType(1)).isTrue();
            assertThat(NumericCompareStrategy.isNumericType(null)).isFalse();
            assertThat(NumericCompareStrategy.isNumericType("x")).isFalse();
        }

        @Test
        @DisplayName("needsPrecisionCompare")
        void needsPrecisionCompare() {
            assertThat(NumericCompareStrategy.needsPrecisionCompare(1, 2)).isTrue();
            assertThat(NumericCompareStrategy.needsPrecisionCompare(1, null)).isFalse();
        }

        @Test
        @DisplayName("带 metrics 的 compareFloats")
        void compareFloats_withMetrics() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            PrecisionMetrics m = new PrecisionMetrics();
            s.setMetrics(m);
            s.compareFloats(0.0, 1e-14, dummyField);
            var snap = m.getSnapshot();
            assertThat(snap.numericComparisonCount).isEqualTo(1);
        }
    }

    // ── AsyncMetricsCollector ──

    @Nested
    @DisplayName("AsyncMetricsCollector — 异步指标")
    class AsyncMetricsCollectorTests {

        @Test
        @DisplayName("recordCounter")
        void recordCounter() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();
            collector.recordCounter("test.counter");
            collector.recordCounter("test.counter2", 2.0);
            var stats = collector.getStats();
            assertThat(stats.getTotalEvents()).isGreaterThanOrEqualTo(2);
            collector.destroy();
        }

        @Test
        @DisplayName("recordTimer")
        void recordTimer() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();
            collector.recordTimer("test.timer", 1_000_000);
            var stats = collector.getStats();
            assertThat(stats.getTotalEvents()).isGreaterThanOrEqualTo(1);
            collector.destroy();
        }

        @Test
        @DisplayName("recordGauge")
        void recordGauge() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();
            collector.recordGauge("test.gauge", 42.0);
            var stats = collector.getStats();
            assertThat(stats.getTotalEvents()).isGreaterThanOrEqualTo(1);
            collector.destroy();
        }

        @Test
        @DisplayName("getStats — CollectorStats")
        void getStats() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();
            collector.recordCounter("x");
            var stats = collector.getStats();
            assertThat(stats.getBufferSize()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getMaxBufferSize()).isPositive();
            assertThat(stats.getDropRate()).isBetween(0.0, 1.0);
            assertThat(stats.getBufferUtilization()).isBetween(0.0, 1.0);
            assertThat(stats.toString()).isNotBlank();
            collector.destroy();
        }
    }

    // ── ChangeTracker ──

    @Nested
    @DisplayName("ChangeTracker — 变更追踪")
    class ChangeTrackerTests {

        @Test
        @DisplayName("track — null name 跳过")
        void track_nullName() {
            ChangeTracker.track(null, "obj", "f");
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("track — null target 跳过")
        void track_nullTarget() {
            ChangeTracker.track("x", null, "f");
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("track — 正常追踪")
        void track_normal() {
            ChangeTracker.clearAllTracking();
            class Obj { int a = 1; }
            Obj o = new Obj();
            ChangeTracker.track("obj", o, "a");
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(1);
            o.a = 2;
            var changes = ChangeTracker.getChanges();
            assertThat(changes).isNotNull();
            assertThat(o.a).isEqualTo(2);
        }

        @Test
        @DisplayName("track — TrackingOptions")
        void track_withOptions() {
            ChangeTracker.clearAllTracking();
            class Obj { int x = 1; }
            Obj o = new Obj();
            ChangeTracker.track("obj", o, TrackingOptions.shallow());
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(1);
            assertThat(o.x).isEqualTo(1);
        }

        @Test
        @DisplayName("trackAll")
        void trackAll() {
            ChangeTracker.clearAllTracking();
            Map<String, Object> map = Map.of(
                    "a", Map.of("v", 1),
                    "b", Map.of("v", 2));
            ChangeTracker.trackAll(map);
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("trackAll — null/empty 跳过")
        void trackAll_nullEmpty() {
            ChangeTracker.clearAllTracking();
            ChangeTracker.trackAll(null);
            ChangeTracker.trackAll(Collections.emptyMap());
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("getChanges — 空快照")
        void getChanges_empty() {
            ChangeTracker.clearAllTracking();
            var changes = ChangeTracker.getChanges();
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("clearAllTracking")
        void clearAllTracking() {
            ChangeTracker.track("x", "val");
            ChangeTracker.clearAllTracking();
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("getTrackedCount")
        void getTrackedCount() {
            ChangeTracker.clearAllTracking();
            ChangeTracker.track("a", 1);
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("getMaxTrackedObjects")
        void getMaxTrackedObjects() {
            assertThat(ChangeTracker.getMaxTrackedObjects()).isPositive();
        }

        @Test
        @DisplayName("clearBySessionId")
        void clearBySessionId() {
            ChangeTracker.track("x", 1);
            ChangeTracker.clearBySessionId("s1");
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(0);
        }
    }

    // ── LevenshteinListStrategy ──

    @Nested
    @DisplayName("LevenshteinListStrategy — 编辑距离")
    class LevenshteinListStrategyTests {

        private final LevenshteinListStrategy strategy = new LevenshteinListStrategy();

        @Test
        @DisplayName("list1 == list2 引用相同")
        void sameReference() {
            List<String> list = List.of("a", "b");
            CompareResult r = strategy.compare(list, list, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("null list1")
        void nullList1() {
            CompareResult r = strategy.compare(null, List.of("a"), CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("null list2")
        void nullList2() {
            CompareResult r = strategy.compare(List.of("a"), null, CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("processWithoutMoves — DELETE")
        void withoutMoves_delete() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "c");
            CompareOptions opts = CompareOptions.builder().detectMoves(false).build();
            CompareResult r = strategy.compare(before, after, opts);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == com.syy.taskflowinsight.tracking.ChangeType.DELETE);
        }

        @Test
        @DisplayName("processWithoutMoves — INSERT")
        void withoutMoves_insert() {
            List<String> before = List.of("a", "c");
            List<String> after = List.of("a", "b", "c");
            CompareOptions opts = CompareOptions.builder().detectMoves(false).build();
            CompareResult r = strategy.compare(before, after, opts);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == com.syy.taskflowinsight.tracking.ChangeType.CREATE);
        }

        @Test
        @DisplayName("processWithoutMoves — REPLACE")
        void withoutMoves_replace() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "x", "c");
            CompareOptions opts = CompareOptions.builder().detectMoves(false).build();
            CompareResult r = strategy.compare(before, after, opts);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == com.syy.taskflowinsight.tracking.ChangeType.UPDATE);
        }

        @Test
        @DisplayName("processWithMoveDetection")
        void withMoveDetection() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("c", "a", "b");
            CompareOptions opts = CompareOptions.builder().detectMoves(true).build();
            CompareResult r = strategy.compare(before, after, opts);
            assertThat(r.getChanges()).isNotEmpty();
            assertThat(r.getAlgorithmUsed()).isEqualTo("LEVENSHTEIN");
        }

        @Test
        @DisplayName("空列表相同")
        void emptyLists() {
            CompareResult r = strategy.compare(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("supportsMoveDetection")
        void supportsMoveDetection() {
            assertThat(strategy.supportsMoveDetection()).isTrue();
        }

        @Test
        @DisplayName("getStrategyName")
        void getStrategyName() {
            assertThat(strategy.getStrategyName()).isEqualTo("LEVENSHTEIN");
        }

        @Test
        @DisplayName("getMaxRecommendedSize")
        void getMaxRecommendedSize() {
            assertThat(strategy.getMaxRecommendedSize()).isEqualTo(500);
        }

        @Test
        @DisplayName("单元素 DELETE")
        void singleDelete() {
            List<String> before = List.of("x");
            List<String> after = Collections.emptyList();
            CompareResult r = strategy.compare(before, after, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).hasSize(1);
            assertThat(r.getChanges().get(0).getChangeType()).isEqualTo(com.syy.taskflowinsight.tracking.ChangeType.DELETE);
        }

        @Test
        @DisplayName("单元素 CREATE")
        void singleCreate() {
            List<String> before = Collections.emptyList();
            List<String> after = List.of("x");
            CompareResult r = strategy.compare(before, after, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).hasSize(1);
            assertThat(r.getChanges().get(0).getChangeType()).isEqualTo(com.syy.taskflowinsight.tracking.ChangeType.CREATE);
        }
    }
}
