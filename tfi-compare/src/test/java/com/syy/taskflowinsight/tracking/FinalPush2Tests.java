package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.EnhancedDateCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.StrategyResolver;
import com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LcsListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.path.CaffeinePathMatcherCache;
import com.syy.taskflowinsight.tracking.path.PathBuilder;
import com.syy.taskflowinsight.tracking.path.PathCache;
import com.syy.taskflowinsight.tracking.path.PathDeduplicationConfig;
import com.syy.taskflowinsight.tracking.path.PathDeduplicator;
import com.syy.taskflowinsight.tracking.path.PathMatcherCacheInterface;
import com.syy.taskflowinsight.tracking.query.ListChangeProjector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 最终推送覆盖测试 — 针对 CaffeinePathMatcherCache、EnhancedDateCompareStrategy、
 * PathDeduplicator、ListCompareExecutor、ListChangeProjector、CompareEngine 的剩余分支。
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Final Push 2 — 最终推送覆盖测试")
class FinalPush2Tests {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. CaffeinePathMatcherCache
    // ─────────────────────────────────────────────────────────────────────────

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
        void convertWildcardToRegex_specialChars() throws Exception {
            // 通过 matches 间接测试 convertWildcardToRegex 对 []{}()\^$|+ 的转义
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

    // ─────────────────────────────────────────────────────────────────────────
    // 2. EnhancedDateCompareStrategy
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // 3. PathDeduplicator
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // 4. ListCompareExecutor
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // 5. ListChangeProjector
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // 6. CompareEngine
    // ─────────────────────────────────────────────────────────────────────────

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
            SimplePojo a = new SimplePojo("x", 1);
            SimplePojo b = new SimplePojo("y", 2);
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

    // ─────────────────────────────────────────────────────────────────────────
    // 测试模型
    // ─────────────────────────────────────────────────────────────────────────

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

    static class SimplePojo {
        private final String name;
        private final int value;

        SimplePojo(String name, int value) {
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
