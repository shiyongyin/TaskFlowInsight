package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.api.ComparatorBuilder;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.tracking.cache.ReflectionMetaCache;
import com.syy.taskflowinsight.tracking.cache.StrategyCache;
import com.syy.taskflowinsight.tracking.compare.CompareCacheConfig;
import com.syy.taskflowinsight.tracking.compare.CompareCacheProperties;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.ResolutionStrategy;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.path.CaffeinePathMatcherCache;
import com.syy.taskflowinsight.tracking.path.PathArbiter;
import com.syy.taskflowinsight.tracking.path.PathCollector;
import com.syy.taskflowinsight.tracking.path.PathDeduplicationConfig;
import com.syy.taskflowinsight.tracking.path.PriorityCalculator;
import com.syy.taskflowinsight.tracking.precision.PrecisionMetrics;
import com.syy.taskflowinsight.tracking.ssot.path.PathNavigator;
import com.syy.taskflowinsight.spi.DefaultComparisonProvider;
import com.syy.taskflowinsight.config.TfiConfig;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 最后311+指令覆盖冲刺测试
 * 覆盖 PrecisionMetrics、PathCollector、CaffeinePathMatcherCache、CompareCacheConfig、
 * PriorityCalculator、PathCandidate、ResolutionStrategy、DefaultComparisonProvider、
 * CompareService、PathNavigator、SessionAwareChangeTracker、ComparatorBuilder
 *
 * @since 3.0.0
 */
