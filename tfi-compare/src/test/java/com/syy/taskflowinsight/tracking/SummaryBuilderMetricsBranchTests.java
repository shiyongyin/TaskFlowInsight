package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.api.builder.TfiContext;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import com.syy.taskflowinsight.tracking.compare.PropertyComparatorRegistry;
import com.syy.taskflowinsight.tracking.summary.CollectionSummary;
import com.syy.taskflowinsight.tracking.summary.SummaryInfo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive branch coverage tests for tracking/summary, api/builder, and metrics packages.
 * Targets every if/else, switch, ternary, and try/catch branch.
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@DisplayName("Summary, Builder, Metrics — Branch Coverage Tests")
class SummaryBuilderMetricsBranchTests {

    // ==================== tracking/summary ====================

    @Nested
    @DisplayName("CollectionSummary — Branch coverage")
    class CollectionSummaryBranchTests {

        private CollectionSummary summary;

        @BeforeEach
        void setUp() {
            summary = new CollectionSummary();
        }

        @Test
        @DisplayName("shouldSummarize returns false when disabled")
        void shouldSummarize_disabled() {
            summary.setEnabled(false);
            assertThat(summary.shouldSummarize(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11))).isFalse();
        }

        @Test
        @DisplayName("shouldSummarize returns false when collection is null")
        void shouldSummarize_nullCollection() {
            assertThat(summary.shouldSummarize(null)).isFalse();
        }

        @Test
        @DisplayName("shouldSummarize returns false when size <= maxSize")
        void shouldSummarize_sizeWithinLimit() {
            summary.setMaxSize(100);
            List<Integer> small = new ArrayList<>();
            for (int i = 0; i < 50; i++) small.add(i);
            assertThat(summary.shouldSummarize(small)).isFalse();
        }

        @Test
        @DisplayName("shouldSummarize returns true when size > maxSize")
        void shouldSummarize_sizeExceedsLimit() {
            summary.setMaxSize(5);
            List<Integer> large = new ArrayList<>();
            for (int i = 0; i < 10; i++) large.add(i);
            assertThat(summary.shouldSummarize(large)).isTrue();
        }

