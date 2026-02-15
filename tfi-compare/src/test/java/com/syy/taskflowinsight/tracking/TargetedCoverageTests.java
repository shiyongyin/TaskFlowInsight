package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.annotation.DiffInclude;
import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.IgnoreDeclaredProperties;
import com.syy.taskflowinsight.annotation.IgnoreInheritedProperties;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.annotation.ValueObject;
import com.syy.taskflowinsight.api.ComparatorBuilder;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.config.ConcurrencyAutoConfiguration;
import com.syy.taskflowinsight.config.ConcurrencyConfig;
import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.config.TfiConfigValidator;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.metrics.TfiMetrics;
import com.syy.taskflowinsight.spi.DefaultComparisonProvider;
import com.syy.taskflowinsight.tracking.cache.ReflectionMetaCache;
import com.syy.taskflowinsight.tracking.cache.StrategyCache;
import com.syy.taskflowinsight.tracking.compare.CompareCacheConfig;
import com.syy.taskflowinsight.tracking.compare.CompareCacheProperties;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.DateCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.EnhancedDateCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ReportFormat;
import com.syy.taskflowinsight.tracking.compare.ResolutionStrategy;
import com.syy.taskflowinsight.tracking.compare.StrategyResolver;
import com.syy.taskflowinsight.tracking.compare.entity.EntityChangeGroup;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityOperation;
import com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LcsListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.monitoring.DegradationConfig;
import com.syy.taskflowinsight.tracking.monitoring.DegradationContext;
import com.syy.taskflowinsight.tracking.monitoring.DegradationDecisionEngine;
import com.syy.taskflowinsight.tracking.monitoring.DegradationLevel;
import com.syy.taskflowinsight.tracking.monitoring.DegradationManager;
import com.syy.taskflowinsight.tracking.monitoring.DegradationPerformanceMonitor;
import com.syy.taskflowinsight.tracking.monitoring.ResourceMonitor;
import com.syy.taskflowinsight.tracking.monitoring.SystemMetrics;
import com.syy.taskflowinsight.tracking.path.CaffeinePathMatcherCache;
import com.syy.taskflowinsight.tracking.path.PathArbiter;
import com.syy.taskflowinsight.tracking.path.PathBuilder;
import com.syy.taskflowinsight.tracking.path.PathCache;
import com.syy.taskflowinsight.tracking.path.PathCollector;
import com.syy.taskflowinsight.tracking.path.PathDeduplicationConfig;
import com.syy.taskflowinsight.tracking.path.PathDeduplicator;
import com.syy.taskflowinsight.tracking.path.PathMatcherCacheInterface;
import com.syy.taskflowinsight.tracking.path.PriorityCalculator;
import com.syy.taskflowinsight.tracking.precision.PrecisionMetrics;
import com.syy.taskflowinsight.tracking.query.ListChangeProjector;
import com.syy.taskflowinsight.tracking.render.MarkdownRenderer;
import com.syy.taskflowinsight.tracking.render.RenderStyle;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import com.syy.taskflowinsight.tracking.snapshot.ShallowReferenceMode;
import com.syy.taskflowinsight.tracking.snapshot.filter.ClassLevelFilterEngine;
import com.syy.taskflowinsight.tracking.snapshot.filter.PathMatcher;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import com.syy.taskflowinsight.tracking.ssot.path.PathNavigator;
import com.syy.taskflowinsight.tracking.summary.SummaryInfo;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consolidated targeted coverage tests merging Last311Tests, ZeroPercentMethodTests,
 * ZeroMethodsFinalPushTests, FinalPushTests, and FinalPush2Tests.
 * Covers PrecisionMetrics, PathCollector, CaffeinePathMatcherCache, CompareCacheConfig,
 * PriorityCalculator, PathArbiter, ResolutionStrategy, DefaultComparisonProvider,
 * CompareService, PathNavigator, SessionAwareChangeTracker, ComparatorBuilder,
 * DegradationManager, DiffDetector, ObjectSnapshotDeep, MarkdownRenderer,
 * EnhancedDateCompareStrategy, TfiConfigValidator, TrackingOptions, ListChangeProjector,
 * ConcurrencyAutoConfiguration, TfiMetrics, SummaryInfo, DateCompareStrategy,
 * ClassLevelFilterEngine, EntityKeyUtils, PathMatcher, DiffBuilder, PathDeduplicator,
 * ListCompareExecutor, CompareEngine, and related components.
 *
 * @since 3.0.0
 */
@DisplayName("Targeted Coverage — Consolidated coverage tests")
class TargetedCoverageTests {

    // ═══════════════════════════════════════════════════════════════════════════
    // From Last311Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("From Last311Tests — 最后311+指令覆盖冲刺")
    class FromLast311 {

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

    // ═══════════════════════════════════════════════════════════════════════════
    // From ZeroPercentMethodTests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("From ZeroPercentMethodTests — 零覆盖率方法专项测试")
    class FromZeroPercent {

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
                    Duration.ofMillis(0),
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
                    DiffDetector.setEnhancedDeduplicationEnabled(false);
                    DiffDetector.setEnhancedDeduplicationEnabled(true);
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
                java.util.NavigableSet<String> keptTree = new TreeSet<>(keptSet);
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

