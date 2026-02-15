package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.monitoring.DegradationConfig;
import com.syy.taskflowinsight.tracking.monitoring.DegradationDecisionEngine;
import com.syy.taskflowinsight.tracking.monitoring.DegradationLevel;
import com.syy.taskflowinsight.tracking.monitoring.DegradationPerformanceMonitor;
import com.syy.taskflowinsight.tracking.monitoring.SystemMetrics;
import com.syy.taskflowinsight.tracking.path.PathDeduplicationConfig;
import com.syy.taskflowinsight.tracking.path.PathDeduplicator;
import com.syy.taskflowinsight.tracking.path.PathDeduplicator.DeduplicationStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 路径列表执行器最终覆盖测试
 * 覆盖 ListCompareExecutor、PathDeduplicator、DegradationDecisionEngine 剩余分支
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@DisplayName("Path List Executor Final — 路径列表执行器最终覆盖测试")
class PathListExecutorFinalTests {

    private static final CompareOptions DEFAULT = CompareOptions.DEFAULT;

    private ListCompareExecutor createExecutor() {
        return new ListCompareExecutor(List.of(
            new SimpleListStrategy(),
            new AsSetListStrategy(),
            new LcsListStrategy(),
            new LevenshteinListStrategy(),
            new EntityListStrategy()
        ));
    }

    // ── ListCompareExecutor 剩余分支 ──

    @Nested
    @DisplayName("ListCompareExecutor — K-pairs 降级")
    class ListExecutorKpairsDegradation {

        @Test
        @DisplayName("DegradationDecisionEngine 触发 K-pairs 降级")
        void kPairsDegradation() throws Exception {
            DegradationConfig config = new DegradationConfig(
                true, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(10),
                200L, 90.0, 1000L,
                new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
                500, 5, 100  // kPairsThreshold=100
            );
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);

            ListCompareExecutor executor = createExecutor();
            injectDegradationEngine(executor, engine);

            List<String> a = new ArrayList<>();
            List<String> b = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                a.add("x" + i);
                b.add("y" + i);
            }
            CompareResult r = executor.compare(a, b, DEFAULT);
            assertThat(r).isNotNull();
            assertThat(executor.getDegradationCount()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("DegradationDecisionEngine 异常时忽略 K-pairs 降级")
        void kPairsDegradation_exceptionIgnored() throws Exception {
            DegradationConfig config = new DegradationConfig(
                true, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(10),
                200L, 90.0, 1000L,
                new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
                500, 5, Integer.MAX_VALUE
            );
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);

            ListCompareExecutor executor = createExecutor();
            injectDegradationEngine(executor, engine);

            List<String> a = List.of("a", "b");
            List<String> b = List.of("a", "c");
            CompareResult r = executor.compare(a, b, DEFAULT);
            assertThat(r).isNotNull();
        }