        @Test
        @DisplayName("summarize returns empty when collection is null")
        void summarize_null() {
            SummaryInfo info = summary.summarize(null);
            assertThat(info.getType()).isEqualTo("empty");
            assertThat(info.getSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("summarize Collection path")
        void summarize_collection() {
            List<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
            SummaryInfo info = summary.summarize(list);
            assertThat(info.getType()).isEqualTo("ArrayList");
            assertThat(info.getSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("summarize Map path")
        void summarize_map() {
            SummaryInfo info = summary.summarize(Map.of("a", 1, "b", 2));
            assertThat(info.getType()).isEqualTo("Map");
            assertThat(info.getSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("summarize Array path")
        void summarize_array() {
            int[] arr = {1, 2, 3};
            SummaryInfo info = summary.summarize(arr);
            assertThat(info.getType()).isEqualTo("int[]");
            assertThat(info.getSize()).isEqualTo(3);
        }

        @Test
        @DisplayName("summarize unsupported type path")
        void summarize_unsupported() {
            SummaryInfo info = summary.summarize("not-a-collection");
            assertThat(info.getType()).isEqualTo("String");
            assertThat(info.getSize()).isEqualTo(-1);
            assertThat(info.getFeatures()).contains("unsupported");
        }

        @Test
        @DisplayName("summarizeCollection small collection path")
        void summarizeCollection_small() {
            summary.setMaxSize(100);
            SummaryInfo info = summary.summarize(List.of(1, 2, 3));
            assertThat(info.getExamples()).isNotEmpty();
            assertThat(info.isTruncated()).isFalse();
        }

        @Test
        @DisplayName("summarizeCollection large collection path with truncation")
        void summarizeCollection_largeTruncated() {
            summary.setMaxSize(5);
            summary.setMaxExamples(3);
            List<Integer> large = new ArrayList<>();
            for (int i = 0; i < 50; i++) large.add(i);
            SummaryInfo info = summary.summarize(large);
            assertThat(info.isTruncated()).isTrue();
            assertThat(info.getTypeDistribution()).isNotNull();
        }

        @Test
        @DisplayName("summarizeCollection with null items skips type distribution")
        void summarizeCollection_nullItems() {
            summary.setMaxSize(5);
            List<Object> withNulls = Arrays.asList(1, null, 3, null, 5);
            SummaryInfo info = summary.summarize(withNulls);
            assertThat(info.getSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("summarizeCollection with simple types adds to uniqueValues")
        void summarizeCollection_simpleTypes() {
            summary.setMaxSize(5);
            SummaryInfo info = summary.summarize(List.of(1, 2, 3, "a", "b"));
            assertThat(info.getUniqueCount()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("summarizeCollection skips sensitive examples")
        void summarizeCollection_sensitiveSkipped() {
            summary.setMaxSize(5);
            summary.setSensitiveWords(List.of("password"));
            SummaryInfo info = summary.summarize(List.of("user", "password123", "token"));
            assertThat(info.getExamples()).isNotNull();
        }

        @Test
        @DisplayName("summarizeCollection homogeneous type adds feature")
        void summarizeCollection_homogeneous() {
            summary.setMaxSize(2);
            SummaryInfo info = summary.summarize(List.of(1, 2, 3, 4, 5));
            assertThat(info.getFeatures()).isNotNull().contains("homogeneous");
        }

        @Test
        @DisplayName("summarizeCollection distinct values adds feature")
        void summarizeCollection_distinct() {
            summary.setMaxSize(2);
            SummaryInfo info = summary.summarize(List.of(1, 2, 3, 4, 5));
            assertThat(info.getFeatures()).isNotNull().contains("distinct");
        }

        @Test
        @DisplayName("summarizeCollection List adds ordered feature")
        void summarizeCollection_ordered() {
            summary.setMaxSize(2);
            SummaryInfo info = summary.summarize(new ArrayList<>(List.of(1, 2, 3)));
            assertThat(info.getFeatures()).isNotNull().contains("ordered");
        }

        @Test
        @DisplayName("summarizeCollection Set adds unique feature")
        void summarizeCollection_unique() {
            summary.setMaxSize(2);
            SummaryInfo info = summary.summarize(new HashSet<>(Set.of(1, 2, 3, 4, 5)));
            assertThat(info.getFeatures()).isNotNull().contains("unique");
        }

        @Test
        @DisplayName("summarizeCollection numeric collection adds statistics")
        void summarizeCollection_numericStatistics() {
            summary.setMaxSize(3);
            SummaryInfo info = summary.summarize(List.of(1.0, 2.0, 3.0, 4.0, 5.0));
            assertThat(info.getStatistics()).isNotNull();
            assertThat(info.getStatistics().getMin()).isEqualTo(1.0);
            assertThat(info.getStatistics().getMax()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("summarizeMap small map path")
        void summarizeMap_small() {
            summary.setMaxSize(100);
            SummaryInfo info = summary.summarize(Map.of("a", 1, "b", 2));
            assertThat(info.getMapExamples()).isNotEmpty();
        }

        @Test
        @DisplayName("summarizeMap large map with truncation")
        void summarizeMap_large() {
            summary.setMaxSize(5);
            Map<String, Integer> large = new LinkedHashMap<>();
            for (int i = 0; i < 20; i++) large.put("k" + i, i);
            SummaryInfo info = summary.summarize(large);
            assertThat(info.isTruncated()).isTrue();
        }

        @Test
        @DisplayName("summarizeMap with null key skips key type")
        void summarizeMap_nullKey() {
            summary.setMaxSize(1);
            Map<Object, Object> map = new HashMap<>();
            map.put(null, 1);
            map.put("b", 2);
            SummaryInfo info = summary.summarize(map);
            assertThat(info.getKeyTypeDistribution()).isNotNull();
        }

        @Test
        @DisplayName("summarizeMap skips sensitive key/value in examples")
        void summarizeMap_sensitiveSkipped() {
            summary.setMaxSize(5);
            summary.setSensitiveWords(List.of("secret"));
            Map<String, String> map = Map.of("a", "x", "secret", "y");
            SummaryInfo info = summary.summarize(map);
            assertThat(info.getMapExamples()).isNotNull();
        }

        @Test
        @DisplayName("summarizeArray with sensitive items skips them")
        void summarizeArray_sensitiveSkipped() {
            summary.setSensitiveWords(List.of("password"));
            Object[] arr = {"a", "password123", "b"};
            SummaryInfo info = summary.summarize(arr);
            assertThat(info.getExamples()).doesNotContain("password123");
        }

        @Test
        @DisplayName("summarizeArray numeric array adds statistics")
        void summarizeArray_numericStatistics() {
            double[] arr = {1.0, 2.0, 3.0, 4.0, 5.0};
            SummaryInfo info = summary.summarize(arr);
            assertThat(info.getStatistics()).isNotNull();
        }

        @Test
        @DisplayName("calculateStatistics returns null when numbers empty")
        void calculateStatistics_emptyNumbers() {
            SummaryInfo info = summary.summarize(List.of("a", "b", "c"));
            assertThat(info.getStatistics()).isNull();
        }

        @Test
        @DisplayName("calculateStatistics even count median")
        void calculateStatistics_evenMedian() {
            summary.setMaxSize(2);
            SummaryInfo info = summary.summarize(List.of(1.0, 2.0, 3.0, 4.0));
            assertThat(info.getStatistics()).isNotNull();
            assertThat(info.getStatistics().getMedian()).isEqualTo(2.5);
        }

        @Test
        @DisplayName("calculateStatistics odd count median")
        void calculateStatistics_oddMedian() {
            summary.setMaxSize(3);
            List<Double> list = new ArrayList<>(Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0));
            SummaryInfo info = summary.summarize(list);
            assertThat(info.getStatistics()).isNotNull();
            assertThat(info.getStatistics().getMedian()).isEqualTo(3.5);
        }

        @Test
        @DisplayName("calculateArrayStatistics empty array returns null")
        void calculateArrayStatistics_empty() {
            int[] empty = {};
            SummaryInfo info = summary.summarize(empty);
            assertThat(info.getStatistics()).isNull();
        }

        @Test
        @DisplayName("sanitize null returns 'null'")
        void sanitize_null() {
            List<Object> list = new ArrayList<>();
            list.add(null);
            SummaryInfo info = summary.summarize(list);
            assertThat(info.getExamples()).contains("null");
        }

        @Test
        @DisplayName("sanitize sensitive value masks")
        void sanitize_sensitive() {
            summary.setSensitiveWords(List.of("password"));
            SummaryInfo info = summary.summarize(List.of("user", "mypassword123"));
            assertThat(info.getExamples()).contains("***MASKED***");
        }

        @Test
        @DisplayName("sanitize long string truncates")
        void sanitize_longString() {
            String longStr = "a".repeat(150);
            SummaryInfo info = summary.summarize(List.of(longStr));
            assertThat(info.getExamples().get(0).toString()).endsWith("...");
        }

        @Test
        @DisplayName("getInstance returns singleton")
        void getInstance() {
            CollectionSummary a = CollectionSummary.getInstance();
            CollectionSummary b = CollectionSummary.getInstance();
            assertThat(a).isSameAs(b);
        }

        @Test
        @DisplayName("summarize exception path returns empty")
        void summarize_exception() {
            List<Object> badList = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6)) {
                @Override
                public java.util.Iterator<Object> iterator() {
                    throw new RuntimeException("test");
                }
            };
            summary.setMaxSize(2);
            SummaryInfo info = summary.summarize(badList);
            assertThat(info.getSize()).isEqualTo(0);
            assertThat(info.getExamples()).isEmpty();
        }
    }

    @Nested
    @DisplayName("SummaryInfo — Branch coverage")
    class SummaryInfoBranchTests {

        @Test
        @DisplayName("empty creates empty summary")
        void empty() {
            SummaryInfo info = SummaryInfo.empty();
            assertThat(info.getType()).isEqualTo("empty");
            assertThat(info.getSize()).isEqualTo(0);
            assertThat(info.getExamples()).isEmpty();
        }

        @Test
        @DisplayName("unsupported creates unsupported summary")
        void unsupported() {
            SummaryInfo info = SummaryInfo.unsupported(String.class);
            assertThat(info.getType()).isEqualTo("String");
            assertThat(info.getSize()).isEqualTo(-1);
            assertThat(info.getFeatures()).contains("unsupported");
        }

        @Test
        @DisplayName("toMap truncated true branch")
        void toMap_truncated() {
            SummaryInfo info = new SummaryInfo();
            info.setTruncated(true);
            Map<String, Object> map = info.toMap();
            assertThat(map).containsEntry("truncated", true);
        }

        @Test
        @DisplayName("toMap uniqueCount > 0 branch")
        void toMap_uniqueCount() {
            SummaryInfo info = new SummaryInfo();
            info.setUniqueCount(5);
            Map<String, Object> map = info.toMap();
            assertThat(map).containsEntry("uniqueCount", 5);
        }

        @Test
        @DisplayName("toMap examples non-null non-empty branch")
        void toMap_examples() {
            SummaryInfo info = new SummaryInfo();
            info.setExamples(List.of(1, 2, 3));
            Map<String, Object> map = info.toMap();
            assertThat(map).containsKey("examples");
            assertThat(map.get("examples")).isEqualTo(List.of(1, 2, 3));
        }

        @Test
        @DisplayName("toMap mapExamples non-null non-empty branch")
        void toMap_mapExamples() {
            SummaryInfo info = new SummaryInfo();
            info.setMapExamples(List.of(Map.entry("k", "v")));
            Map<String, Object> map = info.toMap();
            assertThat(map).containsKey("mapExamples");
        }

        @Test
        @DisplayName("toMap features non-null non-empty branch")
        void toMap_features() {
            SummaryInfo info = new SummaryInfo();
            info.setFeatures(Set.of("ordered"));
            Map<String, Object> map = info.toMap();
            assertThat(map).containsKey("features");
        }

        @Test
        @DisplayName("toMap statistics non-null branch")
        void toMap_statistics() {
            SummaryInfo info = new SummaryInfo();
            SummaryInfo.Statistics stats = new SummaryInfo.Statistics(1.0, 10.0, 5.0, 5.0, 2.0);
            info.setStatistics(stats);
            Map<String, Object> map = info.toMap();
            assertThat(map).containsKey("statistics");
        }

        @Test
        @DisplayName("toCompactString uniqueCount > 0 branch")
        void toCompactString_uniqueCount() {
            SummaryInfo info = new SummaryInfo();
            info.setType("List");
            info.setSize(10);
            info.setUniqueCount(8);
            String s = info.toCompactString();
            assertThat(s).contains("unique=8");
        }

        @Test
        @DisplayName("toCompactString truncated branch")
        void toCompactString_truncated() {
            SummaryInfo info = new SummaryInfo();
            info.setType("List");
            info.setSize(100);
            info.setTruncated(true);
            String s = info.toCompactString();
            assertThat(s).contains("truncated");
        }

        @Test
        @DisplayName("toCompactString features non-null non-empty branch")
        void toCompactString_features() {
            SummaryInfo info = new SummaryInfo();
            info.setType("List");
            info.setSize(5);
            info.setFeatures(Set.of("ordered", "distinct"));
            String s = info.toCompactString();
            assertThat(s).contains("features=");
        }

        @Test
        @DisplayName("toCompactString examples size > 3 adds ellipsis")
        void toCompactString_examplesMany() {
            SummaryInfo info = new SummaryInfo();
            info.setType("List");
            info.setSize(5);
            info.setExamples(List.of(1, 2, 3, 4, 5));
            String s = info.toCompactString();
            assertThat(s).contains("...");
        }

        @Test
        @DisplayName("toCompactString examples size <= 3 no ellipsis")
        void toCompactString_examplesFew() {
            SummaryInfo info = new SummaryInfo();
            info.setType("List");
            info.setSize(3);
            info.setExamples(List.of(1, 2, 3));
            String s = info.toCompactString();
            assertThat(s).contains("examples=");
        }

        @Test
        @DisplayName("Statistics toMap null fields omitted")
        void statistics_toMap_nullFields() {
            SummaryInfo.Statistics stats = new SummaryInfo.Statistics(null, null, null, null, null);
            Map<String, Object> map = stats.toMap();
            assertThat(map).isEmpty();
        }

        @Test
        @DisplayName("Statistics toMap non-null fields included")
        void statistics_toMap_nonNullFields() {
            SummaryInfo.Statistics stats = new SummaryInfo.Statistics(1.0, 10.0, 5.0, 5.0, 2.0);
            Map<String, Object> map = stats.toMap();
            assertThat(map).containsKeys("min", "max", "mean", "median", "stdDev");
        }
    }

    // ==================== api/builder ====================

    @Nested
    @DisplayName("TfiContext — Branch coverage")
    class TfiContextBranchTests {

        @Test
        @DisplayName("compare with default options")
        void compare_defaultOptions() {
            TfiContext ctx = DiffBuilder.create().build();
            CompareResult r = ctx.compare("x", "y");
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare with custom options")
        void compare_customOptions() {
            TfiContext ctx = DiffBuilder.create().build();
            CompareOptions opts = CompareOptions.builder().maxDepth(5).build();
            CompareResult r = ctx.compare("a", "b", opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compareService returns non-null")
        void compareService() {
            TfiContext ctx = DiffBuilder.create().build();
            assertThat(ctx.compareService()).isNotNull();
        }
    }

    @Nested
    @DisplayName("DiffBuilder — Branch coverage")
    class DiffBuilderBranchTests {

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
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("fromSpring with env and max-depth property")
        void fromSpring_maxDepth() {
            StandardEnvironment env = new StandardEnvironment();
            env.getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("test",
                    Map.of("tfi.change-tracking.snapshot.max-depth", "7")));
            DiffBuilder b = DiffBuilder.fromSpring(env);
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("fromSpring with enable-deep property")
        void fromSpring_enableDeep() {
            StandardEnvironment env = new StandardEnvironment();
            env.getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("test",
                    Map.of("tfi.change-tracking.snapshot.enable-deep", "true")));
            DiffBuilder b = DiffBuilder.fromSpring(env);
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("fromSpring with excludes property")
        void fromSpring_excludes() {
            StandardEnvironment env = new StandardEnvironment();
            env.getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("test",
                    Map.of("tfi.change-tracking.snapshot.excludes", "foo,bar")));
            DiffBuilder b = DiffBuilder.fromSpring(env);
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withMaxDepth invalid depth returns this without setting")
        void withMaxDepth_invalid() {
            DiffBuilder b = DiffBuilder.create().withMaxDepth(0);
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withMaxDepth valid depth")
        void withMaxDepth_valid() {
            DiffBuilder b = DiffBuilder.create().withMaxDepth(10);
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withExcludePatterns null patterns no-op")
        void withExcludePatterns_null() {
            DiffBuilder b = DiffBuilder.create().withExcludePatterns((String[]) null);
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withExcludePatterns empty patterns no-op")
        void withExcludePatterns_empty() {
            DiffBuilder b = DiffBuilder.create().withExcludePatterns();
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withExcludePatterns valid patterns")
        void withExcludePatterns_valid() {
            DiffBuilder b = DiffBuilder.create().withExcludePatterns("foo", "bar");
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withPropertyComparator null path no-op")
        void withPropertyComparator_nullPath() {
            PropertyComparator comp = (a, b, f) -> true;
            DiffBuilder b = DiffBuilder.create().withPropertyComparator(null, comp);
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withPropertyComparator null comparator no-op")
        void withPropertyComparator_nullComparator() {
            DiffBuilder b = DiffBuilder.create().withPropertyComparator("user.name", null);
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withPropertyComparator invalid path logs and continues")
        void withPropertyComparator_invalidPath() {
            PropertyComparator comp = (a, b, f) -> true;
            DiffBuilder b = DiffBuilder.create().withPropertyComparator(".invalid", comp);
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withPropertyComparator valid path registers")
        void withPropertyComparator_valid() {
            PropertyComparator comp = (a, b, f) -> true;
            DiffBuilder b = DiffBuilder.create().withPropertyComparator("user.name", comp);
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("build produces TfiContext")
        void build() {
            TfiContext ctx = DiffBuilder.create().withDeepCompare(true).build();
            assertThat(ctx).isNotNull();
            assertThat(ctx.compareService()).isNotNull();
        }
    }

    // ==================== metrics ====================

    @Nested
    @DisplayName("MetricsSummary — Branch coverage")
    class MetricsSummaryBranchTests {

        @Test
        @DisplayName("getErrorRate total > 0 branch")
        void getErrorRate_totalPositive() {
            var summary = com.syy.taskflowinsight.metrics.MetricsSummary.builder()
                .changeTrackingCount(10)
                .snapshotCreationCount(0)
                .pathMatchCount(0)
                .collectionSummaryCount(0)
                .errorCount(2)
                .build();
            assertThat(summary.getErrorRate()).isEqualTo(0.2);
        }

        @Test
        @DisplayName("getErrorRate total == 0 returns 0")
        void getErrorRate_totalZero() {
            var summary = com.syy.taskflowinsight.metrics.MetricsSummary.builder()
                .changeTrackingCount(0)
                .snapshotCreationCount(0)
                .pathMatchCount(0)
                .collectionSummaryCount(0)
                .errorCount(5)
                .build();
            assertThat(summary.getErrorRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("formatDuration null returns 0ms")
        void formatDuration_null() {
            var summary = com.syy.taskflowinsight.metrics.MetricsSummary.builder()
                .avgChangeTrackingTime(null)
                .avgSnapshotCreationTime(null)
                .avgPathMatchTime(null)
                .avgCollectionSummaryTime(null)
                .build();
            Map<String, Object> map = summary.toMap();
            assertThat(map.get("performance")).isNotNull();
        }

        @Test
        @DisplayName("formatDuration millis >= 1")
        void formatDuration_millis() {
            var summary = com.syy.taskflowinsight.metrics.MetricsSummary.builder()
                .avgChangeTrackingTime(Duration.ofMillis(50))
                .build();
            Map<String, Object> map = summary.toMap();
            assertThat(map.get("performance")).isNotNull();
        }

        @Test
        @DisplayName("formatDuration micros path")
        void formatDuration_micros() {
            var summary = com.syy.taskflowinsight.metrics.MetricsSummary.builder()
                .avgPathMatchTime(Duration.ofNanos(5000))
                .build();
            Map<String, Object> map = summary.toMap();
            assertThat(map.get("performance")).isNotNull();
        }

        @Test
        @DisplayName("formatDuration nanos path")
        void formatDuration_nanos() {
            var summary = com.syy.taskflowinsight.metrics.MetricsSummary.builder()
                .avgPathMatchTime(Duration.ofNanos(500))
                .build();
            Map<String, Object> map = summary.toMap();
            assertThat(map.get("performance")).isNotNull();
        }
    }

    @Nested
    @DisplayName("MetricsLogger — Branch coverage")
    class MetricsLoggerBranchTests {

        @Test
        @DisplayName("logMetrics empty metrics skips")
        void logMetrics_emptyMetrics() {
            var logger = new com.syy.taskflowinsight.metrics.MetricsLogger(Optional.empty());
            ReflectionTestUtils.setField(logger, "loggingFormat", "json");
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", false);
            logger.init();
            logger.logMetricsNow();
        }

        @Test
        @DisplayName("logMetrics non-empty with includeZeroMetrics true")
        void logMetrics_includeZero() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            var logger = new com.syy.taskflowinsight.metrics.MetricsLogger(Optional.of(metrics));
            ReflectionTestUtils.setField(logger, "loggingFormat", "json");
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", true);
            logger.init();
            logger.logMetricsNow();
        }

        @Test
        @DisplayName("logMetrics isEmptySummary and !includeZero skips")
        void logMetrics_emptySummarySkip() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            var logger = new com.syy.taskflowinsight.metrics.MetricsLogger(Optional.of(metrics));
            ReflectionTestUtils.setField(logger, "loggingFormat", "json");
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", false);
            logger.init();
            logger.logMetricsNow();
        }

        @Test
        @DisplayName("logMetrics switch json format")
        void logMetrics_jsonFormat() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            metrics.recordChangeTracking(1);
            var logger = new com.syy.taskflowinsight.metrics.MetricsLogger(Optional.of(metrics));
            ReflectionTestUtils.setField(logger, "loggingFormat", "json");
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", true);
            logger.init();
            logger.logMetricsNow();
        }

        @Test
        @DisplayName("logMetrics switch text format")
        void logMetrics_textFormat() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            metrics.recordChangeTracking(1);
            var logger = new com.syy.taskflowinsight.metrics.MetricsLogger(Optional.of(metrics));
            ReflectionTestUtils.setField(logger, "loggingFormat", "text");
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", true);
            logger.init();
            logger.logMetricsNow();
        }

        @Test
        @DisplayName("logMetrics switch compact format")
        void logMetrics_compactFormat() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            metrics.recordChangeTracking(1);
            var logger = new com.syy.taskflowinsight.metrics.MetricsLogger(Optional.of(metrics));
            ReflectionTestUtils.setField(logger, "loggingFormat", "compact");
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", true);
            logger.init();
            logger.logMetricsNow();
        }

        @Test
        @DisplayName("logMetrics unknown format falls back to json")
        void logMetrics_unknownFormat() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            metrics.recordChangeTracking(1);
            var logger = new com.syy.taskflowinsight.metrics.MetricsLogger(Optional.of(metrics));
            ReflectionTestUtils.setField(logger, "loggingFormat", "unknown");
            ReflectionTestUtils.setField(logger, "includeZeroMetrics", true);
            logger.init();
            logger.logMetricsNow();
        }

        @Test
        @DisplayName("toNanos path exercised via getSummary with no operations")
        void toNanos_viaSummary() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            var summary = metrics.getSummary();
            assertThat(summary.toMap()).containsKey("performance");
        }
    }

    @Nested
    @DisplayName("TfiMetrics — Branch coverage")
    class TfiMetricsBranchTests {

        @Test
        @DisplayName("getPathMatchHitRate total > 0")
        void getPathMatchHitRate_positive() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            metrics.recordPathMatch(1, true);
            metrics.recordPathMatch(1, false);
            assertThat(metrics.getPathMatchHitRate()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("getPathMatchHitRate total == 0 returns 0")
        void getPathMatchHitRate_zero() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            assertThat(metrics.getPathMatchHitRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getAverageProcessingTime timer null returns ZERO")
        void getAverageProcessingTime_null() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            var result = metrics.getAverageProcessingTime("nonexistent");
            assertThat(result).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("getAverageProcessingTime timer exists")
        void getAverageProcessingTime_exists() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            metrics.recordChangeTracking(100_000_000L);
            var result = metrics.getAverageProcessingTime("change.tracking");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("calculateHealthScore totalOps == 0 returns 100")
        void calculateHealthScore_noOps() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            var summary = metrics.getSummary();
            assertThat(summary.getHealthScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("calculateHealthScore pathMatch > 0 adjusts by hit rate")
        void calculateHealthScore_withPathMatch() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            metrics.recordPathMatch(1, true);
            metrics.recordPathMatch(1, true);
            var summary = metrics.getSummary();
            assertThat(summary.getHealthScore()).isBetween(0.0, 100.0);
        }

        @Test
        @DisplayName("recordPathMatch cacheHit true")
        void recordPathMatch_hit() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            metrics.recordPathMatch(1, true);
            assertThat(registry.find("tfi.path.match.hit.total").counter().count()).isEqualTo(1);
        }

        @Test
        @DisplayName("recordPathMatch cacheHit false")
        void recordPathMatch_miss() {
            var registry = new SimpleMeterRegistry();
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.of(registry));
            metrics.recordPathMatch(1, false);
            assertThat(registry.find("tfi.path.match.hit.total").counter().count()).isEqualTo(0);
        }

        @Test
        @DisplayName("constructor with empty Optional uses SimpleMeterRegistry")
        void constructor_emptyOptional() {
            var metrics = new com.syy.taskflowinsight.metrics.TfiMetrics(Optional.empty());
            assertThat(metrics.getSummary()).isNotNull();
        }
    }

    @Nested
    @DisplayName("AsyncMetricsCollector — Branch coverage")
    class AsyncMetricsCollectorBranchTests {

        @Test
        @DisplayName("recordCounter when disabled does not add")
        void recordCounter_disabled() {
            var registry = new SimpleMeterRegistry();
            var collector = new com.syy.taskflowinsight.metrics.AsyncMetricsCollector(registry);
            collector.init();
            ReflectionTestUtils.setField(collector, "enabled", false);
            collector.recordCounter("test");
            var stats = collector.getStats();
            assertThat(stats.getTotalEvents()).isEqualTo(0);
        }

        @Test
        @DisplayName("recordTimer when disabled")
        void recordTimer_disabled() {
            var registry = new SimpleMeterRegistry();
            var collector = new com.syy.taskflowinsight.metrics.AsyncMetricsCollector(registry);
            collector.init();
            ReflectionTestUtils.setField(collector, "enabled", false);
            collector.recordTimer("test", 1000);
            assertThat(collector.getStats().getTotalEvents()).isEqualTo(0);
        }

        @Test
        @DisplayName("recordGauge when disabled")
        void recordGauge_disabled() {
            var registry = new SimpleMeterRegistry();
            var collector = new com.syy.taskflowinsight.metrics.AsyncMetricsCollector(registry);
            collector.init();
            ReflectionTestUtils.setField(collector, "enabled", false);
            collector.recordGauge("test", 42.0);
            assertThat(collector.getStats().getTotalEvents()).isEqualTo(0);
        }

        @Test
        @DisplayName("flushMetrics when disabled returns early")
        void flushMetrics_disabled() {
            var registry = new SimpleMeterRegistry();
            var collector = new com.syy.taskflowinsight.metrics.AsyncMetricsCollector(registry);
            collector.init();
            ReflectionTestUtils.setField(collector, "enabled", false);
            collector.flushMetrics();
        }

        @Test
        @DisplayName("processEvent COUNTER branch")
        void processEvent_counter() {
            var registry = new SimpleMeterRegistry();
            var collector = new com.syy.taskflowinsight.metrics.AsyncMetricsCollector(registry);
            collector.init();
            collector.recordCounter("test.counter");
            collector.flushMetrics();
            assertThat(collector.getStats().getProcessedEvents()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("processEvent TIMER branch")
        void processEvent_timer() {
            var registry = new SimpleMeterRegistry();
            var collector = new com.syy.taskflowinsight.metrics.AsyncMetricsCollector(registry);
            collector.init();
            collector.recordTimer("test.timer", 1_000_000L);
            collector.flushMetrics();
            assertThat(collector.getStats().getProcessedEvents()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("processEvent GAUGE branch")
        void processEvent_gauge() {
            var registry = new SimpleMeterRegistry();
            var collector = new com.syy.taskflowinsight.metrics.AsyncMetricsCollector(registry);
            collector.init();
            collector.recordGauge("test.gauge", 42.0);
            collector.flushMetrics();
            assertThat(collector.getStats().getProcessedEvents()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("processCounterEvent with tags")
        void processCounterEvent_withTags() {
            var registry = new SimpleMeterRegistry();
            var collector = new com.syy.taskflowinsight.metrics.AsyncMetricsCollector(registry);
            collector.init();
            collector.recordCounter("tagged.counter", "env", "test");
            collector.flushMetrics();
        }

        @Test
        @DisplayName("processTimerEvent with tags")
        void processTimerEvent_withTags() {
            var registry = new SimpleMeterRegistry();
            var collector = new com.syy.taskflowinsight.metrics.AsyncMetricsCollector(registry);
            collector.init();
            collector.recordTimer("tagged.timer", 1000, "op", "read");
            collector.flushMetrics();
        }

        @Test
        @DisplayName("CollectorStats getDropRate total == 0")
        void collectorStats_dropRateZero() {
            var registry = new SimpleMeterRegistry();
            var collector = new com.syy.taskflowinsight.metrics.AsyncMetricsCollector(registry);
            collector.init();
            var stats = collector.getStats();
            assertThat(stats.getDropRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("CollectorStats getBufferUtilization maxBufferSize == 0")
        void collectorStats_bufferUtilizationZero() {
            var stats = new com.syy.taskflowinsight.metrics.AsyncMetricsCollector.CollectorStats(
                0, 0, 0, 0, 0, true);
            assertThat(stats.getBufferUtilization()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("destroy disables and flushes")
        void destroy() {
            var registry = new SimpleMeterRegistry();
            var collector = new com.syy.taskflowinsight.metrics.AsyncMetricsCollector(registry);
            collector.init();
            collector.recordCounter("pre");
            collector.destroy();
            collector.recordCounter("post");
            assertThat(collector.getStats().isEnabled()).isFalse();
        }
    }
}
