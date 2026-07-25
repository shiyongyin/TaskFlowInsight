package com.syy.taskflowinsight.perf;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.internal.CompareResultReducer;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import com.syy.taskflowinsight.tracking.ChangeType;
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
 * <p>测试场景覆盖 canonical CompareService 与 CompareResult 查询性能基线。
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

            long[] samples = benchmark(() -> svc.compare(a, b, CompareOptions.builder().build()));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[Shallow/SimplePojo] " + stats);
            assertThat(stats.avgMs).isLessThan(1.0);
        }

        @Test
        @DisplayName("Shallow compare of identical objects < 0.5ms avg")
        void shallowCompare_identical() {
            SimplePojo a = new SimplePojo("Bob", 25, "bob@test.com");

            long[] samples = benchmark(() -> svc.compare(a, a, CompareOptions.builder().build()));
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
                
                .build();

            long[] samples = benchmark(() -> svc.compare(map1, map2, opts));
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[Deep/Map(500)] " + stats);
            assertThat(stats.avgMs).isLessThan(20.0);
        }
    }

    // ========== CompareResult Query API Performance ==========

    @Nested
    @DisplayName("CompareResult — Query API Performance")
    class CompareResultQueryPerf {

        @Test
        @DisplayName("groupByObject() with 500 changes < 5ms avg")
        void groupByObject_500changes() {
            List<FieldChange> changes = IntStream.range(0, 500).mapToObj(i ->
                FieldChange.at(ChangeKind.MODIFY, ComparePath.root().append(new PropertySegment("obj" + (i / 50) + ".field" + (i % 10))), "old" + i, "new" + i)
            ).collect(Collectors.toList());

            CompareResult result = CompareResultReducer.complete(changes);

            long[] samples = benchmark(result::groupByObject);
            PerfStats stats = PerfStats.of(samples);

            System.out.println("[CompareResult/groupByObject(500)] " + stats);
            assertThat(stats.avgMs).isLessThan(5.0);
        }

        @Test
        @DisplayName("getChangesByType() with 500 changes < 2ms avg")
        void getChangesByType_500changes() {
            List<FieldChange> changes = IntStream.range(0, 500).mapToObj(i ->
                FieldChange.fromLegacy(i % 3 == 0 ? ChangeType.CREATE : (i % 3 == 1 ? ChangeType.UPDATE : ChangeType.DELETE), ComparePath.root().append(new PropertySegment("field" + i)), "old" + i, "new" + i)
            ).collect(Collectors.toList());

            CompareResult result = CompareResultReducer.complete(changes);

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
            FieldChange.at(ChangeKind.MODIFY, ComparePath.root().append(new PropertySegment("obj" + (i / 20) + ".field" + (i % 20))), "old" + i, "new" + i)
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
