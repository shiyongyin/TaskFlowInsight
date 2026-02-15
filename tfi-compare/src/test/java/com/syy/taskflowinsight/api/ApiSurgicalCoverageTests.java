package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.api.builder.TfiContext;
import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Surgical coverage tests for TrackingStatistics, DiffBuilder (deep compare).
 */
@DisplayName("API — Surgical Coverage Tests")
class ApiSurgicalCoverageTests {

    @AfterEach
    void tearDown() {
        ChangeTracker.clearAllTracking();
    }

    // ── TrackingStatistics ──

    @Nested
    @DisplayName("TrackingStatistics — All methods")
    class TrackingStatisticsTests {

        @Test
        @DisplayName("recordObjectTracked with null name uses placeholder")
        void recordObjectTracked_nullName() {
            TrackingStatistics stats = new TrackingStatistics();
            stats.recordObjectTracked(null);
            assertThat(stats.getSummary().totalObjectsTracked).isEqualTo(1);
            assertThat(stats.getTopChangedObjects(10)).extracting(TrackingStatistics.ObjectStatistics::getObjectName)
                .contains("<null>");
        }

        @Test
        @DisplayName("recordObjectTracked with empty string uses placeholder")
        void recordObjectTracked_emptyString() {
            TrackingStatistics stats = new TrackingStatistics();
            stats.recordObjectTracked("   ");
            assertThat(stats.getTopChangedObjects(10)).extracting(TrackingStatistics.ObjectStatistics::getObjectName)
                .contains("<empty>");
        }

        @Test
        @DisplayName("recordChanges with null/empty list does nothing")
        void recordChanges_nullOrEmpty() {
            TrackingStatistics stats = new TrackingStatistics();
            stats.recordChanges(null, 100);
            stats.recordChanges(Collections.emptyList(), 100);
            assertThat(stats.getSummary().totalChangesDetected).isZero();
        }

        @Test
        @DisplayName("recordChanges with all ChangeTypes")
        void recordChanges_allChangeTypes() {
            TrackingStatistics stats = new TrackingStatistics();
            stats.recordObjectTracked("Order");
            List<ChangeRecord> changes = Arrays.asList(
                ChangeRecord.of("Order", "status", "NEW", "PROCESSING", ChangeType.UPDATE),
                ChangeRecord.of("Order", "id", null, 1L, ChangeType.CREATE),
                ChangeRecord.of("Order", "temp", "x", null, ChangeType.DELETE),
                ChangeRecord.of("Order", "pos", "0", "1", ChangeType.MOVE)
            );
            stats.recordChanges(changes, 50_000_000);
            Map<ChangeType, Integer> dist = stats.getChangeTypeDistribution();
            assertThat(dist.get(ChangeType.UPDATE)).isEqualTo(1);
            assertThat(dist.get(ChangeType.CREATE)).isEqualTo(1);
            assertThat(dist.get(ChangeType.DELETE)).isEqualTo(1);
            assertThat(dist.get(ChangeType.MOVE)).isEqualTo(1);
        }

        @Test
        @DisplayName("recordChanges with null changeType skips type count when object not in stats")
        void recordChanges_nullChangeType() {
            TrackingStatistics stats = new TrackingStatistics();
            ChangeRecord cr = ChangeRecord.builder()
                .objectName("Unknown").fieldName("f").oldValue("a").newValue("b")
                .changeType(null).build();
            stats.recordChanges(List.of(cr), 1000);
            assertThat(stats.getSummary().totalChangesDetected).isEqualTo(1);
            assertThat(stats.getChangeTypeDistribution().values().stream().mapToInt(Integer::intValue).sum())
                .isZero();
        }

        @Test
        @DisplayName("recordChanges with unknown objectName skips objectStats update")
        void recordChanges_unknownObject() {
            TrackingStatistics stats = new TrackingStatistics();
            ChangeRecord cr = ChangeRecord.of("Unknown", "f", "a", "b", ChangeType.UPDATE);
            stats.recordChanges(List.of(cr), 1000);
            assertThat(stats.getTopChangedObjects(10)).isEmpty();
        }