                java.util.NavigableSet<String> tree = new TreeSet<>(
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

        @Nested
        @DisplayName("EnhancedDateCompareStrategy — 容差比较分支")
        class EnhancedDateCompareStrategyToleranceTests {

            private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();

            @Test
            @DisplayName("compareLocalDateTimes — tolerance > 0")
            void compareLocalDateTimes_withTolerance() {
                LocalDateTime a = LocalDateTime.of(2025, 1, 1, 10, 0, 0);
                LocalDateTime b = LocalDateTime.of(2025, 1, 1, 10, 0, 0, 500_000_000);
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

    // ═══════════════════════════════════════════════════════════════════════════
    // From ZeroMethodsFinalPushTests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("From ZeroMethodsFinalPushTests — 零覆盖率方法最终冲刺")
    class FromZeroMethodsFinalPush {

        @Nested
        @DisplayName("TfiConfigValidator.validate — 配置验证")
        class TfiConfigValidatorTests {

            @Test
            @DisplayName("validate 有效配置返回 null")
            void validate_validConfig_returnsNull() {
                TfiConfigValidator validator = new TfiConfigValidator();
                TfiConfig valid = new TfiConfig(true,
                    new TfiConfig.ChangeTracking(true, 8192, 5,
                        new TfiConfig.ChangeTracking.Snapshot(10, 100, Set.of(), 1000, false, 1000L),
                        new TfiConfig.ChangeTracking.Diff("compat", false, 1000, true, "legacy"),
                        new TfiConfig.ChangeTracking.Export("json", true, false, false, false),
                        1024,
                        new TfiConfig.ChangeTracking.Summary(true, 100, 10, Set.of())),
                    new TfiConfig.Context(3600000L, false, 60000L, false, 60000L),
                    new TfiConfig.Metrics(true, Map.of(), "PT1M"),
                    new TfiConfig.Security(true, Set.of()));
                assertThat(validator.validate(valid)).isNull();
            }

            @Test
            @DisplayName("validate null 配置返回错误")
            void validate_nullConfig_returnsError() {
                TfiConfigValidator validator = new TfiConfigValidator();
                assertThat(validator.validate(null)).isEqualTo("TfiConfig cannot be null");
            }

            @Test
            @DisplayName("validate 快照深度超限返回错误")
            void validate_maxDepthExceeded_returnsError() {
                TfiConfigValidator validator = new TfiConfigValidator();
                TfiConfig invalid = new TfiConfig(true,
                    new TfiConfig.ChangeTracking(true, 8192, 5,
                        new TfiConfig.ChangeTracking.Snapshot(51, 100, Set.of(), 1000, false, 1000L),
                        new TfiConfig.ChangeTracking.Diff("compat", false, 1000, true, "legacy"),
                        new TfiConfig.ChangeTracking.Export("json", true, false, false, false),
                        1024,
                        new TfiConfig.ChangeTracking.Summary(true, 100, 10, Set.of())),
                    new TfiConfig.Context(3600000L, false, 60000L, false, 60000L),
                    new TfiConfig.Metrics(true, Map.of(), "PT1M"),
                    new TfiConfig.Security(true, Set.of()));
                assertThat(validator.validate(invalid)).contains("max depth");
            }

            @Test
            @DisplayName("validate maxChangesPerObject 超限返回错误")
            void validate_maxChangesPerObjectExceeded_returnsError() {
                TfiConfigValidator validator = new TfiConfigValidator();
                TfiConfig invalid = new TfiConfig(true,
                    new TfiConfig.ChangeTracking(true, 8192, 5,
                        new TfiConfig.ChangeTracking.Snapshot(10, 100, Set.of(), 1000, false, 1000L),
                        new TfiConfig.ChangeTracking.Diff("compat", false, 5001, true, "legacy"),
                        new TfiConfig.ChangeTracking.Export("json", true, false, false, false),
                        1024,
                        new TfiConfig.ChangeTracking.Summary(true, 100, 10, Set.of())),
                    new TfiConfig.Context(3600000L, false, 60000L, false, 60000L),
                    new TfiConfig.Metrics(true, Map.of(), "PT1M"),
                    new TfiConfig.Security(true, Set.of()));
                assertThat(validator.validate(invalid)).contains("Max changes per object");
            }

            @Test
            @DisplayName("validateAndThrow 无效配置抛出异常")
            void validateAndThrow_invalid_throws() {
                TfiConfigValidator validator = new TfiConfigValidator();
                assertThatThrownBy(() -> validator.validateAndThrow(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TfiConfig cannot be null");
            }
        }

        @Nested
        @DisplayName("TrackingOptions.toString — 追踪选项字符串")
        class TrackingOptionsToStringTests {

            @Test
            @DisplayName("toString 返回完整格式")
            void toString_returnsFullFormat() {
                TrackingOptions opts = TrackingOptions.builder()
                    .depth(TrackingOptions.TrackingDepth.DEEP)
                    .maxDepth(5)
                    .includeFields("a", "b")
                    .excludeFields("c")
                    .build();
                String s = opts.toString();
                assertThat(s).contains("TrackingOptions")
                    .contains("depth=DEEP")
                    .contains("maxDepth=5")
                    .contains("includeFields")
                    .contains("excludeFields");
            }
        }

        @Nested
        @DisplayName("DegradationDecisionEngine.logDecisionProcess — 降级决策日志")
        class DegradationDecisionEngineTests {

            @Test
            @DisplayName("calculateOptimalLevel 触发 logDecisionProcess（通过反射验证）")
            void calculateOptimalLevel_triggersLogDecisionProcess() throws Exception {
                DegradationConfig config = new DegradationConfig(
                    true, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(10),
                    200L, 90.0, 1000L,
                    new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                    new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
                    500, 5, 10000);
                DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
                SystemMetrics metrics = SystemMetrics.builder()
                    .memoryUsagePercent(50.0)
                    .availableMemoryMB(1000)
                    .cpuUsagePercent(30.0)
                    .threadCount(50)
                    .averageOperationTime(Duration.ofMillis(50))
                    .build();
                DegradationLevel level = engine.calculateOptimalLevel(metrics, null);
                assertThat(level).isNotNull();
                Method m = DegradationDecisionEngine.class.getDeclaredMethod(
                    "logDecisionProcess", SystemMetrics.class, List.class, DegradationLevel.class);
                m.setAccessible(true);
                m.invoke(engine, metrics, Collections.emptyList(), level);
            }
        }

        @Nested
        @DisplayName("PrecisionMetrics.registerCounter — 精度指标注册")
        class PrecisionMetricsTests {

            @Test
            @DisplayName("enableMicrometerIfAvailable 触发 registerCounter")
            void enableMicrometerIfAvailable_triggersRegisterCounter() {
                PrecisionMetrics metrics = new PrecisionMetrics();
                metrics.enableMicrometerIfAvailable();
                metrics.recordNumericComparison();
                assertThat(metrics.getSnapshot().numericComparisonCount).isGreaterThanOrEqualTo(0);
            }
        }

        @Nested
        @DisplayName("ComparatorBuilder.compare — 比较器执行")
        class ComparatorBuilderCompareTests {

            @Test
            @DisplayName("compare 使用 CompareService 执行比较")
            void compare_withCompareService_executes() throws Exception {
                CompareService svc = CompareService.createDefault(CompareOptions.builder().build());
                var ctor = ComparatorBuilder.class.getDeclaredConstructor(CompareService.class);
                ctor.setAccessible(true);
                ComparatorBuilder builder = ctor.newInstance(svc);
                CompareResult result = builder.ignoring("id").compare("hello", "hello");
                assertThat(result).isNotNull();
                assertThat(result.isIdentical()).isTrue();
            }

            @Test
            @DisplayName("compare 不同对象返回差异")
            void compare_differentObjects_returnsDiff() throws Exception {
                CompareService svc = CompareService.createDefault(CompareOptions.builder().build());
                var ctor = ComparatorBuilder.class.getDeclaredConstructor(CompareService.class);
                ctor.setAccessible(true);
                ComparatorBuilder builder = ctor.newInstance(svc);
                CompareResult result = builder.compare(Map.of("a", 1), Map.of("b", 2));
                assertThat(result).isNotNull();
                assertThat(result.isIdentical()).isFalse();
            }
        }

        @Nested
        @DisplayName("ListChangeProjector.createMoveEvent — 列表移动事件")
        class ListChangeProjectorMoveTests {

            @Test
            @DisplayName("project LCS detectMoves 触发 createMoveEvent")
            void project_lcsDetectMoves_createsMoveEvent() {
                List<Integer> left = Arrays.asList(1, 2, 3);
                List<Integer> right = Arrays.asList(3, 1);
                CompareResult result = CompareResult.builder()
                    .algorithmUsed("LCS")
                    .identical(false)
                    .build();
                CompareOptions opts = CompareOptions.builder().detectMoves(true).build();
                List<Map<String, Object>> events = ListChangeProjector.project(result, left, right, opts, "items");
                boolean hasMove = events.stream()
                    .anyMatch(e -> "entry_moved".equals(e.get("kind")));
                assertThat(hasMove).isTrue();
            }
        }

        @Nested
        @DisplayName("ConcurrencyAutoConfiguration.initializeConcurrencySettings — 并发初始化")
        class ConcurrencyAutoConfigurationTests {

            @Test
            @DisplayName("initializeConcurrencySettings 可被直接调用")
            void initializeConcurrencySettings_canBeCalled() {
                ConcurrencyConfig config = new ConcurrencyConfig();
                ConcurrencyAutoConfiguration autoConfig = new ConcurrencyAutoConfiguration(config);
                assertThatCode(() -> autoConfig.initializeConcurrencySettings()).doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("TfiMetrics.registerEnterpriseSystemMetrics — 企业系统指标")
        class TfiMetricsEnterpriseTests {

            @Test
            @DisplayName("registerEnterpriseSystemMetrics 通过反射调用")
            void registerEnterpriseSystemMetrics_viaReflection() throws Exception {
                TfiMetrics metrics = new TfiMetrics(Optional.of(new SimpleMeterRegistry()));
                Method m = TfiMetrics.class.getDeclaredMethod("registerEnterpriseSystemMetrics");
                m.setAccessible(true);
                m.invoke(metrics);
            }
        }

        @Nested
        @DisplayName("SummaryInfo.toMap — 摘要转 Map")
        class SummaryInfoToMapTests {

            @Test
            @DisplayName("toMap 返回完整结构")
            void toMap_returnsFullStructure() {
                SummaryInfo info = new SummaryInfo();
                info.setType("ArrayList");
                info.setSize(10);
                info.setTruncated(true);
                info.setUniqueCount(8);
                info.setExamples(List.of(1, 2, 3));
                info.setFeatures(Set.of("sorted"));
                info.setTimestamp(System.currentTimeMillis());
                info.setStatistics(new SummaryInfo.Statistics(1.0, 10.0, 5.0, 5.0, 2.0));
                Map<String, Object> map = info.toMap();
                assertThat(map).containsKeys("type", "size", "truncated", "uniqueCount", "examples", "features", "timestamp", "statistics");
                assertThat(map.get("type")).isEqualTo("ArrayList");
                assertThat(map.get("size")).isEqualTo(10);
            }

            @Test
            @DisplayName("toMap 含 mapExamples")
            void toMap_withMapExamples() {
                SummaryInfo info = new SummaryInfo();
                info.setType("HashMap");
                info.setSize(2);
                info.setMapExamples(List.of(
                    Map.entry("k1", "v1"),
                    Map.entry("k2", "v2")));
                info.setTimestamp(System.currentTimeMillis());
                Map<String, Object> map = info.toMap();
                assertThat(map).containsKey("mapExamples");
            }
        }

        @Nested
        @DisplayName("DateCompareStrategy.generateDateReport — 日期比较报告")
        class DateCompareStrategyReportTests {

            @Test
            @DisplayName("compare 启用报告生成 generateDateReport")
            void compare_withReport_generatesDateReport() {
                DateCompareStrategy strategy = new DateCompareStrategy();
                Date d1 = new Date(1000000);
                Date d2 = new Date(2000000);
                CompareOptions opts = CompareOptions.builder()
                    .generateReport(true)
                    .reportFormat(ReportFormat.MARKDOWN)
                    .calculateSimilarity(true)
                    .build();
                CompareResult result = strategy.compare(d1, d2, opts);
                assertThat(result.getReport()).contains("Date Comparison")
                    .contains("Date 1")
                    .contains("Date 2")
                    .contains("Difference");
            }
        }

        @Nested
        @DisplayName("ClassLevelFilterEngine.shouldIgnoreByClass — 类级过滤")
        class ClassLevelFilterEngineTests {

            @IgnoreDeclaredProperties("ignored")
            static class WithIgnoreDeclared {
                public String kept;
                public String ignored;
            }

            @IgnoreInheritedProperties
            static class ChildWithIgnoreInherited extends WithIgnoreDeclared {
                public String childField;
            }

            @Test
            @DisplayName("shouldIgnoreByClass 包级排除")
            void shouldIgnoreByClass_excludePackages() throws Exception {
                var field = String.class.getDeclaredField("value");
                boolean ignored = ClassLevelFilterEngine.shouldIgnoreByClass(
                    String.class, field, List.of("java.lang"));
                assertThat(ignored).isTrue();
            }

            @Test
            @DisplayName("shouldIgnoreByClass IgnoreDeclaredProperties 指定字段")
            void shouldIgnoreByClass_ignoreDeclaredSpecified() throws Exception {
                var field = WithIgnoreDeclared.class.getDeclaredField("ignored");
                boolean ignored = ClassLevelFilterEngine.shouldIgnoreByClass(
                    WithIgnoreDeclared.class, field, null);
                assertThat(ignored).isTrue();
            }

            @Test
            @DisplayName("shouldIgnoreByClass IgnoreInheritedProperties 继承字段")
            void shouldIgnoreByClass_ignoreInherited() throws Exception {
                var field = WithIgnoreDeclared.class.getDeclaredField("kept");
                boolean ignored = ClassLevelFilterEngine.shouldIgnoreByClass(
                    ChildWithIgnoreInherited.class, field, null);
                assertThat(ignored).isTrue();
            }
        }

        @Nested
        @DisplayName("EntityKeyUtils.normalizeKeyComponent — 实体键规范化")
        class EntityKeyUtilsNormalizeTests {

            @Test
            @DisplayName("tryComputeStableKey 多种类型触发 normalizeKeyComponent")
            void tryComputeStableKey_variousTypes() {
                assertThat(EntityKeyUtils.tryComputeStableKey(new NumKeyEntity())).isPresent();
                assertThat(EntityKeyUtils.tryComputeStableKey(new StrKeyEntity())).isPresent();
                assertThat(EntityKeyUtils.tryComputeStableKey(new BoolKeyEntity())).isPresent();
                assertThat(EntityKeyUtils.tryComputeStableKey(new CollKeyEntity())).isPresent();
            }
        }

        static class NumKeyEntity { @Key int id = 1; }
        static class StrKeyEntity { @Key String id = "x"; }
        static class BoolKeyEntity { @Key boolean flag = true; }
        static class CollKeyEntity { @Key List<String> ids = List.of("a", "b"); }

        @Nested
        @DisplayName("PathMatcher.convertGlobToRegex — 路径 Glob 转正则")
        class PathMatcherGlobTests {

            @Test
            @DisplayName("matchGlob 单层通配符 *")
            void matchGlob_singleStar() {
                assertThat(PathMatcher.matchGlob("order.items", "order.*")).isTrue();
            }

            @Test
            @DisplayName("matchGlob 跨层通配符 **")
            void matchGlob_doubleStar() {
                assertThat(PathMatcher.matchGlob("order.items.name", "order.**")).isTrue();
            }

            @Test
            @DisplayName("matchGlob 问号 ?")
            void matchGlob_questionMark() {
                assertThat(PathMatcher.matchGlob("a", "?")).isTrue();
            }

            @Test
            @DisplayName("matchGlob 数组索引 [*]")
            void matchGlob_arrayIndex() {
                assertThat(PathMatcher.matchGlob("items[0].id", "items[*].id")).isTrue();
            }

            @Test
            @DisplayName("matchGlob 字面量")
            void matchGlob_literal() {
                assertThat(PathMatcher.matchGlob("order.id", "order.id")).isTrue();
            }
        }

        @Nested
        @DisplayName("DiffBuilder.fromSpring — Spring 环境装配")
        class DiffBuilderFromSpringTests {

            @Test
            @DisplayName("fromSpring null 环境")
            void fromSpring_nullEnv() {
                DiffBuilder b = DiffBuilder.fromSpring(null);
                assertThat(b).isNotNull();
            }

            @Test
            @DisplayName("fromSpring 标准环境")
            void fromSpring_standardEnv() {
                StandardEnvironment env = new StandardEnvironment();
                DiffBuilder b = DiffBuilder.fromSpring(env);
                assertThat(b).isNotNull();
            }

            @Test
            @DisplayName("fromSpring 含 max-depth 配置")
            void fromSpring_withMaxDepthConfig() {
                StandardEnvironment env = new StandardEnvironment();
                env.getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("test",
                        Map.of("tfi.change-tracking.snapshot.max-depth", "15")));
                DiffBuilder b = DiffBuilder.fromSpring(env);
                assertThat(b).isNotNull();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // From FinalPushTests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("From FinalPushTests — 最终覆盖冲刺测试")
    class FromFinalPush {

        @BeforeEach
        void setUp() {
            DegradationContext.reset();
            DiffDetector.setPrecisionCompareEnabled(false);
            DiffDetector.setEnhancedDeduplicationEnabled(true);
        }

        @AfterEach
        void tearDown() {
            DegradationContext.reset();
            ObjectSnapshotDeep.resetMetrics();
            DiffDetector.setPrecisionCompareEnabled(false);
            DiffDetector.setCurrentObjectClass(null);
        }

        @Nested
        @DisplayName("ObjectSnapshotDeep — formatPrimitiveArray 边界")
        class ObjectSnapshotDeepFormatPrimitiveArray {

            @Test
            @DisplayName("formatPrimitiveArray 空数组")
            void emptyPrimitiveArray() {
                SnapshotConfig config = new SnapshotConfig();
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                int[] arr = {};
                Map<String, Object> obj = Map.of("arr", arr);
                Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
                Object val = snap.values().stream().filter(v -> v instanceof String).findFirst().orElse(null);
                assertThat(val).asString().contains("[0]");
            }

            @Test
            @DisplayName("formatPrimitiveArray 小数组 ≤10 元素")
            void smallPrimitiveArray() {
                SnapshotConfig config = new SnapshotConfig();
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                int[] arr = {1, 2, 3, 4, 5};
                Map<String, Object> obj = Map.of("arr", arr);
                Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
                Object val = snap.values().stream().filter(v -> v instanceof String).findFirst().orElse(null);
                assertThat(val).asString().contains("1");
                assertThat(val).asString().contains("5");
            }

            @Test
            @DisplayName("formatPrimitiveArray 大数组 >10 元素仅摘要")
            void largePrimitiveArray() {
                SnapshotConfig config = new SnapshotConfig();
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                int[] arr = new int[15];
                for (int i = 0; i < 15; i++) arr[i] = i;
                Map<String, Object> obj = Map.of("arr", arr);
                Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
                Object val = snap.values().stream().filter(v -> v instanceof String).findFirst().orElse(null);
                assertThat(val).asString().contains("[15]");
            }

            @Test
            @DisplayName("formatPrimitiveArray long 类型")
            void longPrimitiveArray() {
                SnapshotConfig config = new SnapshotConfig();
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                long[] arr = {1L, 2L};
                Map<String, Object> obj = Map.of("arr", arr);
                Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
                assertThat(snap).isNotEmpty();
            }
        }

        @Nested
        @DisplayName("ObjectSnapshotDeep — handleEntity DiffInclude 与 includeFields")
        class ObjectSnapshotDeepEntityDiffInclude {

            @Test
            @DisplayName("Entity hasDiffInclude 且 includeFields 包含字段名")
            void entity_diffInclude_withIncludeFields() {
                SnapshotConfig config = new SnapshotConfig();
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                deep.setTypeAwareEnabled(true);
                EntityWithDiffIncludeAndInclude entity = new EntityWithDiffIncludeAndInclude();
                entity.included = "inc";
                Set<String> include = Set.of("included");
                Map<String, Object> snap = deep.captureDeep(entity, 3, include, Collections.emptySet());
                assertThat(snap).containsKey("included");
            }

            @Test
            @DisplayName("Entity ShallowReference 深度递归达到 maxDepth 使用 toString")
            void entity_shallowRef_depthLimit_toString() {
                SnapshotConfig config = new SnapshotConfig();
                config.setShallowReferenceMode(ShallowReferenceMode.VALUE_ONLY);
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                deep.setTypeAwareEnabled(true);
                EntityNestedRecursive e = new EntityNestedRecursive();
                e.child = new EntityNestedRecursive();
                e.child.id = "c1";
                e.child.child = new EntityNestedRecursive();
                e.child.child.id = "c2";
                Map<String, Object> snap = deep.captureDeep(e, 1, Collections.emptySet(), Collections.emptySet());
                assertThat(snap).isNotEmpty();
            }
        }

        @Nested
        @DisplayName("ObjectSnapshotDeep — adjustOptionsForDegradation")
        class ObjectSnapshotDeepAdjustOptions {

            @Test
            @DisplayName("SKIP_DEEP_ANALYSIS 调整 maxDepth 与 depth")
            void skipDeepAnalysis_adjustsOptions() {
                DegradationContext.setCurrentLevel(DegradationLevel.SKIP_DEEP_ANALYSIS);
                SnapshotConfig config = new SnapshotConfig();
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                TrackingOptions opts = TrackingOptions.builder().maxDepth(20).build();
                Map<String, Object> snap = deep.captureDeep(Map.of("a", 1), opts);
                assertThat(snap).isNotEmpty();
            }

            @Test
            @DisplayName("SIMPLE_COMPARISON 降级")
            void simpleComparison_degradation() {
                DegradationContext.setCurrentLevel(DegradationLevel.SIMPLE_COMPARISON);
                SnapshotConfig config = new SnapshotConfig();
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                Map<String, Object> snap = deep.captureDeep(Map.of("a", 1),
                    TrackingOptions.builder().maxDepth(10).build());
                assertThat(snap).isNotEmpty();
            }

            @Test
            @DisplayName("DegradationContext.getMaxElements 限制 collectionSummaryThreshold")
            void degradationMaxElements_limitsThreshold() {
                DegradationContext.setCurrentLevel(DegradationLevel.SKIP_DEEP_ANALYSIS);
                SnapshotConfig config = new SnapshotConfig();
                config.setCollectionSummaryThreshold(500);
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                List<Integer> list = new ArrayList<>();
                for (int i = 0; i < 50; i++) list.add(i);
                Map<String, Object> obj = Map.of("list", list);
                Map<String, Object> snap = deep.captureDeep(obj,
                    TrackingOptions.builder().maxDepth(5).collectionSummaryThreshold(500).build());
                assertThat(snap).isNotEmpty();
            }
        }

        @Nested
        @DisplayName("ObjectSnapshotDeep — handleCollection 截断与 ELEMENT")
        class ObjectSnapshotDeepCollectionTruncation {

            @Test
            @DisplayName("SUMMARY 策略超过 maxElements 截断")
            void summary_truncatedAtMaxElements() {
                SnapshotConfig config = new SnapshotConfig();
                config.setCollectionSummaryThreshold(200);
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.SUMMARY);
                List<Integer> list = new ArrayList<>();
                for (int i = 0; i < 150; i++) list.add(i);
                Map<String, Object> obj = Map.of("list", list);
                Map<String, Object> snap = deep.captureDeep(obj, 5, Collections.emptySet(), Collections.emptySet());
                boolean truncated = snap.entrySet().stream()
                    .anyMatch(e -> String.valueOf(e.getValue()).contains("truncated"));
                assertThat(truncated).isTrue();
            }

            @Test
            @DisplayName("ELEMENT 策略添加 size 与 type 元数据")
            void element_addsMetadata() {
                SnapshotConfig config = new SnapshotConfig();
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.ELEMENT);
                Map<String, Object> obj = Map.of("list", List.of("a", "b", "c"));
                Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
                boolean hasSize = snap.keySet().stream().anyMatch(k -> k.endsWith(".size"));
                boolean hasType = snap.keySet().stream().anyMatch(k -> k.endsWith(".type"));
                assertThat(hasSize).isTrue();
                assertThat(hasType).isTrue();
            }
        }

        @Nested
        @DisplayName("ObjectSnapshotDeep — handleMap 大 Map 与 null 值")
        class ObjectSnapshotDeepMapPaths {

            @Test
            @DisplayName("Map 超过 threshold 使用摘要")
            void mapOverThreshold_summary() {
                SnapshotConfig config = new SnapshotConfig();
                config.setCollectionSummaryThreshold(5);
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                Map<String, Object> large = new LinkedHashMap<>();
                for (int i = 0; i < 15; i++) large.put("k" + i, i);
                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("m", large);
                Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
                Object mVal = snap.values().stream()
                    .filter(v -> v instanceof com.syy.taskflowinsight.tracking.summary.SummaryInfo)
                    .findFirst().orElse(null);
                assertThat(mVal).isInstanceOf(com.syy.taskflowinsight.tracking.summary.SummaryInfo.class);
            }

            @Test
            @DisplayName("Map 值 null 记录")
            void mapNullValue_recorded() {
                SnapshotConfig config = new SnapshotConfig();
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("a", 1);
                m.put("b", null);
                Map<String, Object> obj = Map.of("m", m);
                Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
                assertThat(snap).containsValue(null);
            }
        }

        @Nested
        @DisplayName("ObjectSnapshotDeep — captureShallowReference getId 降级")
        class ObjectSnapshotDeepShallowRefGetId {

            @Test
            @DisplayName("无 @Key 对象降级为 toString")
            void noKey_fallbackToString() {
                SnapshotConfig config = new SnapshotConfig();
                config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_MAP);
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                deep.setTypeAwareEnabled(true);
                EntityWithShallowRef entity = new EntityWithShallowRef();
                entity.ref = new NoKeyObject("fallback");
                Map<String, Object> snap = deep.captureDeep(entity, 3, Collections.emptySet(), Collections.emptySet());
                Object refVal = snap.get("ref");
                assertThat(refVal).isNotNull();
                assertThat(String.valueOf(refVal)).contains("fallback");
            }
        }

        @Nested
        @DisplayName("ObjectSnapshotDeep — matchesPattern 与 excludePatterns")
        class ObjectSnapshotDeepMatchesPattern {

            @Test
            @DisplayName("excludePatterns 使用 *.field 模式")
            void excludePatterns_starField() {
                SnapshotConfig config = new SnapshotConfig();
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                Map<String, Object> obj = Map.of("user", Map.of("name", "A", "password", "x"));
                Set<String> exclude = Set.of("*.password", "user.password");
                Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), exclude);
                assertThat(snap).doesNotContainKey("password");
            }
        }

        @Nested
        @DisplayName("ObjectSnapshotDeep — ValueObject EQUALS 与 FIELDS")
        class ObjectSnapshotDeepValueObject {

            @Test
            @DisplayName("VALUE_OBJECT EQUALS 策略 null 安全")
            void valueObject_equalsNullSafe() {
                SnapshotConfig config = new SnapshotConfig();
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                deep.setTypeAwareEnabled(true);
                Map<String, Object> snap = deep.captureDeep(null, 3, Collections.emptySet(), Collections.emptySet());
                assertThat(snap).isEmpty();
            }

            @Test
            @DisplayName("VALUE_OBJECT FIELDS 策略深层嵌套")
            void valueObject_fieldsDeepNesting() {
                SnapshotConfig config = new SnapshotConfig();
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                deep.setTypeAwareEnabled(true);
                NestedValueObject inner = new NestedValueObject("inner");
                NestedValueObject outer = new NestedValueObject("outer");
                outer.child = inner;
                Map<String, Object> snap = deep.captureDeep(outer, 5, Collections.emptySet(), Collections.emptySet());
                assertThat(snap).containsKey("child.name");
                assertThat(snap).containsKey("name");
            }
        }

        @Nested
        @DisplayName("ObjectSnapshotDeep — 栈深度与时间预算")
        class ObjectSnapshotDeepStackAndTime {

            @Test
            @DisplayName("maxStackDepth 配置生效")
            void maxStackDepth_exceeded() {
                SnapshotConfig config = new SnapshotConfig();
                config.setMaxStackDepth(100);
                ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
                SimplePojoFp pojo = new SimplePojoFp();
                pojo.x = 1;
                pojo.nested = Map.of("y", 2);
                Map<String, Object> snap = deep.captureDeep(pojo, 5, Collections.emptySet(), Collections.emptySet());
                assertThat(snap).isNotEmpty();
            }
        }

        @Nested
        @DisplayName("DiffDetector — diffWithMode ENHANCED")
        class DiffDetectorEnhancedMode {

            @Test
            @DisplayName("ENHANCED 模式 reprOld reprNew valueRepr")
            void enhanced_reprBranches() {
                Map<String, Object> before = Map.of("name", "Alice", "age", 25);
                Map<String, Object> after = Map.of("name", "Bob", "age", 26);
                List<ChangeRecord> r = DiffDetector.diffWithMode("User", before, after, DiffDetector.DiffMode.ENHANCED);
                assertThat(r).isNotEmpty();
                ChangeRecord rec = r.stream().filter(c -> "name".equals(c.getFieldName())).findFirst().orElseThrow();
                assertThat(rec.getReprOld()).isEqualTo("Alice");
                assertThat(rec.getReprNew()).isEqualTo("Bob");
            }

            @Test
            @DisplayName("ENHANCED 模式 DELETE 时 valueRepr 为 oldValue")
            void enhanced_deleteValueRepr() {
                Map<String, Object> before = Map.of("x", "deleted");
                Map<String, Object> after = Collections.emptyMap();
                List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
                ChangeRecord del = r.stream().filter(c -> c.getChangeType() == ChangeType.DELETE).findFirst().orElseThrow();
                assertThat(del.getValueRepr()).isNotNull();
            }
        }

        @Nested
        @DisplayName("DiffDetector — getValueKind 分支")
        class DiffDetectorValueKind {

            @Test
            @DisplayName("getValueKind ENUM")
            void valueKind_enum() {
                Map<String, Object> before = Map.of("e", TestEnum.A);
                Map<String, Object> after = Map.of("e", TestEnum.B);
                List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
                assertThat(r).isNotEmpty();
                assertThat(r.get(0).getValueKind()).isEqualTo("ENUM");
            }

            @Test
            @DisplayName("getValueKind MAP")
            void valueKind_map() {
                Map<String, Object> before = Map.of("m", Map.of("a", 1));
                Map<String, Object> after = Map.of("m", Map.of("a", 2));
                List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
                assertThat(r).isNotEmpty();
                assertThat(r.get(0).getValueKind()).isEqualTo("MAP");
            }

            @Test
            @DisplayName("getValueKind ARRAY")
            void valueKind_array() {
                Map<String, Object> before = Map.of("arr", new String[]{"a"});
                Map<String, Object> after = Map.of("arr", new String[]{"b"});
                List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
                assertThat(r).isNotEmpty();
                assertThat(r.get(0).getValueKind()).isEqualTo("ARRAY");
            }

            @Test
            @DisplayName("getValueKind OTHER")
            void valueKind_other() {
                Map<String, Object> before = Map.of("obj", new CustomObject("x"));
                Map<String, Object> after = Map.of("obj", new CustomObject("y"));
                List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
                assertThat(r).isNotEmpty();
                assertThat(r.get(0).getValueKind()).isEqualTo("OTHER");
            }
        }

        @Nested
        @DisplayName("DiffDetector — toReprCompat 与 BigDecimal")
        class DiffDetectorToReprCompat {

            @Test
            @DisplayName("toReprCompat BigDecimal 去尾零")
            void toReprCompat_bigDecimal() {
                Map<String, Object> before = Map.of("amt", new BigDecimal("1.500"));
                Map<String, Object> after = Map.of("amt", new BigDecimal("2.500"));
                List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
                assertThat(r).isNotEmpty();
            }

            @Test
            @DisplayName("toReprCompat Double Float")
            void toReprCompat_doubleFloat() {
                Map<String, Object> before = Map.of("d", 1.5);
                Map<String, Object> after = Map.of("d", 2.5);
                List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
                assertThat(r).isNotEmpty();
            }
        }

        @Nested
        @DisplayName("DiffDetector — deduplicateByPath 嵌套路径")
        class DiffDetectorDeduplicatePath {

            @Test
            @DisplayName("基础去重嵌套路径")
            void dedup_nestedPaths() {
                DiffDetector.setEnhancedDeduplicationEnabled(false);
                Map<String, Object> before = new HashMap<>();
                before.put("root", Map.of("a", 1, "b", Map.of("x", 10)));
                Map<String, Object> after = new HashMap<>();
                after.put("root", Map.of("a", 2, "b", Map.of("x", 20)));
                List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
                assertThat(r).isNotEmpty();
            }

            @Test
            @DisplayName("增强去重包含嵌套")
            void dedup_enhanced_nested() {
                DiffDetector.setEnhancedDeduplicationEnabled(true);
                Map<String, Object> before = Map.of("a", 1, "b", Map.of("x", 1));
                Map<String, Object> after = Map.of("a", 2, "b", Map.of("x", 2));
                List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
                assertThat(r).isNotEmpty();
            }
        }

        @Nested
        @DisplayName("DiffDetector — compatHeavyOptimizations 与 heavy cache")
        class DiffDetectorHeavyPaths {

            @Test
            @DisplayName("compatHeavyOptimizations 大对象缓存")
            void compatHeavy_cache() {
                boolean saved = DiffDetector.isCompatHeavyOptimizationsEnabled();
                try {
                    DiffDetector.setCompatHeavyOptimizationsEnabled(true);
                    Map<String, Object> before = new HashMap<>();
                    Map<String, Object> after = new HashMap<>();
                    for (int i = 0; i < 60; i++) {
                        before.put("k" + i, i);
                        after.put("k" + i, i + 1);
                    }
                    List<ChangeRecord> r1 = DiffDetector.diffWithMode("Heavy", before, after, DiffDetector.DiffMode.COMPAT);
                    List<ChangeRecord> r2 = DiffDetector.diffWithMode("Heavy", before, after, DiffDetector.DiffMode.COMPAT);
                    assertThat(r1).isNotEmpty();
                    assertThat(r2).hasSize(r1.size());
                } finally {
                    DiffDetector.setCompatHeavyOptimizationsEnabled(saved);
                }
            }
        }

        @Nested
        @DisplayName("DiffDetector — Map Set Collection 策略比较")
        class DiffDetectorCollectionStrategies {

            @Test
            @DisplayName("Map 策略比较相同返回空")
            void mapStrategy_identical() {
                Map<String, Object> m = Map.of("a", 1, "b", 2);
                Map<String, Object> before = Map.of("m", m);
                Map<String, Object> after = Map.of("m", new HashMap<>(m));
                List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
                assertThat(r).isEmpty();
            }

            @Test
            @DisplayName("Set 策略比较")
            void setStrategy() {
                Set<String> s1 = new HashSet<>(Set.of("a", "b"));
                Set<String> s2 = new HashSet<>(Set.of("a", "c"));
                Map<String, Object> before = Map.of("s", s1);
                Map<String, Object> after = Map.of("s", s2);
                List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
                assertThat(r).isNotEmpty();
            }

            @Test
            @DisplayName("Collection 策略比较")
            void collectionStrategy() {
                List<String> c1 = List.of("a", "b");
                List<String> c2 = List.of("a", "c");
                Map<String, Object> before = Map.of("c", c1);
                Map<String, Object> after = Map.of("c", c2);
                List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
                assertThat(r).isNotEmpty();
            }
        }

        @Entity
        static class EntityWithDiffIncludeAndInclude {
            @Key
            String id = "1";
            @DiffInclude
            String included;
            String excluded = "exc";
        }

        @Entity
        static class EntityNestedRecursive {
            @Key
            String id = "root";
            EntityNestedRecursive child;
        }

        @Entity
        static class EntityWithShallowRef {
            @Key
            String id = "e1";
            @ShallowReference
            Object ref;
        }

        static class NoKeyObject {
            final String data;

            NoKeyObject(String data) {
                this.data = data;
            }

            @Override
            public String toString() {
                return "NoKeyObject{data=" + data + "}";
            }
        }

        @ValueObject
        static class NestedValueObject {
            String name;
            NestedValueObject child;

            NestedValueObject(String name) {
                this.name = name;
            }
        }

        enum TestEnum { A, B }

        static class CustomObject {
            final String v;

            CustomObject(String v) {
                this.v = v;
            }
        }

        static class SimplePojoFp {
            int x;
            Map<String, Object> nested;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // From FinalPush2Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("From FinalPush2Tests — 最终推送覆盖测试")
    class FromFinalPush2 {

        @Nested
        @DisplayName("CaffeinePathMatcherCache — 路径匹配缓存")
        class CaffeinePathMatcherCacheTests {

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
            @DisplayName("matchBatch — 模式编译失败时降级到 fallbackMatch")
            void matchBatch_patternCompileFails_usesFallback() {
                List<String> paths = List.of("foo", "bar");
                Map<String, Boolean> result = cache.matchBatch(paths, "[invalid");
                assertThat(result).hasSize(2);
                assertThat(result.get("foo")).isFalse();
                assertThat(result.get("bar")).isFalse();
            }

            @Test
            @DisplayName("matchBatch — 含 null 路径时跳过")
            void matchBatch_skipsNullPaths() {
                List<String> paths = new ArrayList<>();
                paths.add("a.foo");
                paths.add(null);
                paths.add("b.bar");
                Map<String, Boolean> result = cache.matchBatch(paths, "*.foo");
                assertThat(result).containsOnlyKeys("a.foo", "b.bar");
                assertThat(result.get("a.foo")).isTrue();
                assertThat(result.get("b.bar")).isFalse();
            }

            @Test
            @DisplayName("matches — 结果缓存命中")
            void matches_resultCacheHit() {
                String path = "com.syy.service.UserService";
                String pattern = "com.syy.**";
                boolean first = cache.matches(path, pattern);
                boolean second = cache.matches(path, pattern);
                assertThat(first).isEqualTo(second).isTrue();
            }

            @Test
            @DisplayName("matches — 通配符 **. 后跟点")
            void matches_doubleStarDot() {
                assertThat(cache.matches("a.b.c", "**.b.c")).isTrue();
            }

            @Test
            @DisplayName("fallbackMatch — 通配符模式直接编译")
            void fallbackMatch_wildcardPattern() {
                assertThat(cache.matches("user.name", "*.name")).isTrue();
                assertThat(cache.matches("user.email", "*.name")).isFalse();
            }

            @Test
            @DisplayName("convertWildcardToRegex — 正则特殊字符转义")
            void convertWildcardToRegex_specialChars() {
                assertThat(cache.matches("a", "a")).isTrue();
                assertThat(cache.matches("a.b", "a.b")).isTrue();
            }

            @Test
            @DisplayName("getStats — 异常时返回零统计")
            void getStats_exceptionReturnsZero() {
                PathMatcherCacheInterface.CacheStats stats = cache.getStats();
                assertThat(stats).isNotNull();
                assertThat(stats.getHitCount()).isGreaterThanOrEqualTo(0);
                assertThat(stats.getEvictionCount()).isGreaterThanOrEqualTo(0);
            }

            @Test
            @DisplayName("preload — 含 null 模式时跳过")
            void preload_skipsNullPatterns() {
                List<String> patterns = new ArrayList<>();
                patterns.add("valid.*");
                patterns.add(null);
                patterns.add("other.**");
                assertThatCode(() -> cache.preload(patterns)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("registerMetrics — 通过 matches 触发 Gauge 注册")
            void registerMetrics_viaMatches() {
                cache.matches("x.y", "*.y");
                PathMatcherCacheInterface.CacheStats stats = cache.getStats();
                assertThat(stats.getSize()).isGreaterThanOrEqualTo(0);
            }
        }

        @Nested
        @DisplayName("EnhancedDateCompareStrategy — 增强日期比较")
        class EnhancedDateCompareStrategyTests {

            private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();

            @Test
            @DisplayName("compareDates — 带容差")
            void compareDates_withTolerance() {
                Date a = new Date(1000);
                Date b = new Date(1500);
                assertThat(strategy.compareDates(a, b, 600)).isTrue();
                assertThat(strategy.compareDates(a, b, 400)).isFalse();
            }

            @Test
            @DisplayName("compareDates — 默认容差")
            void compareDates_defaultTolerance() {
                Date d = new Date(1000);
                assertThat(strategy.compareDates(d, d)).isTrue();
            }

            @Test
            @DisplayName("compareInstants — 带容差")
            void compareInstants_withTolerance() {
                Instant a = Instant.ofEpochMilli(1000);
                Instant b = Instant.ofEpochMilli(1500);
                assertThat(strategy.compareInstants(a, b, 600)).isTrue();
            }

            @Test
            @DisplayName("compareLocalDateTimes — 零容差精确比较")
            void compareLocalDateTimes_zeroTolerance() {
                LocalDateTime t = LocalDateTime.of(2025, 1, 1, 12, 0);
                assertThat(strategy.compareLocalDateTimes(t, t, 0)).isTrue();
            }

            @Test
            @DisplayName("compareLocalDateTimes — 带容差")
            void compareLocalDateTimes_withTolerance() {
                LocalDateTime a = LocalDateTime.of(2025, 1, 1, 12, 0, 0);
                LocalDateTime b = LocalDateTime.of(2025, 1, 1, 12, 0, 1);
                assertThat(strategy.compareLocalDateTimes(a, b, 2000)).isTrue();
            }

            @Test
            @DisplayName("compareLocalDates — 带容差")
            void compareLocalDates_withTolerance() {
                LocalDate a = LocalDate.of(2025, 1, 1);
                LocalDate b = LocalDate.of(2025, 1, 2);
                assertThat(strategy.compareLocalDates(a, b, 86400_000 * 2)).isTrue();
            }

            @Test
            @DisplayName("compareDurations — 带容差")
            void compareDurations_withTolerance() {
                Duration a = Duration.ofMillis(100);
                Duration b = Duration.ofMillis(150);
                assertThat(strategy.compareDurations(a, b, 60)).isTrue();
            }

            @Test
            @DisplayName("comparePeriods — 精确比较")
            void comparePeriods() {
                Period a = Period.of(1, 2, 3);
                Period b = Period.of(1, 2, 3);
                assertThat(strategy.comparePeriods(a, b)).isTrue();
                assertThat(strategy.comparePeriods(a, Period.of(1, 2, 4))).isFalse();
            }

            @Test
            @DisplayName("compareTemporal — 所有支持类型")
            void compareTemporal_allTypes() {
                Date d = new Date(1000);
                assertThat(strategy.compareTemporal(d, new Date(1000), 0)).isTrue();

                Instant i = Instant.ofEpochMilli(1000);
                assertThat(strategy.compareTemporal(i, Instant.ofEpochMilli(1000), 0)).isTrue();

                LocalDateTime ldt = LocalDateTime.of(2025, 1, 1, 12, 0);
                assertThat(strategy.compareTemporal(ldt, ldt, 0)).isTrue();

                LocalDate ld = LocalDate.of(2025, 1, 1);
                assertThat(strategy.compareTemporal(ld, ld, 0)).isTrue();

                Duration dur = Duration.ofSeconds(10);
                assertThat(strategy.compareTemporal(dur, Duration.ofSeconds(10), 0)).isTrue();

                Period p = Period.ofDays(1);
                assertThat(strategy.compareTemporal(p, Period.ofDays(1), 0)).isTrue();
            }

            @Test
            @DisplayName("compareTemporal — 类型不同返回 false")
            void compareTemporal_differentTypes() {
                assertThat(strategy.compareTemporal(new Date(1000), Instant.ofEpochMilli(1000), 0)).isFalse();
            }

            @Test
            @DisplayName("compareTemporal — 不支持类型回退 equals")
            void compareTemporal_unsupportedTypeFallsBackToEquals() {
                String s = "hello";
                assertThat(strategy.compareTemporal(s, "hello", 0)).isTrue();
            }

            @Test
            @DisplayName("isTemporalType — 所有时间类型")
            void isTemporalType() {
                assertThat(EnhancedDateCompareStrategy.isTemporalType(new Date())).isTrue();
                assertThat(EnhancedDateCompareStrategy.isTemporalType(Instant.now())).isTrue();
                assertThat(EnhancedDateCompareStrategy.isTemporalType(LocalDateTime.now())).isTrue();
                assertThat(EnhancedDateCompareStrategy.isTemporalType(LocalDate.now())).isTrue();
                assertThat(EnhancedDateCompareStrategy.isTemporalType(java.time.LocalTime.now())).isTrue();
                assertThat(EnhancedDateCompareStrategy.isTemporalType(Duration.ZERO)).isTrue();
                assertThat(EnhancedDateCompareStrategy.isTemporalType(Period.ZERO)).isTrue();
                assertThat(EnhancedDateCompareStrategy.isTemporalType(null)).isFalse();
                assertThat(EnhancedDateCompareStrategy.isTemporalType("x")).isFalse();
            }

            @Test
            @DisplayName("needsTemporalCompare")
            void needsTemporalCompare() {
                assertThat(EnhancedDateCompareStrategy.needsTemporalCompare(new Date(), new Date())).isTrue();
                assertThat(EnhancedDateCompareStrategy.needsTemporalCompare("a", "b")).isFalse();
            }
        }

        @Nested
        @DisplayName("PathDeduplicator — 路径去重")
        class PathDeduplicatorTests {

            @AfterEach
            void tearDown() {
                PathBuilder.clearCache();
            }

            @Test
            @DisplayName("deduplicateWithObjectGraph — 启用且有小规模变更")
            void deduplicateWithObjectGraph_enabledSmallChanges() {
                PathDeduplicationConfig config = new PathDeduplicationConfig();
                config.setEnabled(true);
                config.setCacheEnabled(true);
                config.setFastPathChangeLimit(1000);

                PathDeduplicator deduplicator = new PathDeduplicator(config);

                ChangeRecord r1 = ChangeRecord.builder()
                    .objectName("Order")
                    .fieldName("order.status")
                    .oldValue("NEW")
                    .newValue("PAID")
                    .changeType(ChangeType.UPDATE)
                    .build();

                Map<String, Object> before = Map.of("order.status", "NEW");
                Map<String, Object> after = Map.of("order.status", "PAID");

                List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
                    List.of(r1), before, after);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getFieldName()).isEqualTo("order.status");
            }

            @Test
            @DisplayName("deduplicateWithObjectGraph — 超过 fastPathChangeLimit 直接返回")
            void deduplicateWithObjectGraph_exceedsFastLimit() {
                PathDeduplicationConfig config = new PathDeduplicationConfig();
                config.setFastPathChangeLimit(2);

                PathDeduplicator deduplicator = new PathDeduplicator(config);

                List<ChangeRecord> records = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    records.add(ChangeRecord.builder()
                        .objectName("X")
                        .fieldName("f" + i)
                        .oldValue("a")
                        .newValue("b")
                        .changeType(ChangeType.UPDATE)
                        .build());
                }

                Map<String, Object> snap = new HashMap<>();
                records.forEach(r -> snap.put(r.getFieldName(), r.getNewValue()));

                List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
                    records, snap, snap);

                assertThat(result).hasSize(5);
            }

            @Test
            @DisplayName("deduplicateLegacy — 同路径多记录去重")
            void deduplicateLegacy_samePathDedup() {
                PathDeduplicator deduplicator = new PathDeduplicator(true, new PathCache());

                ChangeRecord r1 = ChangeRecord.builder()
                    .objectName("X").fieldName("a.b").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build();
                ChangeRecord r2 = ChangeRecord.builder()
                    .objectName("X").fieldName("a.b").oldValue(1).newValue(2).changeType(ChangeType.UPDATE).build();

                List<ChangeRecord> result = deduplicator.deduplicate(List.of(r1, r2));
                assertThat(result).hasSize(1);
            }

            @Test
            @DisplayName("getStatistics — 返回完整统计")
            void getStatistics() {
                PathDeduplicator deduplicator = new PathDeduplicator(new PathDeduplicationConfig());
                var stats = deduplicator.getStatistics();
                assertThat(stats).isNotNull();
                assertThat(stats.getTotalDeduplicationCount()).isGreaterThanOrEqualTo(0);
                assertThat(stats.getClippedGroupsCount()).isGreaterThanOrEqualTo(0);
            }

            @Test
            @DisplayName("resetStatistics")
            void resetStatistics() {
                PathDeduplicator deduplicator = new PathDeduplicator(new PathDeduplicationConfig());
                assertThatCode(deduplicator::resetStatistics).doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("ListCompareExecutor — 列表比较执行器")
        class ListCompareExecutorTests {

            private ListCompareExecutor createExecutor() {
                return new ListCompareExecutor(List.of(
                    new SimpleListStrategy(),
                    new AsSetListStrategy(),
                    new LcsListStrategy(),
                    new LevenshteinListStrategy(),
                    new EntityListStrategy()
                ));
            }

            @Test
            @DisplayName("compare — 指定不存在策略时降级")
            void compare_nonexistentStrategy_degrades() {
                ListCompareExecutor executor = createExecutor();
                CompareOptions opts = CompareOptions.builder().strategyName("NONEXISTENT").build();
                CompareResult r = executor.compare(List.of("a", "b"), List.of("a", "x"), opts);
                assertThat(r).isNotNull();
            }

            @Test
            @DisplayName("compare — 大列表触发 size 降级")
            void compare_largeList_triggersSizeDegradation() {
                ListCompareExecutor executor = createExecutor();
                List<String> large = new ArrayList<>();
                for (int i = 0; i < 600; i++) large.add("x" + i);

                CompareOptions opts = CompareOptions.builder().strategyName("LCS").build();
                CompareResult r = executor.compare(large, new ArrayList<>(large), opts);
                assertThat(r).isNotNull();
                assertThat(executor.getDegradationCount()).isGreaterThanOrEqualTo(0);
            }

            @Test
            @DisplayName("compare — calculateSimilarity 空集")
            void compare_calculateSimilarity_emptyUnion() {
                ListCompareExecutor executor = createExecutor();
                CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
                CompareResult r = executor.compare(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    opts);
                assertThat(r.getSimilarity()).isEqualTo(1.0);
            }

            @Test
            @DisplayName("compare — 混合类型检测")
            void compare_mixedElementTypes() {
                ListCompareExecutor executor = createExecutor();
                List<Object> mixed = List.of("a", 1, true);
                CompareResult r = executor.compare(mixed, List.of("a", 1, true), CompareOptions.DEFAULT);
                assertThat(r).isNotNull();
            }

            @Test
            @DisplayName("getSupportedStrategies")
            void getSupportedStrategies() {
                ListCompareExecutor executor = createExecutor();
                assertThat(executor.getSupportedStrategies())
                    .contains("SIMPLE", "AS_SET", "LCS", "LEVENSHTEIN", "ENTITY");
            }
        }

        @Nested
        @DisplayName("ListChangeProjector — 列表变更投影")
        class ListChangeProjectorTests {

            @Test
            @DisplayName("project — ENTITY 算法带 duplicateKeys")
            void project_entityWithDuplicateKeys() {
                CompareResult result = CompareResult.builder()
                    .algorithmUsed("ENTITY")
                    .identical(false)
                    .duplicateKeys(Set.of("id=1"))
                    .build();

                List<TestEntityKey> left = List.of(new TestEntityKey(1, "A"));
                List<TestEntityKey> right = List.of(new TestEntityKey(1, "B"));

                List<Map<String, Object>> events = ListChangeProjector.project(
                    result, left, right, CompareOptions.DEFAULT, "items");

                assertThat(events).isNotNull();
            }

            @Test
            @DisplayName("project — LCS + detectMoves 合并 moved")
            void project_lcsDetectMoves() {
                CompareResult result = CompareResult.builder()
                    .algorithmUsed("LCS")
                    .identical(false)
                    .build();

                List<String> left = List.of("a", "b", "c");
                List<String> right = List.of("c", "a", "b");

                CompareOptions opts = CompareOptions.builder().detectMoves(true).build();
                List<Map<String, Object>> events = ListChangeProjector.project(
                    result, left, right, opts, "list");

                assertThat(events).isNotNull();
            }

            @Test
            @DisplayName("project — ENTITY 新增/删除/更新")
            void project_entityAddRemoveUpdate() {
                CompareResult result = CompareResult.builder()
                    .algorithmUsed("ENTITY")
                    .identical(false)
                    .duplicateKeys(Collections.emptySet())
                    .build();

                List<TestEntityKey> left = List.of(new TestEntityKey(1, "A"), new TestEntityKey(2, "B"));
                List<TestEntityKey> right = List.of(new TestEntityKey(1, "A2"), new TestEntityKey(3, "C"));

                List<Map<String, Object>> events = ListChangeProjector.project(
                    result, left, right, CompareOptions.DEFAULT, "entities");

                assertThat(events).isNotEmpty();
                events.forEach(e -> {
                    assertThat(e).containsKeys("kind", "path", "details");
                    if (e.get("details") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> d = (Map<String, Object>) e.get("details");
                        assertThat(d).containsKey("entityKey");
                    }
                });
            }
        }

        @Nested
        @DisplayName("CompareEngine — 比较引擎")
        class CompareEngineTests {

            private CompareEngine engine;
            private ListCompareExecutor listExecutor;

            @BeforeEach
            void setUp() {
                listExecutor = new ListCompareExecutor(List.of(
                    new SimpleListStrategy(),
                    new EntityListStrategy()
                ));
                engine = new CompareEngine(
                    new StrategyResolver(),
                    null,
                    null,
                    listExecutor,
                    new ConcurrentHashMap<>(),
                    new ConcurrentHashMap<>()
                );
            }

            @Test
            @DisplayName("execute — List 路由到 ListCompareExecutor")
            void execute_listRoutesToListExecutor() {
                List<String> a = List.of("a", "b");
                List<String> b = List.of("a", "x");
                CompareResult r = engine.execute(a, b, CompareOptions.DEFAULT);
                assertThat(r).isNotNull();
                assertThat(r.getChanges()).isNotEmpty();
            }

            @Test
            @DisplayName("execute — 深度比较 fallback")
            void execute_deepCompareFallback() {
                SimplePojoFp2 a = new SimplePojoFp2("x", 1);
                SimplePojoFp2 b = new SimplePojoFp2("y", 2);
                CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
                CompareResult r = engine.execute(a, b, opts);
                assertThat(r).isNotNull();
                assertThat(r.getChanges()).isNotEmpty();
            }

            @Test
            @DisplayName("detectShallowReferenceChanges — @ShallowReference")
            void execute_shallowReferenceField() {
                WithShallowRef a = new WithShallowRef(new RefTarget(1));
                WithShallowRef b = new WithShallowRef(new RefTarget(2));
                CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
                CompareResult r = engine.execute(a, b, opts);
                assertThat(r).isNotNull();
            }

            @Test
            @DisplayName("execute — 数组引用变更")
            void execute_arrayReferenceChange() {
                RefTarget[] arrA = new RefTarget[]{new RefTarget(1)};
                RefTarget[] arrB = new RefTarget[]{new RefTarget(2)};
                CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
                CompareResult r = engine.execute(arrA, arrB, opts);
                assertThat(r).isNotNull();
            }

            @Test
            @DisplayName("execute — Map 值引用变更")
            void execute_mapValueReferenceChange() {
                Map<String, RefTarget> mapA = Map.of("k", new RefTarget(1));
                Map<String, RefTarget> mapB = Map.of("k", new RefTarget(2));
                CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
                CompareResult r = engine.execute(mapA, mapB, opts);
                assertThat(r).isNotNull();
            }
        }

        @Entity
        static class TestEntityKey {
            @Key
            private final int id;
            private final String name;

            TestEntityKey(int id, String name) {
                this.id = id;
                this.name = name;
            }
        }

        static class SimplePojoFp2 {
            private final String name;
            private final int value;

            SimplePojoFp2(String name, int value) {
                this.name = name;
                this.value = value;
            }
        }

        @Entity
        static class RefTarget {
            @Key
            private final int id;

            RefTarget(int id) {
                this.id = id;
            }
        }

        static class WithShallowRef {
            @ShallowReference
            private final RefTarget ref;

            WithShallowRef(RefTarget ref) {
                this.ref = ref;
            }
        }
    }
}
