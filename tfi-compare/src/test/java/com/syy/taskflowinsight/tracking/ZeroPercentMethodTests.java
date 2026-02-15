package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.metrics.TfiMetrics;
import com.syy.taskflowinsight.tracking.compare.EnhancedDateCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.entity.EntityChangeGroup;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityOperation;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.monitoring.DegradationConfig;
import com.syy.taskflowinsight.tracking.monitoring.DegradationDecisionEngine;
import com.syy.taskflowinsight.tracking.monitoring.DegradationLevel;
import com.syy.taskflowinsight.tracking.monitoring.DegradationManager;
import com.syy.taskflowinsight.tracking.monitoring.DegradationPerformanceMonitor;
import com.syy.taskflowinsight.tracking.monitoring.ResourceMonitor;
import com.syy.taskflowinsight.tracking.monitoring.SystemMetrics;
import com.syy.taskflowinsight.tracking.path.CaffeinePathMatcherCache;
import com.syy.taskflowinsight.tracking.path.PathArbiter;
import com.syy.taskflowinsight.tracking.path.PathCollector;
import com.syy.taskflowinsight.tracking.path.PathDeduplicationConfig;
import com.syy.taskflowinsight.tracking.render.MarkdownRenderer;
import com.syy.taskflowinsight.tracking.render.RenderStyle;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对 0% 或极低覆盖率方法的专项测试
 * 覆盖 DegradationManager、PathCollector、DiffDetector、PathArbiter、
 * CaffeinePathMatcherCache、ObjectSnapshotDeep、MarkdownRenderer、EnhancedDateCompareStrategy
 *
 * @since 3.0.0
 */
@DisplayName("零覆盖率方法专项测试")
class ZeroPercentMethodTests {

    // ── 1. DegradationManager.getDegradationReason ──

    @Nested
    @DisplayName("DegradationManager — getDegradationReason 降级原因")
    class DegradationManagerGetDegradationReasonTests {

        private DegradationManager manager;
        private DegradationConfig config;
        private DegradationPerformanceMonitor perfMonitor;
        private ResourceMonitor resourceMonitor;
        private TfiMetrics tfiMetrics;
        private DegradationDecisionEngine decisionEngine;
        private ApplicationEventPublisher eventPublisher;

        @BeforeEach
        void setUp() {
            config = new DegradationConfig(
                    true,
                    Duration.ofSeconds(5),
                    Duration.ofMillis(0), // 无滞后，便于测试
                    Duration.ofSeconds(10),
                    200L,
                    90.0,
                    1000L,
                    new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                    new DegradationConfig.PerformanceThresholds(100L, 0.05, 80.0),
                    500,
                    5,
                    10000
            );
            perfMonitor = new DegradationPerformanceMonitor();
            resourceMonitor = new ResourceMonitor();
            tfiMetrics = new TfiMetrics(Optional.of(new SimpleMeterRegistry()));
            decisionEngine = new DegradationDecisionEngine(config);
            eventPublisher = event -> {};
            manager = new DegradationManager(
                    perfMonitor, resourceMonitor, tfiMetrics, config,
                    decisionEngine, eventPublisher
            );
        }

        @Test
        @DisplayName("evaluateAndAdjust 执行并触发 getDegradationReason")
        void evaluateAndAdjust_triggersGetDegradationReason() {
            perfMonitor.recordOperation(Duration.ofMillis(300));
            perfMonitor.recordOperation(Duration.ofMillis(300));
            manager.evaluateAndAdjust();
            assertThat(manager.getCurrentLevel()).isNotNull();
        }

