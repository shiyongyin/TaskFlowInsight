package com.syy.taskflowinsight.perf;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.path.PathDeduplicator;
import com.syy.taskflowinsight.tracking.path.PathDeduplicationConfig;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * tfi-compare 模块核心性能测试
 *
 * <p>测试场景覆盖 CompareService、PathDeduplicator、ObjectSnapshotDeep 的性能基线。
 * 通过 {@code -Dtfi.perf.enabled=true} 或 {@code -Pperf} 激活。</p>
 *
 * <h3>运行方式</h3>
 * <pre>{@code
 * # 方式1：Maven profile
 * ./mvnw test -pl tfi-compare -Pperf -Dtest=CompareModulePerformanceTests
 *
 * # 方式2：系统属性
 * ./mvnw test -pl tfi-compare -Dtfi.perf.enabled=true -Dtest=CompareModulePerformanceTests
 * }</pre>
 *
 * <h3>性能指标</h3>
 * <ul>
 *   <li>P50/P95/avg: 采样后排序取中位数/95分位/平均值</li>
 *   <li>通过率: 核心操作须在目标时间内完成（SLA 门禁）</li>
 * </ul>
 *
 * @author Test Expert Panel
 * @since v3.0.0
 */
@DisplayName("tfi-compare Performance Tests")
@EnabledIfSystemProperty(named = "tfi.perf.enabled", matches = "true")
class CompareModulePerformanceTests {

    private static final int WARMUP = 5;
    private static final int SAMPLES = 20;

    // ========== CompareService Performance ==========

    @Nested
    @DisplayName("CompareService — Shallow Comparison Performance")
    class CompareServiceShallowPerf {

        private CompareService svc;

        @BeforeEach
        void setUp() {
            svc = new CompareService();
        }

        @Test
        @DisplayName("Shallow compare of simple POJO < 1ms avg")
        void shallowCompare_simplePojo() {
            SimplePojo a = new SimplePojo("Alice", 30, "alice@test.com");
            SimplePojo b = new SimplePojo("Alice", 31, "alice@updated.com");

            long[] samples = benchmark(() -> svc.compare(a, b, CompareOptions.DEFAULT));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[Shallow/SimplePojo] " + stats);
            assertThat(stats.avgMs).isLessThan(1.0);
        }

        @Test
        @DisplayName("Shallow compare of identical objects < 0.5ms avg")
        void shallowCompare_identical() {
            SimplePojo a = new SimplePojo("Bob", 25, "bob@test.com");

            long[] samples = benchmark(() -> svc.compare(a, a, CompareOptions.DEFAULT));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[Shallow/Identical] " + stats);
            assertThat(stats.avgMs).isLessThan(0.5);
        }
    }

    @Nested
    @DisplayName("CompareService — Deep Comparison Performance")
    class CompareServiceDeepPerf {

        private CompareService svc;

        @BeforeEach
        void setUp() {
            svc = new CompareService();
        }

        @Test
        @DisplayName("Deep compare of nested object (depth=3) < 5ms avg")
        void deepCompare_nested() {
            NestedPojo a = createNestedPojo(3, "v1");
            NestedPojo b = createNestedPojo(3, "v2");

            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .maxDepth(5)
                .build();

            long[] samples = benchmark(() -> svc.compare(a, b, opts));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[Deep/Nested(depth=3)] " + stats);
            assertThat(stats.avgMs).isLessThan(5.0);
        }

        @Test
        @DisplayName("Deep compare of wide object (20 fields) < 5ms avg")
        void deepCompare_wideObject() {
            Map<String, Object> a = new LinkedHashMap<>();
            Map<String, Object> b = new LinkedHashMap<>();
            for (int i = 0; i < 20; i++) {
                a.put("field" + i, "value" + i);
                b.put("field" + i, i < 15 ? "value" + i : "changed" + i);
            }

            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .build();

            long[] samples = benchmark(() -> svc.compare(a, b, opts));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[Deep/WideObject(20 fields)] " + stats);
            assertThat(stats.avgMs).isLessThan(5.0);
        }

        @Test
        @DisplayName("List comparison (1000 elements) < 50ms avg")
        void listCompare_1000() {
            List<String> list1 = IntStream.range(0, 1000)
                .mapToObj(i -> "item-" + i)
                .collect(Collectors.toList());
            List<String> list2 = new ArrayList<>(list1);
            list2.set(500, "changed-500");
            list2.add("new-item");

            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .build();

            long[] samples = benchmark(() -> svc.compare(list1, list2, opts));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[Deep/List(1000)] " + stats);
            assertThat(stats.avgMs).isLessThan(50.0);
        }

