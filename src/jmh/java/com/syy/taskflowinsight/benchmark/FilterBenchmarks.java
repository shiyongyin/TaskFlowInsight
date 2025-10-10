package com.syy.taskflowinsight.benchmark;

import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import com.syy.taskflowinsight.tracking.snapshot.filter.PathMatcher;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * P2过滤框架JMH性能基准测试
 *
 * 目标:
 * - 验证大对象快照性能退化 ≤ 3% (p95)
 * - 验证Pattern缓存命中率 > 95%
 *
 * 运行方式:
 * ./mvnw clean test-compile exec:exec@run-benchmarks
 * 或使用JMH运行器直接运行
 *
 * 基准配置:
 * - Warmup: 3 iterations, 1 second each
 * - Measurement: 5 iterations, 2 seconds each
 * - Fork: 1
 * - Mode: Average time (ns/op)
 *
 * @author TaskFlow Insight Team
 * @since 2025-10-09
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Thread)
public class FilterBenchmarks {

    // ========== Benchmark 1: Large Object Filtering ==========

    /**
     * 状态：大对象过滤基准
     * 测试大对象快照 + 典型过滤配置的性能表现
     */
    @State(Scope.Thread)
    public static class LargeObjectFilterState {
        ObjectSnapshotDeep baselineSnapshot;
        ObjectSnapshotDeep filteredSnapshot;
        LargeObjectGenerator.LargeBusinessObject testObject;

        @Setup(Level.Trial)
        public void setup() {
            // Baseline: No filtering
            SnapshotConfig baselineConfig = new SnapshotConfig();
            baselineConfig.setEnableDeep(true);
            baselineConfig.setMaxDepth(3);
            baselineConfig.setIncludePatterns(Collections.emptyList());
            baselineConfig.setExcludePatterns(Collections.emptyList());
            baselineConfig.setDefaultExclusionsEnabled(false);
            baselineSnapshot = new ObjectSnapshotDeep(baselineConfig);

            // With Filtering: Typical production configuration
            SnapshotConfig filteredConfig = new SnapshotConfig();
            filteredConfig.setEnableDeep(true);
            filteredConfig.setMaxDepth(3);
            filteredConfig.setExcludePatterns(List.of(
                "*.password",
                "*.internal.*",
                "*.debug.*"
            ));
            filteredConfig.setDefaultExclusionsEnabled(true);
            filteredSnapshot = new ObjectSnapshotDeep(filteredConfig);

            // Generate test object
            testObject = LargeObjectGenerator.generateLargeObject();
        }

        @TearDown(Level.Trial)
        public void teardown() {
            PathMatcher.clearCache();
            ObjectSnapshotDeep.resetMetrics();
        }
    }

    /**
     * 基准1: 无过滤基线（用于对比）
     * 测试未启用任何过滤规则时的快照性能
     */
    @Benchmark
    public void baseline_NoFiltering(LargeObjectFilterState state, Blackhole blackhole) {
        Map<String, Object> result = state.baselineSnapshot.captureDeep(
            state.testObject,
            3,
            Collections.emptySet(),
            Collections.emptySet()
        );
        blackhole.consume(result);
    }

    /**
     * 基准2: 大对象过滤（关键指标）
     * 测试启用典型过滤配置后的快照性能
     *
     * 性能目标: p95退化 ≤ 3% vs baseline_NoFiltering
     */
    @Benchmark
    public void filterLargeObject(LargeObjectFilterState state, Blackhole blackhole) {
        Map<String, Object> result = state.filteredSnapshot.captureDeep(
            state.testObject,
            3,
            Collections.emptySet(),
            Collections.emptySet()
        );
        blackhole.consume(result);
    }

    // ========== Benchmark 2: Pattern Compilation Cache ==========

    /**
     * 状态：路径匹配缓存基准
     * 测试Pattern编译缓存的命中率和性能
     */
    @State(Scope.Thread)
    public static class PatternCacheState {
        String[] testPaths;
        String[] patterns;

        @Setup(Level.Trial)
        public void setup() {
            // Prepare test paths (simulate real-world field paths)
            testPaths = new String[]{
                "order.orderId",
                "order.items[0].name",
                "order.items[1].price",
                "user.username",
                "user.password",  // Should match *.password
                "internal.token",  // Should match *.internal.*
                "debug.trace"      // Should match *.debug.*
            };

            // Prepare patterns (reused across multiple matches)
            patterns = new String[]{
                "order.*",
                "order.items[*].*",
                "user.*",
                "*.password",
                "*.internal.*",
                "*.debug.*"
            };

            // Clear cache for clean benchmark
            PathMatcher.clearCache();
        }

        @TearDown(Level.Iteration)
        public void teardown() {
            // Don't clear cache between iterations - we want to measure cache hits
        }
    }

    /**
     * 基准3: Pattern缓存性能（关键指标）
     * 测试高频路径匹配场景下的Pattern缓存效果
     *
     * 性能目标:
     * - 缓存命中率 > 95%
     * - 平均匹配时间 < 500ns (with cache)
     */
    @Benchmark
    public void patternCompilationCache(PatternCacheState state, Blackhole blackhole) {
        // Simulate high-frequency matching (pattern reuse)
        int matchCount = 0;
        for (String path : state.testPaths) {
            for (String pattern : state.patterns) {
                boolean matches = PathMatcher.matchGlob(path, pattern);
                if (matches) {
                    matchCount++;
                }
            }
        }
        blackhole.consume(matchCount);
    }

    // ========== Auxiliary: Cache Hit Rate Measurement ==========

    /**
     * 辅助基准: 测量缓存命中率
     * 用于验证缓存效果（非性能测试，用于诊断）
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void measureCacheHitRate(Blackhole blackhole) {
        PathMatcher.clearCache();
        int initialSize = PathMatcher.getCacheSize();

        // First pass: Cache miss (compile patterns)
        String[] patterns = {"order.*", "user.*", "*.password"};
        for (int i = 0; i < 100; i++) {
            for (String pattern : patterns) {
                PathMatcher.matchGlob("test.field", pattern);
            }
        }

        int finalSize = PathMatcher.getCacheSize();

        // Expected: only 3 patterns cached (100 iterations reuse same patterns)
        // Cache hit rate = (300 - 3) / 300 = 99%
        blackhole.consume(finalSize);

        // Note: Actual hit rate verification done in FilterPerformanceTests
        // This benchmark just demonstrates cache behavior
    }
}