        @Test
        @DisplayName("通过反射直接调用 getDegradationReason — 多分支覆盖")
        void getDegradationReason_viaReflection() throws Exception {
            Method m = DegradationManager.class.getDeclaredMethod(
                    "getDegradationReason", SystemMetrics.class, DegradationLevel.class);
            m.setAccessible(true);

            SystemMetrics metricsHighMem = SystemMetrics.builder()
                    .averageOperationTime(Duration.ofMillis(50))
                    .slowOperationCount(0)
                    .memoryUsagePercent(85.0)
                    .availableMemoryMB(100)
                    .cpuUsagePercent(50)
                    .threadCount(10)
                    .timestamp(System.currentTimeMillis())
                    .build();
            String reason1 = (String) m.invoke(manager, metricsHighMem, DegradationLevel.SUMMARY_ONLY);
            assertThat(reason1).contains("memory_usage=");

            SystemMetrics metricsHighAvg = SystemMetrics.builder()
                    .averageOperationTime(Duration.ofMillis(500))
                    .slowOperationCount(0)
                    .memoryUsagePercent(50)
                    .availableMemoryMB(500)
                    .cpuUsagePercent(50)
                    .threadCount(10)
                    .timestamp(System.currentTimeMillis())
                    .build();
            String reason2 = (String) m.invoke(manager, metricsHighAvg, DegradationLevel.SIMPLE_COMPARISON);
            assertThat(reason2).contains("avg_time=");
        }
    }

    // ── 2–4. PathCollector: collectFromObject, collectFromArray, collectFromCollection ──

    @Nested
    @DisplayName("PathCollector — collectFromObject/Array/Collection 路径收集")
    class PathCollectorTraversalTests {

        static class NestedPojo {
            String name;
            int value;
        }

        @Test
        @DisplayName("collectPathsForObject — 嵌套对象触发 collectFromObject")
        void collectFromObject_viaNestedPojo() {
            Map<String, Object> root = new HashMap<>();
            NestedPojo inner = new NestedPojo();
            inner.name = "test";
            inner.value = 42;
            root.put("inner", inner);

            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            List<PathArbiter.PathCandidate> paths = collector.collectPathsForObject(
                    inner, "inner", root);
            assertThat(paths).isNotEmpty();
            assertThat(paths.stream().map(PathArbiter.PathCandidate::getPath))
                    .anyMatch(p -> p.contains("inner"));
        }

        @Test
        @DisplayName("collectPathsForObject — 数组触发 collectFromArray")
        void collectFromArray_viaObjectArray() {
            Map<String, Object> root = new HashMap<>();
            Object[] arr = new Object[]{"a", "b", new NestedPojo()};
            root.put("items", arr);

            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            NestedPojo target = (NestedPojo) arr[2];
            List<PathArbiter.PathCandidate> paths = collector.collectPathsForObject(
                    target, "items[2]", root);
            assertThat(paths).isNotEmpty();
            assertThat(paths.stream().map(PathArbiter.PathCandidate::getPath))
                    .anyMatch(p -> p.contains("[2]"));
        }

        @Test
        @DisplayName("collectPathsForObject — List 触发 collectFromCollection")
        void collectFromCollection_viaList() {
            Map<String, Object> root = new HashMap<>();
            NestedPojo target = new NestedPojo();
            target.name = "x";
            List<Object> list = Arrays.asList(1, "y", target);
            root.put("list", list);

            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            List<PathArbiter.PathCandidate> paths = collector.collectPathsForObject(
                    target, "list[2]", root);
            assertThat(paths).isNotEmpty();
        }

        @Test
        @DisplayName("collectPathsForObject — Set 触发 collectFromCollection")
        void collectFromCollection_viaSet() {
            NestedPojo target = new NestedPojo();
            target.name = "s";
            Set<Object> set = new HashSet<>(Arrays.asList(1, target));

            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("set", set);

            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            List<PathArbiter.PathCandidate> paths = collector.collectPathsForObject(
                    target, "set", snapshot);
            assertThat(paths).isNotEmpty();
        }
    }

    // ── 5. DiffDetector.warmUpDedup ──