        @Test
        @DisplayName("Map comparison (500 entries) < 20ms avg")
        void mapCompare_500() {
            Map<String, String> map1 = new LinkedHashMap<>();
            Map<String, String> map2 = new LinkedHashMap<>();
            for (int i = 0; i < 500; i++) {
                map1.put("key-" + i, "value-" + i);
                map2.put("key-" + i, i < 490 ? "value-" + i : "changed-" + i);
            }

            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .build();

            long[] samples = benchmark(() -> svc.compare(map1, map2, opts));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[Deep/Map(500)] " + stats);
            assertThat(stats.avgMs).isLessThan(20.0);
        }
    }

    // ========== PathDeduplicator Performance ==========

    @Nested
    @DisplayName("PathDeduplicator — Deduplication Performance")
    class PathDeduplicatorPerf {

        @Test
        @DisplayName("Deduplication of 100 change records < 10ms avg")
        void dedup_100records() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            PathDeduplicator dedup = new PathDeduplicator(config);

            List<ChangeRecord> changes = createSyntheticChangeRecords(100);

            long[] samples = benchmark(() -> dedup.deduplicate(changes));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[PathDedup/100 records] " + stats);
            assertThat(stats.avgMs).isLessThan(10.0);
        }

        @Test
        @DisplayName("Deduplication of 1000 change records < 100ms avg")
        void dedup_1000records() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            PathDeduplicator dedup = new PathDeduplicator(config);

            List<ChangeRecord> changes = createSyntheticChangeRecords(1000);

