package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.cache.ReflectionMetaCache;
import com.syy.taskflowinsight.tracking.cache.StrategyCache;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareStrategy;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.determinism.StableSorter;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import com.syy.taskflowinsight.tracking.ssot.path.PathNavigator;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;
import com.syy.taskflowinsight.tracking.summary.CollectionSummary;
import com.syy.taskflowinsight.tracking.summary.SummaryInfo;
import com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil;
import com.syy.taskflowinsight.spi.DefaultComparisonProvider;
import com.syy.taskflowinsight.spi.DefaultRenderProvider;
import com.syy.taskflowinsight.spi.DefaultTrackingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deep coverage tests for tracking (root), cache, ssot, summary, concurrent, and spi packages.
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Remaining Packages — Deep Coverage Tests")
class RemainingPackagesCoverageTests {

    // ──────────────────────────────────────────────────────────────
    //  ChangeTracker (tracking root)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ChangeTracker")
    class ChangeTrackerTests {

        @BeforeEach
        @AfterEach
        void clear() {
            ChangeTracker.clearAllTracking();
        }

        @Test
        @DisplayName("track with name null — skips")
        void track_nameNull_skips() {
            assertThatCode(() -> ChangeTracker.track(null, Map.of("x", 1)))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("track with target null — skips")
        void track_targetNull_skips() {
            assertThatCode(() -> ChangeTracker.track("obj", null))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("trackAll — batch")
        void trackAll_batch() {
            ChangeTracker.trackAll(Map.of(
                "a", Map.of("x", 1),
                "b", Map.of("y", 2)));
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("trackAll — null/empty")
        void trackAll_nullEmpty_skips() {
            assertThatCode(() -> ChangeTracker.trackAll(null)).doesNotThrowAnyException();
            assertThatCode(() -> ChangeTracker.trackAll(Map.of())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("getTrackedCount")
        void getTrackedCount() {
            ChangeTracker.track("x", Map.of("a", 1));
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("getMaxTrackedObjects")
        void getMaxTrackedObjects() {
            assertThat(ChangeTracker.getMaxTrackedObjects()).isPositive();
        }

        @Test
        @DisplayName("clearBySessionId")
        void clearBySessionId() {
            ChangeTracker.track("x", Map.of("a", 1));
            ChangeTracker.clearBySessionId("s1");
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("TrackingException constructors")
        void trackingException_constructors() {
            ChangeTracker.TrackingException e1 = new ChangeTracker.TrackingException("msg");
            assertThat(e1.getMessage()).isEqualTo("msg");
            ChangeTracker.TrackingException e2 = new ChangeTracker.TrackingException("msg", e1);
            assertThat(e2.getCause()).isSameAs(e1);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  SessionAwareChangeTracker
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SessionAwareChangeTracker")
    class SessionAwareChangeTrackerTests {

        @AfterEach
        void clear() {
            SessionAwareChangeTracker.clearAll();
        }

        @Test
        @DisplayName("getCurrentSessionChanges — no session returns empty")
        void getCurrentSessionChanges_noSession_returnsEmpty() {
            assertThat(SessionAwareChangeTracker.getCurrentSessionChanges()).isEmpty();
        }

        @Test
        @DisplayName("getSessionChanges — unknown session returns empty")
        void getSessionChanges_unknown_returnsEmpty() {
            assertThat(SessionAwareChangeTracker.getSessionChanges("unknown")).isEmpty();
        }

        @Test
        @DisplayName("getAllChanges — returns all")
        void getAllChanges_returnsAll() {
            assertThat(SessionAwareChangeTracker.getAllChanges()).isEmpty();
        }

        @Test
        @DisplayName("clearSession — returns count")
        void clearSession_returnsCount() {
            int count = SessionAwareChangeTracker.clearSession("s1");
            assertThat(count).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("getSessionMetadata — unknown returns null")
        void getSessionMetadata_unknown_returnsNull() {
            assertThat(SessionAwareChangeTracker.getSessionMetadata("unknown")).isNull();
        }

        @Test
        @DisplayName("getAllSessionMetadata")
        void getAllSessionMetadata() {
            assertThat(SessionAwareChangeTracker.getAllSessionMetadata()).isNotNull();
        }

        @Test
        @DisplayName("cleanupExpiredSessions")
        void cleanupExpiredSessions() {
            int removed = SessionAwareChangeTracker.cleanupExpiredSessions(0);
            assertThat(removed).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("SessionMetadata — getters and setters")
        void sessionMetadata_gettersSetters() {
            SessionAwareChangeTracker.SessionMetadata meta =
                new SessionAwareChangeTracker.SessionMetadata("s1");
            assertThat(meta.getSessionId()).isEqualTo("s1");
            assertThat(meta.getCreatedTime()).isPositive();
            meta.incrementChangeCount();
            assertThat(meta.getChangeCount()).isEqualTo(1);
            meta.updateLastActivity();
            meta.recordObjectChange("Order");
            assertThat(meta.getObjectChangeCounts()).containsKey("Order");
            assertThat(meta.getAge()).isGreaterThanOrEqualTo(0);
            assertThat(meta.getIdleTime()).isGreaterThanOrEqualTo(0);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  ChangeType
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ChangeType")
    class ChangeTypeTests {

        @Test
        @DisplayName("enum values")
        void enumValues() {
            assertThat(ChangeType.values()).containsExactly(
                ChangeType.CREATE, ChangeType.UPDATE, ChangeType.DELETE, ChangeType.MOVE);
        }

        @Test
        @DisplayName("valueOf")
        void valueOf() {
            assertThat(ChangeType.valueOf("CREATE")).isEqualTo(ChangeType.CREATE);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  StableSorter (determinism)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("StableSorter")
    class StableSorterTests {

        @Test
        @DisplayName("sortByFieldChange — empty")
        void sortByFieldChange_empty() {
            List<FieldChange> sorted = StableSorter.sortByFieldChange(List.of());
            assertThat(sorted).isEmpty();
        }

        @Test
        @DisplayName("sortByFieldChange — single element")
        void sortByFieldChange_single() {
            FieldChange fc = FieldChange.builder()
                .fieldName("x")
                .fieldPath("entity[1].x")
                .changeType(ChangeType.UPDATE)
                .build();
            List<FieldChange> sorted = StableSorter.sortByFieldChange(List.of(fc));
            assertThat(sorted).hasSize(1);
        }

        @Test
        @DisplayName("sortByFieldChange — multiple by key/field/priority")
        void sortByFieldChange_multiple() {
            FieldChange create = FieldChange.builder()
                .fieldPath("entity[1].a")
                .changeType(ChangeType.CREATE)
                .build();
            FieldChange update = FieldChange.builder()
                .fieldPath("entity[1].b")
                .changeType(ChangeType.UPDATE)
                .build();
            List<FieldChange> sorted = StableSorter.sortByFieldChange(List.of(create, update));
            assertThat(sorted).hasSize(2);
            assertThat(sorted).containsExactlyInAnyOrder(create, update);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  ReflectionMetaCache (tracking/cache)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ReflectionMetaCache")
    class ReflectionMetaCacheTests {

        @Test
        @DisplayName("enabled — getFieldsOrResolve")
        void enabled_getFieldsOrResolve() {
            ReflectionMetaCache cache = new ReflectionMetaCache(true, 100, 60_000);
            List<Field> fields = cache.getFieldsOrResolve(String.class, ReflectionMetaCache::defaultFieldResolver);
            assertThat(fields).isNotNull();
        }

        @Test
        @DisplayName("disabled — pass-through")
        void disabled_passthrough() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            List<Field> fields = cache.getFieldsOrResolve(String.class, ReflectionMetaCache::defaultFieldResolver);
            assertThat(fields).isNotNull();
        }

        @Test
        @DisplayName("defaultFieldResolver")
        void defaultFieldResolver() {
            List<Field> fields = ReflectionMetaCache.defaultFieldResolver(String.class);
            assertThat(fields).isNotNull();
        }

        @Test
        @DisplayName("getHitRate — disabled returns -1")
        void getHitRate_disabled() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            assertThat(cache.getHitRate()).isEqualTo(-1.0);
        }

        @Test
        @DisplayName("getRequestCount — disabled returns 0")
        void getRequestCount_disabled() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            assertThat(cache.getRequestCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("getCacheSize — disabled returns 0")
        void getCacheSize_disabled() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            assertThat(cache.getCacheSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("invalidateAll")
        void invalidateAll() {
            ReflectionMetaCache cache = new ReflectionMetaCache(true, 100, 60_000);
            assertThatCode(cache::invalidateAll).doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  StrategyCache (tracking/cache)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("StrategyCache")
    class StrategyCacheTests {

        @Test
        @DisplayName("enabled — getOrResolve")
        void enabled_getOrResolve() {
            StrategyCache cache = new StrategyCache(true, 100, 60_000);
            CompareStrategy<?> strategy = cache.getOrResolve(String.class, t -> null);
            assertThat(strategy).isNull();
        }

        @Test
        @DisplayName("disabled — pass-through")
        void disabled_passthrough() {
            StrategyCache cache = new StrategyCache(false, 100, 60_000);
            CompareStrategy<?> strategy = cache.getOrResolve(String.class, t -> null);
            assertThat(strategy).isNull();
        }

        @Test
        @DisplayName("getHitRate — disabled returns -1")
        void getHitRate_disabled() {
            StrategyCache cache = new StrategyCache(false, 100, 60_000);
            assertThat(cache.getHitRate()).isEqualTo(-1.0);
        }

        @Test
        @DisplayName("invalidateAll")
        void invalidateAll() {
            StrategyCache cache = new StrategyCache(true, 100, 60_000);
            assertThatCode(cache::invalidateAll).doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  PathUtils (tracking/ssot/path)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PathUtils")
    class PathUtilsTests {

        @Test
        @DisplayName("buildEntityPath")
        void buildEntityPath() {
            assertThat(PathUtils.buildEntityPath("1")).isEqualTo("entity[1]");
            assertThat(PathUtils.buildEntityPath("1", "field")).isEqualTo("entity[1].field");
        }

        @Test
        @DisplayName("buildEntityPathWithDup")
        void buildEntityPathWithDup() {
            assertThat(PathUtils.buildEntityPathWithDup("1", 0)).isEqualTo("entity[1#0]");
        }

        @Test
        @DisplayName("buildMapValuePath")
        void buildMapValuePath() {
            assertThat(PathUtils.buildMapValuePath("key")).isEqualTo("map[key]");
        }

        @Test
        @DisplayName("buildMapKeyAttrPath")
        void buildMapKeyAttrPath() {
            assertThat(PathUtils.buildMapKeyAttrPath("k")).isEqualTo("map[KEY:k]");
        }

        @Test
        @DisplayName("buildListIndexPath")
        void buildListIndexPath() {
            assertThat(PathUtils.buildListIndexPath(0)).isEqualTo("[0]");
            assertThat(PathUtils.buildListIndexPath(0, "x")).isEqualTo("[0].x");
        }

        @Test
        @DisplayName("parse — entity path")
        void parse_entityPath() {
            PathUtils.KeyFieldPair pair = PathUtils.parse("entity[1].field");
            assertThat(pair.key()).isEqualTo("entity[1]");
            assertThat(pair.field()).isEqualTo("field");
        }

        @Test
        @DisplayName("parse — non-entity path")
        void parse_nonEntityPath() {
            PathUtils.KeyFieldPair pair = PathUtils.parse("foo.bar");
            assertThat(pair.key()).isEqualTo("-");
            assertThat(pair.field()).isEqualTo("foo.bar");
        }

        @Test
        @DisplayName("unescape")
        void unescape() {
            assertThat(PathUtils.unescape("a\\:b")).isEqualTo("a:b");
            assertThat(PathUtils.unescape(null)).isNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  PathNavigator (tracking/ssot/path)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PathNavigator")
    class PathNavigatorTests {

        @Test
        @DisplayName("resolve — null root")
        void resolve_nullRoot() {
            assertThat(PathNavigator.resolve(null, "path")).isNull();
        }

        @Test
        @DisplayName("resolve — null path")
        void resolve_nullPath() {
            assertThat(PathNavigator.resolve("obj", null)).isNull();
        }

        @Test
        @DisplayName("resolve — simple field")
        void resolve_simpleField() {
            Object root = new SimpleBean();
            assertThat(PathNavigator.resolve(root, "field")).isEqualTo("value");
        }

        @Test
        @DisplayName("isAnnotatedField — null")
        void isAnnotatedField_null() {
            assertThat(PathNavigator.isAnnotatedField(null, "x", Deprecated.class)).isFalse();
        }

        static class SimpleBean {
            public String field = "value";
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  EntityKeyUtils (tracking/ssot/key)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EntityKeyUtils")
    class EntityKeyUtilsTests {

        @Test
        @DisplayName("tryComputeStableKey — null")
        void tryComputeStableKey_null() {
            assertThat(EntityKeyUtils.tryComputeStableKey(null)).isEmpty();
        }

        @Test
        @DisplayName("computeStableKeyOrUnresolved — simple object")
        void computeStableKeyOrUnresolved() {
            String key = EntityKeyUtils.computeStableKeyOrUnresolved("hello");
            assertThat(key).isNotNull();
        }

        @Test
        @DisplayName("computeStableKeyOrNull — null")
        void computeStableKeyOrNull_null() {
            assertThat(EntityKeyUtils.computeStableKeyOrNull(null)).isNull();
        }

        @Test
        @DisplayName("tryComputeCompactKey — null")
        void tryComputeCompactKey_null() {
            assertThat(EntityKeyUtils.tryComputeCompactKey(null)).isEmpty();
        }

        @Test
        @DisplayName("computeCompactKeyOrUnresolved")
        void computeCompactKeyOrUnresolved() {
            String key = EntityKeyUtils.computeCompactKeyOrUnresolved("x");
            assertThat(key).isNotNull();
        }

        @Test
        @DisplayName("collectKeyFields — class without @Key")
        void collectKeyFields_noKey() {
            List<Field> fields = EntityKeyUtils.collectKeyFields(String.class);
            assertThat(fields).isEmpty();
        }

        @Test
        @DisplayName("computeReferenceIdentifier — null")
        void computeReferenceIdentifier_null() {
            assertThat(EntityKeyUtils.computeReferenceIdentifier(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("computeReferenceIdentifier — simple object")
        void computeReferenceIdentifier_simple() {
            String ref = EntityKeyUtils.computeReferenceIdentifier("x");
            assertThat(ref).isNotNull();
        }

        @Test
        @DisplayName("UNRESOLVED constant")
        void unresolvedConstant() {
            assertThat(EntityKeyUtils.UNRESOLVED).isEqualTo("__UNRESOLVED__");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  CollectionSummary & SummaryInfo (tracking/summary)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CollectionSummary")
    class CollectionSummaryTests {

        private final CollectionSummary summarizer = new CollectionSummary();

        @Test
        @DisplayName("getInstance")
        void getInstance() {
            assertThat(CollectionSummary.getInstance()).isNotNull();
        }

        @Test
        @DisplayName("shouldSummarize — disabled")
        void shouldSummarize_disabled() {
            summarizer.setEnabled(false);
            assertThat(summarizer.shouldSummarize(List.of(1, 2, 3))).isFalse();
            summarizer.setEnabled(true);
        }

        @Test
        @DisplayName("shouldSummarize — null")
        void shouldSummarize_null() {
            assertThat(summarizer.shouldSummarize(null)).isFalse();
        }

        @Test
        @DisplayName("summarize — array")
        void summarize_array() {
            SummaryInfo info = summarizer.summarize(new int[]{1, 2, 3});
            assertThat(info).isNotNull();
            assertThat(info.getSize()).isEqualTo(3);
        }

        @Test
        @DisplayName("summarize — unsupported type")
        void summarize_unsupported() {
            SummaryInfo info = summarizer.summarize(new Object());
            assertThat(info.getFeatures()).contains("unsupported");
        }

        @Test
        @DisplayName("setters")
        void setters() {
            summarizer.setMaxSize(50);
            summarizer.setMaxExamples(5);
            summarizer.setSensitiveWords(List.of("secret"));
        }
    }

    @Nested
    @DisplayName("SummaryInfo")
    class SummaryInfoTests {

        @Test
        @DisplayName("empty")
        void empty() {
            SummaryInfo info = SummaryInfo.empty();
            assertThat(info.getType()).isEqualTo("empty");
            assertThat(info.getSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("unsupported")
        void unsupported() {
            SummaryInfo info = SummaryInfo.unsupported(String.class);
            assertThat(info.getType()).isEqualTo("String");
            assertThat(info.getFeatures()).contains("unsupported");
        }

        @Test
        @DisplayName("toMap")
        void toMap() {
            SummaryInfo info = SummaryInfo.empty();
            Map<String, Object> map = info.toMap();
            assertThat(map).containsKey("type");
            assertThat(map).containsKey("timestamp");
        }

        @Test
        @DisplayName("toCompactString")
        void toCompactString() {
            SummaryInfo info = new SummaryInfo();
            info.setType("List");
            info.setSize(10);
            assertThat(info.toCompactString()).contains("List").contains("size=10");
        }

        @Test
        @DisplayName("Statistics toMap")
        void statistics_toMap() {
            SummaryInfo.Statistics stats = new SummaryInfo.Statistics(1.0, 1.0, 1.0, 1.0, null);
            assertThat(stats.toMap()).containsKey("min");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  ConcurrentRetryUtil (concurrent)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ConcurrentRetryUtil")
    class ConcurrentRetryUtilTests {

        @Test
        @DisplayName("executeWithRetry — success")
        void executeWithRetry_success() {
            String result = ConcurrentRetryUtil.executeWithRetry(() -> "ok");
            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("executeWithRetry — null operation throws")
        void executeWithRetry_nullOperation_throws() {
            assertThatThrownBy(() -> ConcurrentRetryUtil.executeWithRetry((java.util.function.Supplier<String>) null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("executeWithRetry — Runnable")
        void executeWithRetry_runnable() {
            final boolean[] ran = {false};
            ConcurrentRetryUtil.executeWithRetry(() -> ran[0] = true);
            assertThat(ran[0]).isTrue();
        }

        @Test
        @DisplayName("executeWithRetry — custom params")
        void executeWithRetry_customParams() {
            String result = ConcurrentRetryUtil.executeWithRetry(() -> "x", 3, 1);
            assertThat(result).isEqualTo("x");
        }

        @Test
        @DisplayName("executeWithRetryOrSummary — success")
        void executeWithRetryOrSummary_success() {
            String result = ConcurrentRetryUtil.executeWithRetryOrSummary(
                () -> "ok",
                () -> "fallback");
            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("executeWithRetryOrSummary — null throws")
        void executeWithRetryOrSummary_null_throws() {
            assertThatThrownBy(() -> ConcurrentRetryUtil.executeWithRetryOrSummary(
                () -> "x",
                null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("isRetryable — CME")
        void isRetryable_cme() {
            assertThat(ConcurrentRetryUtil.isRetryable(new java.util.ConcurrentModificationException())).isTrue();
        }

        @Test
        @DisplayName("isRetryable — CME in cause")
        void isRetryable_cmeInCause() {
            Exception e = new Exception("wrap", new java.util.ConcurrentModificationException());
            assertThat(ConcurrentRetryUtil.isRetryable(e)).isTrue();
        }

        @Test
        @DisplayName("isRetryable — non-CME")
        void isRetryable_nonCme() {
            assertThat(ConcurrentRetryUtil.isRetryable(new RuntimeException())).isFalse();
        }

        @Test
        @DisplayName("getGlobalStats")
        void getGlobalStats() {
            assertThat(ConcurrentRetryUtil.getGlobalStats()).isNotNull();
        }

        @Test
        @DisplayName("RetryStats")
        void retryStats() {
            ConcurrentRetryUtil.RetryStats stats = ConcurrentRetryUtil.getGlobalStats();
            assertThat(stats.getSuccessRate()).isBetween(0.0, 1.0);
            assertThat(stats.toString()).contains("RetryStats");
        }

        @Test
        @DisplayName("setDefaultRetryParams")
        void setDefaultRetryParams() {
            assertThatCode(() -> ConcurrentRetryUtil.setDefaultRetryParams(5, 10))
                .doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  SPI (DefaultComparisonProvider, DefaultTrackingProvider, DefaultRenderProvider)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DefaultComparisonProvider — Deep")
    class DefaultComparisonProviderDeepTests {

        @Test
        @DisplayName("compare with options")
        void compare_withOptions() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            CompareResult result = provider.compare("a", "b",
                com.syy.taskflowinsight.tracking.compare.CompareOptions.DEFAULT);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("threeWayMerge — throws")
        void threeWayMerge_throws() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            assertThatThrownBy(() -> provider.threeWayMerge("a", "b", "c"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("toString")
        void toString_() {
            assertThat(new DefaultComparisonProvider().toString()).contains("DefaultComparisonProvider");
        }
    }

    @Nested
    @DisplayName("DefaultTrackingProvider — Deep")
    class DefaultTrackingProviderDeepTests {

        @AfterEach
        void clear() {
            ChangeTracker.clearAllTracking();
        }

        @Test
        @DisplayName("trackAll")
        void trackAll() {
            DefaultTrackingProvider provider = new DefaultTrackingProvider();
            assertThatThrownBy(() -> provider.trackAll(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("toString")
        void toString_() {
            assertThat(new DefaultTrackingProvider().toString()).contains("DefaultTrackingProvider");
        }
    }

    @Nested
    @DisplayName("DefaultRenderProvider — Deep")
    class DefaultRenderProviderDeepTests {

        @Test
        @DisplayName("render — null result")
        void render_nullResult() {
            DefaultRenderProvider provider = new DefaultRenderProvider();
            assertThat(provider.render(null, "standard")).isEqualTo("[null]");
        }

        @Test
        @DisplayName("render — RenderStyle object")
        void render_renderStyleObject() {
            DefaultRenderProvider provider = new DefaultRenderProvider();
            CompareResult result = CompareResult.identical();
            String rendered = provider.render(result, com.syy.taskflowinsight.tracking.render.RenderStyle.standard());
            assertThat(rendered).isNotNull();
        }

        @Test
        @DisplayName("render — unknown style type")
        void render_unknownStyleType() {
            DefaultRenderProvider provider = new DefaultRenderProvider();
            CompareResult result = CompareResult.identical();
            String rendered = provider.render(result, 123);
            assertThat(rendered).isNotNull();
        }

        @Test
        @DisplayName("toString")
        void toString_() {
            assertThat(new DefaultRenderProvider().toString()).contains("DefaultRenderProvider");
        }
    }
}