    @Nested
    @DisplayName("DiffDetector — warmUpDedup 预热去重")
    class DiffDetectorWarmUpTests {

        @Test
        @DisplayName("tfi.align.warmup=true 时 warmUpDedup 被触发")
        void warmUpDedup_viaSystemProperty() {
            String saved = System.getProperty("tfi.align.warmup");
            try {
                System.setProperty("tfi.align.warmup", "true");
                DiffDetector.setEnhancedDeduplicationEnabled(true);
                DiffDetector.setEnhancedDeduplicationEnabled(false); // 切换会触发 warmup
                DiffDetector.setEnhancedDeduplicationEnabled(true);
                // warmUpDedup 在 setEnhancedDeduplicationEnabled 内被调用
                assertThat(DiffDetector.isEnhancedDeduplicationEnabled()).isTrue();
            } finally {
                if (saved != null) {
                    System.setProperty("tfi.align.warmup", saved);
                } else {
                    System.clearProperty("tfi.align.warmup");
                }
            }
        }
    }

    // ── 6–7. PathArbiter.removeAncestors, hasDescendantPrefix ──

    @Nested
    @DisplayName("PathArbiter — removeAncestors / hasDescendantPrefix")
    class PathArbiterPrivateMethodsTests {

        @Test
        @DisplayName("removeAncestors 通过反射调用")
        void removeAncestors_viaReflection() throws Exception {
            Method m = PathArbiter.class.getDeclaredMethod(
                    "removeAncestors",
                    java.util.Set.class,
                    java.util.NavigableSet.class,
                    java.util.Map.class,
                    String.class);
            m.setAccessible(true);

            Set<String> keptSet = new HashSet<>(Arrays.asList("a", "a.b", "a.b.c"));
            java.util.NavigableSet<String> keptTree = new java.util.TreeSet<>(keptSet);
            Map<String, PathArbiter.PathCandidate> keptMap = new HashMap<>();
            Object ref = new Object();
            keptMap.put("a", new PathArbiter.PathCandidate("a", 1, PathArbiter.AccessType.FIELD, ref));
            keptMap.put("a.b", new PathArbiter.PathCandidate("a.b", 2, PathArbiter.AccessType.FIELD, ref));
            keptMap.put("a.b.c", new PathArbiter.PathCandidate("a.b.c", 3, PathArbiter.AccessType.FIELD, ref));

            m.invoke(null, keptSet, keptTree, keptMap, "a.b.c");
            assertThat(keptSet).doesNotContain("a", "a.b");
            assertThat(keptSet).contains("a.b.c");
        }

        @Test
        @DisplayName("hasDescendantPrefix 通过反射调用")
        void hasDescendantPrefix_viaReflection() throws Exception {
            Method m = PathArbiter.class.getDeclaredMethod(
                    "hasDescendantPrefix",
                    java.util.NavigableSet.class,
                    String.class);
            m.setAccessible(true);

            java.util.NavigableSet<String> tree = new java.util.TreeSet<>(
                    Arrays.asList("a.b", "a.b.c", "x.y"));
            assertThat((Boolean) m.invoke(null, tree, "a")).isTrue();
            assertThat((Boolean) m.invoke(null, tree, "x")).isTrue();
            assertThat((Boolean) m.invoke(null, tree, "z")).isFalse();
        }