@DisplayName("最后311+指令覆盖冲刺 — Last311Tests")
class Last311Tests {

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        SessionAwareChangeTracker.clearAll();
        meterRegistry = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        SessionAwareChangeTracker.clearAll();
        if (meterRegistry != null) {
            try {
                Metrics.removeRegistry(meterRegistry);
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    // ── 1. PrecisionMetrics.registerCounter & initializeMicrometerMetrics ──

    @Nested
    @DisplayName("PrecisionMetrics — registerCounter 与 initializeMicrometerMetrics")
    class PrecisionMetricsTests {

        @Test
        @DisplayName("enableMicrometerIfAvailable 使用 SimpleMeterRegistry 触发 registerCounter")
        void enableMicrometer_triggersRegisterCounter() {
            Metrics.addRegistry(meterRegistry);
            PrecisionMetrics metrics = new PrecisionMetrics();
            metrics.enableMicrometerIfAvailable();
            metrics.recordNumericComparison();
            metrics.recordDateTimeComparison();
            metrics.recordToleranceHit("absolute", 0.1);
            metrics.recordToleranceHit("relative", 0.2);
            metrics.recordToleranceHit("date", 0.3);
            metrics.recordBigDecimalComparison("compareTo");
            metrics.recordCalculationTime(1_000_000);
            metrics.recordCacheHit();
            metrics.recordCacheMiss();
            PrecisionMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
            assertThat(snapshot.numericComparisonCount).isGreaterThanOrEqualTo(1);
            assertThat(snapshot.dateTimeComparisonCount).isGreaterThanOrEqualTo(1);
            assertThat(snapshot.toleranceHitCount).isEqualTo(3);
            assertThat(snapshot.bigDecimalComparisonCount).isEqualTo(1);
            assertThat(snapshot.precisionCacheHitCount).isEqualTo(1);
            assertThat(snapshot.precisionCacheMissCount).isEqualTo(1);
            assertThat(snapshot.getCacheHitRate()).isBetween(0.0, 1.0);
            assertThat(snapshot.getAverageCalculationTimeMicros()).isGreaterThanOrEqualTo(0);
            assertThat(snapshot.getToleranceHitRate()).isGreaterThanOrEqualTo(0);
            metrics.reset();
            metrics.logSummary();
        }

        @Test
        @DisplayName("registerCounter 通过反射直接调用")
        void registerCounter_viaReflection() throws Exception {
            Metrics.addRegistry(meterRegistry);
            PrecisionMetrics metrics = new PrecisionMetrics();
            metrics.enableMicrometerIfAvailable();
            Method registerCounter = PrecisionMetrics.class.getDeclaredMethod(
                "registerCounter", Object.class, String.class, String.class);
            registerCounter.setAccessible(true);
            registerCounter.invoke(metrics, meterRegistry, "tfi.precision.test.counter", "Test counter");
        }
    }

    // ── 2. PathCollector.collectFromObject ──

    @Nested
    @DisplayName("PathCollector — collectFromObject 通过 collectPathsForObject")
    class PathCollectorTests {

        static class NestedBean {
            public String name = "nested";
            public int value = 42;
        }

        static class ComplexBean {
            public String id = "root";
            public NestedBean child = new NestedBean();
        }

        @Test
        @DisplayName("collectPathsForObject 复杂对象触发 collectFromObject")
        void collectPathsForObject_complexObject_triggersCollectFromObject() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setMaxCollectionDepth(10);
            config.setMaxObjectsPerLevel(20);
            config.setCacheEnabled(true);
            PathCollector collector = new PathCollector(config);

            ComplexBean target = new ComplexBean();
            Map<String, Object> rootSnapshot = new HashMap<>();
            rootSnapshot.put("root", target);

            List<PathArbiter.PathCandidate> paths = collector.collectPathsForObject(
                target.child, "root.child", rootSnapshot);

            assertThat(paths).isNotNull();
            collector.clearCache();
            assertThat(collector.getCacheStatistics()).containsKey("cacheSize");
        }
    }

    // ── 3. CaffeinePathMatcherCache.fallbackMatch ──

    @Nested
    @DisplayName("CaffeinePathMatcherCache — fallbackMatch")
    class CaffeinePathMatcherCacheTests {

        @Test
        @DisplayName("matches 无效正则触发 fallbackMatch")
        void matches_invalidRegex_triggersFallback() {
            TfiConfig tfiConfig = new TfiConfig(true,
                new TfiConfig.ChangeTracking(true, 8192, 5,
                    new TfiConfig.ChangeTracking.Snapshot(10, 100, null, 1000, false, 1000L),
                    new TfiConfig.ChangeTracking.Diff("compat", false, 1000, true, "legacy"),
                    new TfiConfig.ChangeTracking.Export("json", true, false, false, false),
                    1024,
                    new TfiConfig.ChangeTracking.Summary(true, 100, 10, null)),
                new TfiConfig.Context(3600000L, false, 60000L, false, 60000L),
                new TfiConfig.Metrics(true, Map.of(), "PT1M"),
                new TfiConfig.Security(true, null));

            CaffeinePathMatcherCache cache = new CaffeinePathMatcherCache(tfiConfig, meterRegistry);
            boolean result = cache.matches("order.items.name", "[invalid");
            assertThat(result).isFalse();
            cache.matchBatch(Arrays.asList("a", "b"), "[invalid");
            cache.findMatchingPatterns("path", List.of("*", "**", "exact"));
            cache.preload(List.of("valid.*"));
            cache.clear();
            assertThat(cache.getStats()).isNotNull();
        }
    }

    // ── 4. CompareCacheConfig.strategyCache & reflectionMetaCache ──

    @Nested
    @DisplayName("CompareCacheConfig — strategyCache 与 reflectionMetaCache")
    class CompareCacheConfigTests {

        @Test
        @DisplayName("strategyCache Bean 方法可被调用")
        void strategyCache_beanMethod() {
            CompareCacheConfig config = new CompareCacheConfig();
            CompareCacheProperties props = new CompareCacheProperties();
            props.setStrategy(new CompareCacheProperties.CacheConfig(true, 5000, 120000));
            props.setReflection(new CompareCacheProperties.CacheConfig(true, 3000, 60000));

            StrategyCache strategyCache = config.strategyCache(props);
            assertThat(strategyCache).isNotNull();
        }

        @Test
        @DisplayName("reflectionMetaCache Bean 方法可被调用")
        void reflectionMetaCache_beanMethod() {
            CompareCacheConfig config = new CompareCacheConfig();
            CompareCacheProperties props = new CompareCacheProperties();
            props.setReflection(new CompareCacheProperties.CacheConfig(true, 3000, 60000));

            ReflectionMetaCache reflectionCache = config.reflectionMetaCache(props);
            assertThat(reflectionCache).isNotNull();
        }
    }

    // ── 5. PriorityCalculator.calculateStableIdScore ──

    @Nested
    @DisplayName("PriorityCalculator — calculateStableIdScore")
    class PriorityCalculatorTests {

        @Test
        @DisplayName("calculateStableIdScore 通过反射调用")
        void calculateStableIdScore_viaReflection() throws Exception {
            PriorityCalculator calc = new PriorityCalculator(new PathDeduplicationConfig());
            Method m = PriorityCalculator.class.getDeclaredMethod("calculateStableIdScore", String.class);
            m.setAccessible(true);
            long score1 = (Long) m.invoke(calc, new Object[]{"ID00000001"});
            long score2 = (Long) m.invoke(calc, new Object[]{"IDABCD1234"});
            long score3 = (Long) m.invoke(calc, new Object[]{null});
            long score4 = (Long) m.invoke(calc, new Object[]{"plainHash"});
            assertThat(score1).isGreaterThanOrEqualTo(0);
            assertThat(score2).isGreaterThanOrEqualTo(0);
            assertThat(score3).isEqualTo(0);
            assertThat(score4).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("createComparator 使用 getStableId 作为 tie-break")
        void createComparator_usesStableId() {
            PriorityCalculator calc = new PriorityCalculator(new PathDeduplicationConfig());
            PathArbiter.PathCandidate c1 = new PathArbiter.PathCandidate("a.b", 2, "obj1");
            PathArbiter.PathCandidate c2 = new PathArbiter.PathCandidate("a.c", 2, "obj2");
            List<PathArbiter.PathCandidate> sorted = calc.sortByPriority(Arrays.asList(c1, c2));
            assertThat(sorted).hasSize(2);
            assertThat(calc.selectHighestPriority(Arrays.asList(c1, c2))).isNotNull();
            assertThat(calc.verifyConsistency(c1, 5)).isTrue();
            assertThat(calc.getPriorityDetails(c1)).contains("Priority");
        }
    }

    // ── 6. PathArbiter$PathCandidate.toString ──

    @Nested
    @DisplayName("PathArbiter.PathCandidate — toString")
    class PathCandidateToStringTests {

        @Test
        @DisplayName("PathCandidate toString 返回完整格式")
        void pathCandidate_toString() {
            PathArbiter.PathCandidate candidate = new PathArbiter.PathCandidate(
                "order.items[0].name", 3, "target");
            String s = candidate.toString();
            assertThat(s).contains("PathCandidate")
                .contains("order.items[0].name")
                .contains("depth=3")
                .contains("id=");
        }
    }

    // ── 7. ResolutionStrategy.<clinit> ──

    @Nested
    @DisplayName("ResolutionStrategy — 静态初始化")
    class ResolutionStrategyTests {

        @Test
        @DisplayName("ResolutionStrategy 类引用触发 <clinit>")
        void resolutionStrategy_classRef() {
            ResolutionStrategy[] values = ResolutionStrategy.values();
            assertThat(values).contains(
                ResolutionStrategy.USE_LEFT,
                ResolutionStrategy.USE_RIGHT,
                ResolutionStrategy.USE_BASE,
                ResolutionStrategy.MERGE_VALUES,
                ResolutionStrategy.MANUAL,
                ResolutionStrategy.SKIP
            );
        }
    }

    // ── 8. DefaultComparisonProvider.compare ──

    @Nested
    @DisplayName("DefaultComparisonProvider — compare 两个重载")
    class DefaultComparisonProviderTests {

        @Test
        @DisplayName("compare 两参数重载")
        void compare_twoArgs() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            CompareResult r1 = provider.compare("a", "a");
            assertThat(r1).isNotNull();
            assertThat(r1.isIdentical()).isTrue();

            CompareResult r2 = provider.compare(Map.of("a", 1), Map.of("b", 2));
            assertThat(r2).isNotNull();
            assertThat(r2.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("compare 三参数重载带 CompareOptions")
        void compare_threeArgs() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            CompareResult r = provider.compare(
                Map.of("x", 1),
                Map.of("x", 2),
                CompareOptions.builder().calculateSimilarity(true).build());
            assertThat(r).isNotNull();
            assertThat(provider.similarity("a", "a")).isEqualTo(1.0);
            assertThat(provider.similarity(Map.of("a", 1), Map.of("a", 2))).isLessThan(1.0);
            assertThat(provider.priority()).isEqualTo(0);
            assertThat(provider.toString()).contains("DefaultComparisonProvider");
        }
    }

    // ── 9. CompareService.calculateSimilarity ──

    @Nested
    @DisplayName("CompareService — calculateSimilarity")
    class CompareServiceSimilarityTests {

        @Test
        @DisplayName("compare 启用 calculateSimilarity 触发 calculateSimilarity")
        void compare_withCalculateSimilarity() {
            CompareService svc = new CompareService();
            CompareOptions opts = CompareOptions.builder()
                .calculateSimilarity(true)
                .enableDeepCompare(true)
                .maxDepth(5)
                .build();

            CompareResult r = svc.compare(
                Map.of("a", 1, "b", 2),
                Map.of("a", 1, "b", 3),
                opts);

            assertThat(r).isNotNull();
            assertThat(r.getSimilarity()).isNotNull();
            assertThat(r.getSimilarity()).isBetween(0.0, 1.0);
        }
    }

    // ── 10. PathNavigator.navigateIndex ──

    @Nested
    @DisplayName("PathNavigator — navigateIndex")
    class PathNavigatorTests {

        static class WithList {
            public List<String> items = Arrays.asList("a", "b", "c");
        }

        static class WithArray {
            public String[] arr = new String[]{"x", "y", "z"};
        }

        static class WithMap {
            public Map<String, String> map = new HashMap<>(Map.of("key1", "v1", "0", "index0"));
        }

        @Test
        @DisplayName("resolve 列表索引触发 navigateIndex")
        void resolve_listIndex() {
            WithList root = new WithList();
            Object v = PathNavigator.resolve(root, "items[0]");
            assertThat(v).isEqualTo("a");
            Object v1 = PathNavigator.resolve(root, "items[1]");
            assertThat(v1).isEqualTo("b");
        }

        @Test
        @DisplayName("resolve 数组索引触发 navigateIndex")
        void resolve_arrayIndex() {
            WithArray root = new WithArray();
            Object v = PathNavigator.resolve(root, "arr[0]");
            assertThat(v).isEqualTo("x");
        }

        @Test
        @DisplayName("resolve Map 键触发 navigateIndex")
        void resolve_mapKey() {
            WithMap root = new WithMap();
            Object v = PathNavigator.resolve(root, "map[key1]");
            assertThat(v).isEqualTo("v1");
        }
    }

    // ── 11. SessionAwareChangeTracker.enforeLimits ──

    @Nested
    @DisplayName("SessionAwareChangeTracker — enforeLimits")
    class SessionAwareChangeTrackerEnforeLimitsTests {

        @Test
        @DisplayName("enforeLimits 通过反射调用")
        void enforeLimits_viaReflection() throws Exception {
            Method m = SessionAwareChangeTracker.class.getDeclaredMethod("enforeLimits");
            m.setAccessible(true);
            m.invoke(null);
        }

        @Test
        @DisplayName("recordChange 多次调用触发 enforeLimits 逻辑")
        void recordChange_triggersEnforeLimits() throws Exception {
            try (ManagedThreadContext ctx = ManagedThreadContext.create("limit-test")) {
                for (int i = 0; i < 5; i++) {
                    SessionAwareChangeTracker.recordChange(
                        ChangeRecord.of("Obj", "f" + i, "old", "new", ChangeType.UPDATE));
                }
                List<ChangeRecord> changes = SessionAwareChangeTracker.getCurrentSessionChanges();
                assertThat(changes).hasSize(5);
            }
        }
    }

    // ── 12. ComparatorBuilder.compare ──

    @Nested
    @DisplayName("ComparatorBuilder — compare")
    class ComparatorBuilderCompareTests {

        @Test
        @DisplayName("compare 使用 CompareService 执行")
        void compare_withCompareService() throws Exception {
            CompareService svc = CompareService.createDefault(CompareOptions.builder().build());
            var ctor = ComparatorBuilder.class.getDeclaredConstructor(CompareService.class);
            ctor.setAccessible(true);
            ComparatorBuilder builder = ctor.newInstance(svc);

            CompareResult r1 = builder.compare("hello", "hello");
            assertThat(r1).isNotNull();
            assertThat(r1.isIdentical()).isTrue();

            CompareResult r2 = builder.ignoring("id").compare(
                Map.of("id", 1, "name", "A"),
                Map.of("id", 2, "name", "A"));
            assertThat(r2).isNotNull();

            CompareResult r3 = builder.withSimilarity().compare(
                List.of(1, 2, 3),
                List.of(1, 2, 4));
            assertThat(r3).isNotNull();
        }
    }
}