        @Test
        @DisplayName("getSummary returns full summary")
        void getSummary() {
            TrackingStatistics stats = new TrackingStatistics();
            stats.recordObjectTracked("A");
            stats.recordObjectTracked("B");
            stats.recordChanges(List.of(
                ChangeRecord.of("A", "f", "x", "y", ChangeType.UPDATE)
            ), 1_000_000);
            TrackingStatistics.StatisticsSummary summary = stats.getSummary();
            assertThat(summary.totalObjectsTracked).isEqualTo(2);
            assertThat(summary.totalChangesDetected).isEqualTo(1);
            assertThat(summary.changeTypeDistribution).isNotEmpty();
            assertThat(summary.duration).isNotNull();
        }

        @Test
        @DisplayName("getAverageDetectionTimeMs returns 0 when no snapshots")
        void getAverageDetectionTimeMs_noSnapshots() {
            TrackingStatistics stats = new TrackingStatistics();
            assertThat(stats.getAverageDetectionTimeMs()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getTopChangedObjects with limit")
        void getTopChangedObjects() {
            TrackingStatistics stats = new TrackingStatistics();
            stats.recordObjectTracked("A");
            stats.recordObjectTracked("B");
            stats.recordObjectTracked("C");
            stats.recordChanges(List.of(
                ChangeRecord.of("A", "f1", "x", "y", ChangeType.UPDATE),
                ChangeRecord.of("A", "f2", "a", "b", ChangeType.UPDATE),
                ChangeRecord.of("B", "f", "x", "y", ChangeType.UPDATE)
            ), 1000);
            List<TrackingStatistics.ObjectStatistics> top = stats.getTopChangedObjects(2);
            assertThat(top).hasSize(2);
            assertThat(top.get(0).getTotalChanges()).isGreaterThanOrEqualTo(top.get(1).getTotalChanges());
        }

        @Test
        @DisplayName("getPerformanceStatistics empty returns zeros")
        void getPerformanceStatistics_empty() {
            TrackingStatistics stats = new TrackingStatistics();
            TrackingStatistics.PerformanceStatistics perf = stats.getPerformanceStatistics();
            assertThat(perf.minMicros).isZero();
            assertThat(perf.maxMicros).isZero();
        }

        @Test
        @DisplayName("getPerformanceStatistics with snapshots")
        void getPerformanceStatistics_withSnapshots() {
            TrackingStatistics stats = new TrackingStatistics();
            stats.recordChanges(List.of(
                ChangeRecord.of("A", "f", "x", "y", ChangeType.UPDATE)
            ), 100_000);
            stats.recordChanges(List.of(
                ChangeRecord.of("A", "f", "y", "z", ChangeType.UPDATE)
            ), 200_000);
            TrackingStatistics.PerformanceStatistics perf = stats.getPerformanceStatistics();
            assertThat(perf.minMicros).isLessThanOrEqualTo(perf.maxMicros);
        }

        @Test
        @DisplayName("reset clears all")
        void reset() {
            TrackingStatistics stats = new TrackingStatistics();
            stats.recordObjectTracked("A");
            stats.recordChanges(List.of(
                ChangeRecord.of("A", "f", "x", "y", ChangeType.UPDATE)
            ), 1000);
            stats.reset();
            assertThat(stats.getSummary().totalObjectsTracked).isZero();
            assertThat(stats.getSummary().totalChangesDetected).isZero();
        }

        @Test
        @DisplayName("format produces readable output")
        void format() {
            TrackingStatistics stats = new TrackingStatistics();
            stats.recordObjectTracked("Order");
            stats.recordChanges(List.of(
                ChangeRecord.of("Order", "status", "NEW", "DONE", ChangeType.UPDATE)
            ), 1000);
            String formatted = stats.format();
            assertThat(formatted).contains("Tracking Statistics");
            assertThat(formatted).contains("Objects Tracked");
            assertThat(formatted).contains("Order");
        }

        @Test
        @DisplayName("ObjectStatistics recordChange and getFieldChangeCounts")
        void objectStatistics_recordChange() {
            TrackingStatistics.ObjectStatistics objStats =
                new TrackingStatistics.ObjectStatistics("Order");
            objStats.recordChange(ChangeRecord.of("Order", "status", "NEW", "DONE", ChangeType.UPDATE));
            objStats.recordChange(ChangeRecord.of("Order", "status", "DONE", "CANCEL", ChangeType.UPDATE));
            objStats.recordChange(ChangeRecord.of("Order", "amount", 10, 20, ChangeType.UPDATE));
            assertThat(objStats.getTotalChanges()).isEqualTo(3);
            Map<String, Integer> fieldCounts = objStats.getFieldChangeCounts();
            assertThat(fieldCounts.get("status")).isEqualTo(2);
            assertThat(fieldCounts.get("amount")).isEqualTo(1);
        }
    }

    // ── DiffBuilder ──

    @Nested
    @DisplayName("DiffBuilder — Builder and compare")
    class DiffBuilderTests {

        @Test
        @DisplayName("create returns new builder")
        void create() {
            DiffBuilder b = DiffBuilder.create();
            assertThat(b).isNotNull();
        }

        @Test
        @DisplayName("fromSpring with null env")
        void fromSpring_nullEnv() {
            DiffBuilder b = DiffBuilder.fromSpring(null);
            assertThat(b).isNotNull();
        }

        @Test
        @DisplayName("fromSpring with StandardEnvironment")
        void fromSpring_standardEnv() {
            StandardEnvironment env = new StandardEnvironment();
            DiffBuilder b = DiffBuilder.fromSpring(env);
            assertThat(b).isNotNull();
        }

        @Test
        @DisplayName("withMaxDepth")
        void withMaxDepth() {
            TfiContext ctx = DiffBuilder.create()
                .withMaxDepth(5)
                .build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withMaxDepth 0 or negative ignored")
        void withMaxDepth_invalid() {
            TfiContext ctx = DiffBuilder.create()
                .withMaxDepth(0)
                .build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withDeepCompare")
        void withDeepCompare() {
            TfiContext ctx = DiffBuilder.create()
                .withDeepCompare(true)
                .build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withExcludePatterns")
        void withExcludePatterns() {
            TfiContext ctx = DiffBuilder.create()
                .withExcludePatterns("*.secret", "internal.*")
                .build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withExcludePatterns null/empty ignored")
        void withExcludePatterns_nullOrEmpty() {
            TfiContext ctx = DiffBuilder.create()
                .withExcludePatterns()
                .build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withPropertyComparator")
        void withPropertyComparator() {
            PropertyComparator comp = (a, b, f) -> true;
            TfiContext ctx = DiffBuilder.create()
                .withPropertyComparator("order.amount", comp)
                .build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withPropertyComparator null path ignored")
        void withPropertyComparator_nullPath() {
            PropertyComparator comp = (a, b, f) -> true;
            TfiContext ctx = DiffBuilder.create()
                .withPropertyComparator(null, comp)
                .build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("build and compare deep")
        void buildAndCompare() {
            TfiContext ctx = DiffBuilder.create()
                .withDeepCompare(true)
                .withMaxDepth(5)
                .build();
            Map<String, Object> a = Map.of("name", "Alice", "age", 30);
            Map<String, Object> b = Map.of("name", "Bob", "age", 30);
            CompareResult result = ctx.compare(a, b);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("compare with custom options")
        void compare_withCustomOptions() {
            TfiContext ctx = DiffBuilder.create().build();
            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult result = ctx.compare("a", "b", opts);
            assertThat(result).isNotNull();
        }
    }
}