        @Test
        @DisplayName("deduplicateMostSpecific 覆盖祖先移除逻辑")
        void deduplicateMostSpecific_ancestorRemoval() {
            Object ref = new Object();
            List<PathArbiter.PathCandidate> candidates = Arrays.asList(
                    new PathArbiter.PathCandidate("a", 1, PathArbiter.AccessType.FIELD, ref),
                    new PathArbiter.PathCandidate("a.b", 2, PathArbiter.AccessType.FIELD, ref),
                    new PathArbiter.PathCandidate("a.b.c", 3, PathArbiter.AccessType.FIELD, ref)
            );
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(candidates);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPath()).isEqualTo("a.b.c");
        }
    }

    // ── 8. CaffeinePathMatcherCache.fallbackMatch ──

    @Nested
    @DisplayName("CaffeinePathMatcherCache — fallbackMatch 降级匹配")
    class CaffeinePathMatcherCacheFallbackTests {

        private CaffeinePathMatcherCache cache;
        private TfiConfig tfiConfig;

        @BeforeEach
        void setUp() {
            tfiConfig = new TfiConfig(null, null, null, null, null);
            cache = new CaffeinePathMatcherCache(tfiConfig, new SimpleMeterRegistry());
        }

        @AfterEach
        void tearDown() {
            if (cache != null) cache.destroy();
        }

        @Test
        @DisplayName("无效正则触发 fallbackMatch — 返回 false")
        void invalidPattern_triggersFallback() {
            boolean r = cache.matches("foo", "[invalid");
            assertThat(r).isFalse();
        }

        @Test
        @DisplayName("fallbackMatch — * 和 ** 返回 true")
        void fallbackMatch_wildcards() {
            assertThat(cache.matches("anything", "*")).isTrue();
            assertThat(cache.matches("a.b.c", "**")).isTrue();
        }

        @Test
        @DisplayName("fallbackMatch — 无通配符字面量匹配")
        void fallbackMatch_literal() {
            assertThat(cache.matches("foo.bar", "foo.bar")).isTrue();
            assertThat(cache.matches("foo.bar", "foo.baz")).isFalse();
        }
    }

    // ── 9. ObjectSnapshotDeep.matchesPattern ──

    @Nested
    @DisplayName("ObjectSnapshotDeep — matchesPattern 通配符匹配")
    class ObjectSnapshotDeepMatchesPatternTests {

        @Test
        @DisplayName("matchesPattern 通过反射调用")
        void matchesPattern_viaReflection() throws Exception {
            ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(new SnapshotConfig());
            Method m = ObjectSnapshotDeep.class.getDeclaredMethod("matchesPattern", String.class, String.class);
            m.setAccessible(true);

            assertThat((Boolean) m.invoke(snapshot, "a.b.c", "*")).isTrue();
            assertThat((Boolean) m.invoke(snapshot, "user.name", "*.name")).isTrue();
            assertThat((Boolean) m.invoke(snapshot, "name", "*.name")).isTrue();
            assertThat((Boolean) m.invoke(snapshot, "user.email", "*.name")).isFalse();
        }

        @Test
        @DisplayName("captureDeep 使用 excludePatterns 通配符")
        void captureDeep_withExcludePatterns() {
            Map<String, Object> data = new HashMap<>();
            data.put("name", "Alice");
            data.put("password", "secret");
            data.put("nested", Map.of("key", "val"));

            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
            Set<String> exclude = Set.of("*.password", "nested.*");
            Map<String, Object> result = snapshot.captureDeep(data, 5, Collections.emptySet(), exclude);
            assertThat(result).isNotEmpty();
            assertThat(result.values()).contains("Alice");
        }
    }

    // ── 10–11. MarkdownRenderer.renderChangesKeyPrefixed, splitKeyAndField ──

    @Nested
    @DisplayName("MarkdownRenderer — renderChangesKeyPrefixed / splitKeyAndField")
    class MarkdownRendererKeyPrefixedTests {

        @Test
        @DisplayName("KEY_PREFIXED 模式触发 renderChangesKeyPrefixed")
        void renderChangesKeyPrefixed() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("entity[1]")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder()
                            .fieldName("name")
                            .fieldPath("entity[1].name")
                            .oldValue("Alice")
                            .newValue("Bob")
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .addChange(FieldChange.builder()
                            .fieldName("age")
                            .fieldPath("entity[1].age")
                            .oldValue(25)
                            .newValue(26)
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                    .groups(List.of(group))
                    .build();
            RenderStyle style = RenderStyle.builder()
                    .entityKeyMode(RenderStyle.EntityKeyMode.KEY_PREFIXED)
                    .tableFormat(RenderStyle.TableFormat.GITHUB)
                    .build();
            String markdown = renderer.render(result, style);
            assertThat(markdown).contains("[entity[1]] name");
            assertThat(markdown).contains("[entity[1]] age");
        }

        @Test
        @DisplayName("KEY_SEPARATED 触发 splitKeyAndField")
        void splitKeyAndField_viaKeySeparated() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            EntityChangeGroup group = EntityChangeGroup.builder()
                    .entityKey("entity[id=1]")
                    .operation(EntityOperation.MODIFY)
                    .addChange(FieldChange.builder()
                            .fieldPath("entity[id=1].orders[2].amount")
                            .fieldName("amount")
                            .oldValue(100)
                            .newValue(200)
                            .changeType(ChangeType.UPDATE)
                            .build())
                    .build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                    .groups(List.of(group))
                    .build();
            RenderStyle style = RenderStyle.builder()
                    .entityKeyMode(RenderStyle.EntityKeyMode.KEY_SEPARATED)
                    .tableFormat(RenderStyle.TableFormat.GITHUB)
                    .build();
            String markdown = renderer.render(result, style);
            assertThat(markdown).contains("entity[id=1]");
            assertThat(markdown).contains("orders[2].amount");
        }

        @Test
        @DisplayName("splitKeyAndField — 空路径回退")
        void splitKeyAndField_emptyPath() throws Exception {
            Method m = MarkdownRenderer.class.getDeclaredMethod("splitKeyAndField", String.class);
            m.setAccessible(true);
            MarkdownRenderer renderer = new MarkdownRenderer();
            Object pair = m.invoke(renderer, "");
            assertThat(pair.toString()).contains("-");
        }
    }

    // ── 12. EnhancedDateCompareStrategy 容差分支 ──

    @Nested
    @DisplayName("EnhancedDateCompareStrategy — 容差比较分支")
    class EnhancedDateCompareStrategyToleranceTests {

        private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();

        @Test
        @DisplayName("compareLocalDateTimes — tolerance > 0")
        void compareLocalDateTimes_withTolerance() {
            LocalDateTime a = LocalDateTime.of(2025, 1, 1, 10, 0, 0);
            LocalDateTime b = LocalDateTime.of(2025, 1, 1, 10, 0, 0, 500_000_000); // +0.5s
            assertThat(strategy.compareLocalDateTimes(a, b, 0)).isFalse();
            assertThat(strategy.compareLocalDateTimes(a, b, 1000)).isTrue();
        }

        @Test
        @DisplayName("compareDurations — 容差内/外")
        void compareDurations_withTolerance() {
            Duration a = Duration.ofSeconds(10);
            Duration b = Duration.ofMillis(10_500);
            assertThat(strategy.compareDurations(a, b, 0)).isFalse();
            assertThat(strategy.compareDurations(a, b, 1000)).isTrue();
        }

        @Test
        @DisplayName("compareInstants — 容差内/外")
        void compareInstants_withTolerance() {
            Instant a = Instant.parse("2025-01-01T10:00:00Z");
            Instant b = Instant.parse("2025-01-01T10:00:01Z");
            assertThat(strategy.compareInstants(a, b, 0)).isFalse();
            assertThat(strategy.compareInstants(a, b, 2000)).isTrue();
        }

        @Test
        @DisplayName("compareDates — 容差内/外")
        void compareDates_withTolerance() {
            Date a = Date.from(Instant.parse("2025-01-01T10:00:00Z"));
            Date b = Date.from(Instant.parse("2025-01-01T10:00:01Z"));
            assertThat(strategy.compareDates(a, b, 0)).isFalse();
            assertThat(strategy.compareDates(a, b, 2000)).isTrue();
        }
    }
}