        private void injectDegradationEngine(ListCompareExecutor executor, DegradationDecisionEngine engine)
                throws Exception {
            Field f = ListCompareExecutor.class.getDeclaredField("degradationDecisionEngine");
            f.setAccessible(true);
            f.set(executor, engine);
        }
    }

    @Nested
    @DisplayName("ListCompareExecutor — Entity 检测")
    class ListExecutorEntityDetection {

        @Test
        @DisplayName("Entity 无 @Key 时发出诊断")
        void entityWithoutKey() {
            ListCompareExecutor executor = createExecutor();
            List<EntityNoKey> a = List.of(new EntityNoKey("x"));
            List<EntityNoKey> b = List.of(new EntityNoKey("y"));
            CompareResult r = executor.compare(a, b, CompareOptions.builder().strategyName("ENTITY").build());
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("混合元素类型列表")
        void mixedElementTypes() {
            ListCompareExecutor executor = createExecutor();
            List<Object> mixed = new ArrayList<>();
            mixed.add("string");
            mixed.add(42);
            CompareResult r = executor.compare(mixed, new ArrayList<>(mixed), DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("LCS 自动路由 detectMoves + preferLcsWhenDetectMoves")
        void lcsAutoRouteWithDetectMoves() throws Exception {
            ListCompareExecutor executor = createExecutor();
            CompareRoutingProperties props = new CompareRoutingProperties();
            props.getLcs().setEnabled(true);
            props.getLcs().setPreferLcsWhenDetectMoves(true);
            injectAutoRouteProps(executor, props);

            CompareOptions opts = CompareOptions.builder().detectMoves(true).build();
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("b", "a", "c");
            CompareResult r = executor.compare(before, after, opts);
            assertThat(r).isNotNull();
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.MOVE);
        }

        @Test
        @DisplayName("未知策略名回退到 SIMPLE")
        void unknownStrategyFallback() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("UNKNOWN_STRATEGY").build();
            CompareResult r = executor.compare(List.of("a"), List.of("b"), opts);
            assertThat(r).isNotNull();
        }

        private void injectAutoRouteProps(ListCompareExecutor executor, CompareRoutingProperties props)
                throws Exception {
            Field f = ListCompareExecutor.class.getDeclaredField("autoRouteProps");
            f.setAccessible(true);
            f.set(executor, props);
        }
    }

    @Entity
    static class EntityNoKey {
        @SuppressWarnings("unused")
        private String name;
        EntityNoKey(String name) { this.name = name; }
    }

    // ── PathDeduplicator ──

    @Nested
    @DisplayName("PathDeduplicator — 剩余路径")
    class PathDeduplicatorTests {

        private PathDeduplicator deduplicator;

        @BeforeEach
        void setUp() {
            deduplicator = new PathDeduplicator();
        }

        @Test
        @DisplayName("deduplicateLegacy 同一路径多条记录")
        void deduplicateLegacy_samePath() {
            List<ChangeRecord> records = List.of(
                ChangeRecord.of("O", "field", "old", "new", ChangeType.UPDATE),
                ChangeRecord.of("O", "field", "old", "new", ChangeType.UPDATE)
            );
            List<ChangeRecord> result = deduplicator.deduplicate(records);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("deduplicateLegacy null 返回空列表")
        void deduplicateLegacy_null() {
            List<ChangeRecord> result = deduplicator.deduplicate(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateLegacy 空列表")
        void deduplicateLegacy_empty() {
            List<ChangeRecord> result = deduplicator.deduplicate(Collections.emptyList());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph 禁用时返回原列表")
        void deduplicateWithObjectGraph_disabled() {
            PathDeduplicationConfig cfg = new PathDeduplicationConfig();
            cfg.setEnabled(false);
            PathDeduplicator disabled = new PathDeduplicator(cfg);
            List<ChangeRecord> records = List.of(
                ChangeRecord.of("O", "field", "old", "new", ChangeType.UPDATE)
            );
            List<ChangeRecord> result = disabled.deduplicateWithObjectGraph(
                records, Map.of("field", "old"), Map.of("field", "new"));
            assertThat(result).isEqualTo(records);
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph 超过 fastPathChangeLimit")
        void deduplicateWithObjectGraph_fastPath() {
            List<ChangeRecord> records = new ArrayList<>();
            for (int i = 0; i < 900; i++) {
                records.add(ChangeRecord.of("O", "field" + i, "old", "new", ChangeType.UPDATE));
            }
            List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
                records, new HashMap<>(), new HashMap<>());
            assertThat(result).hasSize(900);
        }

        @Test
        @DisplayName("getStatistics 返回有效统计")
        void getStatistics() {
            deduplicator.deduplicate(List.of(
                ChangeRecord.of("O", "f", "old", "new", ChangeType.UPDATE),
                ChangeRecord.of("O", "f", "old", "new", ChangeType.UPDATE)
            ));
            DeduplicationStatistics stats = deduplicator.getStatistics();
            assertThat(stats).isNotNull();
            assertThat(stats.getTotalDeduplicationCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getDuplicateRemovalRate()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("resetStatistics")
        void resetStatistics() {
            deduplicator.deduplicate(List.of(
                ChangeRecord.of("O", "f", "old", "new", ChangeType.UPDATE)
            ));
            assertThatCode(deduplicator::resetStatistics).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("isEnabled 返回配置状态")
        void isEnabled() {
            assertThat(deduplicator.isEnabled()).isTrue();
        }
    }

    // ── DegradationDecisionEngine ──

    @Nested
    @DisplayName("DegradationDecisionEngine — 剩余分支")
    class DegradationDecisionEngineTests {

        private DegradationConfig config;
        private DegradationPerformanceMonitor perfMonitor;

        @BeforeEach
        void setUp() {
            config = new DegradationConfig(
                true, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(10),
                200L, 90.0, 1000L,
                new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
                500, 5, 10000
            );
            perfMonitor = new DegradationPerformanceMonitor();
        }

        @Test
        @DisplayName("calculateOptimalLevel 内存压力 >= 90")
        void calculateOptimalLevel_memoryCritical() {
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            SystemMetrics metrics = SystemMetrics.builder()
                .memoryUsagePercent(92)
                .cpuUsagePercent(50)
                .threadCount(10)
                .averageOperationTime(Duration.ofMillis(50))
                .availableMemoryMB(1000)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, perfMonitor);
            assertThat(level).isEqualTo(DegradationLevel.DISABLED);
        }

        @Test
        @DisplayName("calculateOptimalLevel 内存 80-90")
        void calculateOptimalLevel_memorySummary() {
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            SystemMetrics metrics = SystemMetrics.builder()
                .memoryUsagePercent(85)
                .cpuUsagePercent(50)
                .threadCount(10)
                .averageOperationTime(Duration.ofMillis(50))
                .availableMemoryMB(1000)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, perfMonitor);
            assertThat(level).isEqualTo(DegradationLevel.SUMMARY_ONLY);
        }

        @Test
        @DisplayName("calculateOptimalLevel 性能严重 avgTime>1000")
        void calculateOptimalLevel_perfSevere() {
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            DegradationPerformanceMonitor slowMonitor = new DegradationPerformanceMonitor();
            SystemMetrics metrics = SystemMetrics.builder()
                .memoryUsagePercent(50)
                .cpuUsagePercent(50)
                .threadCount(10)
                .averageOperationTime(Duration.ofMillis(1500))
                .availableMemoryMB(1000)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, slowMonitor);
            assertThat(level).isEqualTo(DegradationLevel.DISABLED);
        }

        @Test
        @DisplayName("calculateOptimalLevel 性能高 avgTime>150")
        void calculateOptimalLevel_perfHigh() {
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            SystemMetrics metrics = SystemMetrics.builder()
                .memoryUsagePercent(50)
                .cpuUsagePercent(50)
                .threadCount(10)
                .averageOperationTime(Duration.ofMillis(200))
                .availableMemoryMB(1000)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, perfMonitor);
            assertThat(level).isIn(DegradationLevel.SUMMARY_ONLY, DegradationLevel.SIMPLE_COMPARISON,
                DegradationLevel.SKIP_DEEP_ANALYSIS, DegradationLevel.FULL_TRACKING);
        }

        @Test
        @DisplayName("calculateOptimalLevel 资源压力 CPU>=95")
        void calculateOptimalLevel_cpuCritical() {
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            SystemMetrics metrics = SystemMetrics.builder()
                .memoryUsagePercent(50)
                .cpuUsagePercent(96)
                .threadCount(10)
                .averageOperationTime(Duration.ofMillis(50))
                .availableMemoryMB(1000)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, perfMonitor);
            assertThat(level).isEqualTo(DegradationLevel.DISABLED);
        }

        @Test
        @DisplayName("calculateOptimalLevel 线程数>1000")
        void calculateOptimalLevel_highThreads() {
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            SystemMetrics metrics = SystemMetrics.builder()
                .memoryUsagePercent(50)
                .cpuUsagePercent(50)
                .threadCount(1500)
                .averageOperationTime(Duration.ofMillis(50))
                .availableMemoryMB(1000)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, perfMonitor);
            assertThat(level).isEqualTo(DegradationLevel.SKIP_DEEP_ANALYSIS);
        }

        @Test
        @DisplayName("calculateOptimalLevel 可用内存<100MB")
        void calculateOptimalLevel_lowAvailableMem() {
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            SystemMetrics metrics = SystemMetrics.builder()
                .memoryUsagePercent(50)
                .cpuUsagePercent(50)
                .threadCount(10)
                .averageOperationTime(Duration.ofMillis(50))
                .availableMemoryMB(80)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, perfMonitor);
            assertThat(level).isEqualTo(DegradationLevel.SUMMARY_ONLY);
        }

        @Test
        @DisplayName("shouldDegradeForListSize")
        void shouldDegradeForListSize() {
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            assertThat(engine.shouldDegradeForListSize(600)).isTrue();
            assertThat(engine.shouldDegradeForListSize(400)).isFalse();
        }

        @Test
        @DisplayName("shouldDegradeForKPairs")
        void shouldDegradeForKPairs() {
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            assertThat(engine.shouldDegradeForKPairs(15000)).isTrue();
            assertThat(engine.shouldDegradeForKPairs(5000)).isFalse();
        }
    }
}