            long[] samples = benchmark(() -> dedup.deduplicate(changes));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[PathDedup/1000 records] " + stats);
            assertThat(stats.avgMs).isLessThan(100.0);
        }
    }

    // ========== ObjectSnapshotDeep Performance ==========

    @Nested
    @DisplayName("ObjectSnapshotDeep — Snapshot Performance")
    class SnapshotDeepPerf {

        @Test
        @DisplayName("Deep snapshot of POJO < 2ms avg")
        void snapshot_simplePojo() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep snapshotter = new ObjectSnapshotDeep(config);
            SimplePojo target = new SimplePojo("Alice", 30, "alice@test.com");

            long[] samples = benchmark(() ->
                snapshotter.captureDeep(target, 3, Collections.emptySet(), Collections.emptySet()));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[Snapshot/SimplePojo] " + stats);
            assertThat(stats.avgMs).isLessThan(2.0);
        }

        @Test
        @DisplayName("Deep snapshot of nested object (depth=3) < 5ms avg")
        void snapshot_nestedPojo() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep snapshotter = new ObjectSnapshotDeep(config);
            NestedPojo target = createNestedPojo(3, "snap");

            long[] samples = benchmark(() ->
                snapshotter.captureDeep(target, 5, Collections.emptySet(), Collections.emptySet()));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[Snapshot/Nested(depth=3)] " + stats);
            assertThat(stats.avgMs).isLessThan(5.0);
        }

        @Test
        @DisplayName("Deep snapshot of List(100) < 10ms avg")
        void snapshot_list100() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep snapshotter = new ObjectSnapshotDeep(config);
            List<String> target = IntStream.range(0, 100)
                .mapToObj(i -> "item-" + i).collect(Collectors.toList());

            long[] samples = benchmark(() ->
                snapshotter.captureDeep(target, 3, Collections.emptySet(), Collections.emptySet()));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[Snapshot/List(100)] " + stats);
            assertThat(stats.avgMs).isLessThan(10.0);
        }
    }

    // ========== CompareResult Query API Performance ==========

    @Nested
    @DisplayName("CompareResult — Query API Performance")
    class CompareResultQueryPerf {

        @Test
        @DisplayName("prettyPrint() with 500 changes < 5ms avg")
        void prettyPrint_500changes() {
            List<FieldChange> changes = IntStream.range(0, 500).mapToObj(i ->
                FieldChange.builder()
                    .fieldName("field" + i)
                    .fieldPath("obj.field" + i)
                    .changeType(i % 3 == 0 ? ChangeType.CREATE : (i % 3 == 1 ? ChangeType.UPDATE : ChangeType.DELETE))
                    .oldValue("old" + i)
                    .newValue("new" + i)
                    .build()
            ).collect(Collectors.toList());

            CompareResult result = CompareResult.builder()
                .changes(changes)
                .identical(false)
                .build();

            long[] samples = benchmark(result::prettyPrint);
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[CompareResult/prettyPrint(500)] " + stats);
            assertThat(stats.avgMs).isLessThan(5.0);
        }

        @Test
        @DisplayName("groupByObject() with 500 changes < 5ms avg")
        void groupByObject_500changes() {
            List<FieldChange> changes = IntStream.range(0, 500).mapToObj(i ->
                FieldChange.builder()
                    .fieldName("field" + (i % 10))
                    .fieldPath("obj" + (i / 50) + ".field" + (i % 10))
                    .changeType(ChangeType.UPDATE)
                    .oldValue("old" + i)
                    .newValue("new" + i)
                    .build()
            ).collect(Collectors.toList());

            CompareResult result = CompareResult.builder()
                .changes(changes)
                .identical(false)
                .build();

            long[] samples = benchmark(result::groupByObject);
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[CompareResult/groupByObject(500)] " + stats);
            assertThat(stats.avgMs).isLessThan(5.0);
        }

        @Test
        @DisplayName("getChangesByType() with 500 changes < 2ms avg")
        void getChangesByType_500changes() {
            List<FieldChange> changes = IntStream.range(0, 500).mapToObj(i ->
                FieldChange.builder()
                    .fieldName("field" + i)
                    .changeType(i % 3 == 0 ? ChangeType.CREATE : (i % 3 == 1 ? ChangeType.UPDATE : ChangeType.DELETE))
                    .oldValue("old" + i)
                    .newValue("new" + i)
                    .build()
            ).collect(Collectors.toList());

            CompareResult result = CompareResult.builder()
                .changes(changes)
                .identical(false)
                .build();

            long[] samples = benchmark(() -> result.getChangesByType(ChangeType.CREATE, ChangeType.DELETE));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[CompareResult/getChangesByType(500)] " + stats);
            assertThat(stats.avgMs).isLessThan(2.0);
        }
    }

    // ========== Benchmark Infrastructure ==========

    /**
     * Run warmup + sampling and return nanosecond timings.
     */
    private long[] benchmark(Runnable task) {
        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            task.run();
        }
        // Sample
        long[] samples = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long start = System.nanoTime();
            task.run();
            samples[i] = System.nanoTime() - start;
        }
        return samples;
    }

    /**
     * Performance statistics from benchmark samples.
     */
    static class PerfStats {
        final double avgMs;
        final double p50Ms;
        final double p95Ms;
        final double minMs;
        final double maxMs;

        PerfStats(double avgMs, double p50Ms, double p95Ms, double minMs, double maxMs) {
            this.avgMs = avgMs;
            this.p50Ms = p50Ms;
            this.p95Ms = p95Ms;
            this.minMs = minMs;
            this.maxMs = maxMs;
        }

        static PerfStats of(long[] nanoSamples) {
            long[] sorted = nanoSamples.clone();
            Arrays.sort(sorted);
            int n = sorted.length;
            double avg = Arrays.stream(sorted).average().orElse(0);
            double p50 = sorted[n / 2];
            double p95 = sorted[(int) (n * 0.95)];
            double min = sorted[0];
            double max = sorted[n - 1];
            return new PerfStats(
                avg / 1_000_000.0,
                p50 / 1_000_000.0,
                p95 / 1_000_000.0,
                min / 1_000_000.0,
                max / 1_000_000.0
            );
        }

        @Override
        public String toString() {
            return String.format("avg=%.3fms p50=%.3fms p95=%.3fms min=%.3fms max=%.3fms",
                avgMs, p50Ms, p95Ms, minMs, maxMs);
        }
    }

    // ========== Test Data Helpers ==========

    private List<FieldChange> createSyntheticChanges(int count) {
        return IntStream.range(0, count).mapToObj(i ->
            FieldChange.builder()
                .fieldName("field" + (i % 20))
                .fieldPath("obj" + (i / 20) + ".field" + (i % 20))
                .changeType(ChangeType.UPDATE)
                .oldValue("old" + i)
                .newValue("new" + i)
                .build()
        ).collect(Collectors.toList());
    }

    private List<ChangeRecord> createSyntheticChangeRecords(int count) {
        return IntStream.range(0, count).mapToObj(i ->
            ChangeRecord.of(
                "obj" + (i / 20),
                "field" + (i % 20),
                "old" + i,
                "new" + i,
                ChangeType.UPDATE
            )
        ).collect(Collectors.toList());
    }

    private static NestedPojo createNestedPojo(int depth, String suffix) {
        NestedPojo current = null;
        for (int d = depth; d >= 0; d--) {
            NestedPojo next = new NestedPojo();
            next.name = "level-" + d + "-" + suffix;
            next.value = d * 100;
            next.child = current;
            next.tags = List.of("tag-" + d + "-a", "tag-" + d + "-b");
            current = next;
        }
        return current;
    }

    // ========== Test POJOs ==========

    static class SimplePojo {
        String name;
        int age;
        String email;

        SimplePojo(String name, int age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }

        public String getName() { return name; }
        public int getAge() { return age; }
        public String getEmail() { return email; }
    }

    static class NestedPojo {
        String name;
        int value;
        NestedPojo child;
        List<String> tags;

        public String getName() { return name; }
        public int getValue() { return value; }
        public NestedPojo getChild() { return child; }
        public List<String> getTags() { return tags; }
    }
}
