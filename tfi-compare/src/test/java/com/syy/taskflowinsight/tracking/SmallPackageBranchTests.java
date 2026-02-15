package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ObjectType;
import com.syy.taskflowinsight.annotation.ValueObject;
import com.syy.taskflowinsight.annotation.ValueObjectCompareStrategy;
import com.syy.taskflowinsight.registry.DiffRegistry;
import com.syy.taskflowinsight.registry.ObjectTypeResolver;
import com.syy.taskflowinsight.registry.ValueObjectStrategyResolver;
import com.syy.taskflowinsight.spi.ComparisonProvider;
import com.syy.taskflowinsight.spi.DefaultComparisonProvider;
import com.syy.taskflowinsight.spi.DefaultRenderProvider;
import com.syy.taskflowinsight.spi.DefaultTrackingProvider;
import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareStrategy;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.comparators.BigDecimalScaleComparator;
import com.syy.taskflowinsight.tracking.compare.comparators.CaseInsensitiveComparator;
import com.syy.taskflowinsight.tracking.compare.comparators.EnumSemanticComparator;
import com.syy.taskflowinsight.tracking.compare.comparators.TrimCaseComparator;
import com.syy.taskflowinsight.tracking.compare.comparators.UrlCanonicalComparator;
import com.syy.taskflowinsight.tracking.compare.entity.EntityChangeGroup;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityOperation;
import com.syy.taskflowinsight.tracking.cache.ReflectionMetaCache;
import com.syy.taskflowinsight.tracking.cache.StrategyCache;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.render.RenderStyle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive branch coverage tests for four smaller packages in tfi-compare:
 * spi, tracking/cache, registry, tracking/compare/comparators.
 *
 * <p>Targets all uncovered if/else/switch branches in each package.
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@DisplayName("Small Package Branch Coverage — spi, cache, registry, comparators")
class SmallPackageBranchTests {

    // ═══════════════════════════════════════════════════════════════
    // SPI Package
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DefaultRenderProvider — all branches")
    class DefaultRenderProviderBranchTests {

        private final DefaultRenderProvider provider = new DefaultRenderProvider();

        @Test
        @DisplayName("render: null result → returns [null]")
        void render_nullResult_returnsNullPlaceholder() {
            assertThat(provider.render(null, "standard")).isEqualTo("[null]");
        }

        @Test
        @DisplayName("render: EntityListDiffResult → delegates to renderer")
        void render_entityListDiffResult_delegatesToRenderer() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("e[1]")
                .operation(EntityOperation.MODIFY)
                .addChange(FieldChange.builder()
                    .fieldName("f").oldValue(1).newValue(2)
                    .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE).build())
                .build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(List.of(group))
                .build();
            String out = provider.render(result, "standard");
            assertThat(out).isNotBlank().contains("Modified");
        }

        @Test
        @DisplayName("render: non-EntityListDiffResult → returns type message")
        void render_otherType_returnsTypeMessage() {
            String out = provider.render("hello", "standard");
            assertThat(out).contains("String").contains("rendering not supported");
        }

        @Test
        @DisplayName("render: style null → uses standard")
        void render_styleNull_usesStandard() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("e[1]")
                .operation(EntityOperation.MODIFY)
                .addChange(FieldChange.builder()
                    .fieldName("f").oldValue(1).newValue(2)
                    .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE).build())
                .build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(List.of(group))
                .build();
            String out = provider.render(result, null);
            assertThat(out).isNotBlank();
        }

        @Test
        @DisplayName("render: style RenderStyle object → uses it")
        void render_styleRenderStyleObject_usesIt() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("e[1]")
                .operation(EntityOperation.MODIFY)
                .addChange(FieldChange.builder()
                    .fieldName("f").oldValue(1).newValue(2)
                    .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE).build())
                .build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(List.of(group))
                .build();
            String out = provider.render(result, RenderStyle.simple());
            assertThat(out).isNotBlank();
        }

        @Test
        @DisplayName("render: style String simple → uses simple")
        void render_styleStringSimple_usesSimple() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("e[1]")
                .operation(EntityOperation.MODIFY)
                .addChange(FieldChange.builder()
                    .fieldName("f").oldValue(1).newValue(2)
                    .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE).build())
                .build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(List.of(group))
                .build();
            String out = provider.render(result, "simple");
            assertThat(out).isNotBlank();
        }

        @Test
        @DisplayName("render: style String detailed → uses detailed")
        void render_styleStringDetailed_usesDetailed() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("e[1]")
                .operation(EntityOperation.MODIFY)
                .addChange(FieldChange.builder()
                    .fieldName("f").oldValue(1).newValue(2)
                    .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE).build())
                .build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(List.of(group))
                .build();
            String out = provider.render(result, "detailed");
            assertThat(out).isNotBlank();
        }

        @Test
        @DisplayName("render: style String standard → uses standard")
        void render_styleStringStandard_usesStandard() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("e[1]")
                .operation(EntityOperation.MODIFY)
                .addChange(FieldChange.builder()
                    .fieldName("f").oldValue(1).newValue(2)
                    .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE).build())
                .build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(List.of(group))
                .build();
            String out = provider.render(result, "standard");
            assertThat(out).isNotBlank();
        }

        @Test
        @DisplayName("render: style unknown type → uses standard")
        void render_styleUnknownType_usesStandard() {
            EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("e[1]")
                .operation(EntityOperation.MODIFY)
                .addChange(FieldChange.builder()
                    .fieldName("f").oldValue(1).newValue(2)
                    .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE).build())
                .build();
            EntityListDiffResult result = EntityListDiffResult.builder()
                .groups(List.of(group))
                .build();
            String out = provider.render(result, 42);
            assertThat(out).isNotBlank();
        }

        @Test
        @DisplayName("priority returns 0")
        void priority_returnsZero() {
            assertThat(provider.priority()).isEqualTo(0);
        }

        @Test
        @DisplayName("toString contains fallback")
        void toString_containsFallback() {
            assertThat(provider.toString()).contains("DefaultRenderProvider").contains("fallback");
        }
    }

    @Nested
    @DisplayName("DefaultComparisonProvider — all branches")
    class DefaultComparisonProviderBranchTests {

        private final DefaultComparisonProvider provider = new DefaultComparisonProvider();

        @Test
        @DisplayName("compare: success path")
        void compare_success_returnsResult() {
            CompareResult r = provider.compare("a", "b");
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare: identical objects")
        void compare_identical_returnsIdentical() {
            String s = "same";
            CompareResult r = provider.compare(s, s);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("compare(options): null options uses DEFAULT")
        void compare_optionsNull_usesDefault() {
            CompareResult r = provider.compare("a", "b", null);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("compare(options): non-null options")
        void compare_optionsNonNull_usesOptions() {
            CompareResult r = provider.compare("a", "b", CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("similarity: identical result")
        void similarity_identical_returnsOne() {
            assertThat(provider.similarity("x", "x")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("similarity: changeCount 0")
        void similarity_changeCountZero_returnsOne() {
            assertThat(provider.similarity("a", "a")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("similarity: changeCount > 0")
        void similarity_changeCountPositive_returnsFraction() {
            Map<String, Object> a = Map.of("x", 1, "y", 2);
            Map<String, Object> b = Map.of("x", 10, "y", 20);
            double sim = provider.similarity(a, b);
            assertThat(sim).isBetween(0.0, 1.0).isLessThan(1.0);
        }

        @Test
        @DisplayName("priority returns 0")
        void priority_returnsZero() {
            assertThat(provider.priority()).isEqualTo(0);
        }

        @Test
        @DisplayName("toString contains fallback")
        void toString_containsFallback() {
            assertThat(provider.toString()).contains("DefaultComparisonProvider").contains("fallback");
        }
    }

    @Nested
    @DisplayName("DefaultTrackingProvider — all branches")
    class DefaultTrackingProviderBranchTests {

        @AfterEach
        void tearDown() {
            ChangeTracker.clearAllTracking();
        }

        @Test
        @DisplayName("track: success path")
        void track_success_noException() {
            DefaultTrackingProvider p = new DefaultTrackingProvider();
            p.track("obj", "value");
            assertThat(p.changes()).isNotNull();
        }

        @Test
        @DisplayName("changes: success path")
        void changes_success_returnsList() {
            DefaultTrackingProvider p = new DefaultTrackingProvider();
            List<ChangeRecord> list = p.changes();
            assertThat(list).isNotNull();
        }

        @Test
        @DisplayName("clear: success path")
        void clear_success_noException() {
            DefaultTrackingProvider p = new DefaultTrackingProvider();
            p.clear();
        }

        @Test
        @DisplayName("priority returns 0")
        void priority_returnsZero() {
            assertThat(new DefaultTrackingProvider().priority()).isEqualTo(0);
        }

        @Test
        @DisplayName("toString contains fallback")
        void toString_containsFallback() {
            assertThat(new DefaultTrackingProvider().toString())
                .contains("DefaultTrackingProvider").contains("fallback");
        }
    }

    @Nested
    @DisplayName("TrackingProvider — default method branches")
    class TrackingProviderDefaultMethodBranchTests {

        private final TrackingProvider provider = new DefaultTrackingProvider();

        @AfterEach
        void tearDown() {
            ChangeTracker.clearAllTracking();
        }

        @Test
        @DisplayName("trackAll: null targets throws NPE")
        void trackAll_nullTargets_throwsNpe() {
            assertThatThrownBy(() -> provider.trackAll(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("targets");
        }

        @Test
        @DisplayName("trackAll: empty map succeeds")
        void trackAll_emptyMap_succeeds() {
            provider.trackAll(Collections.emptyMap());
        }

        @Test
        @DisplayName("withTracked: null action does not throw")
        void withTracked_nullAction_doesNotThrow() {
            provider.withTracked("x", "val", null);
        }

        @Test
        @DisplayName("withTracked: non-null action runs")
        void withTracked_nonNullAction_runs() {
            List<String> ran = new ArrayList<>();
            provider.withTracked("x", "val", () -> ran.add("ok"));
            assertThat(ran).containsExactly("ok");
        }
    }

    @Nested
    @DisplayName("ComparisonProvider — default similarity branches")
    class ComparisonProviderDefaultSimilarityBranchTests {

        private final ComparisonProvider provider = new ComparisonProvider() {
            @Override
            public CompareResult compare(Object before, Object after) {
                return CompareResult.identical();
            }
        };

        @Test
        @DisplayName("similarity: both null → 1.0")
        void similarity_bothNull_returnsOne() {
            assertThat(provider.similarity(null, null)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("similarity: left null → 0.0")
        void similarity_leftNull_returnsZero() {
            assertThat(provider.similarity(null, "x")).isEqualTo(0.0);
        }

        @Test
        @DisplayName("similarity: right null → 0.0")
        void similarity_rightNull_returnsZero() {
            assertThat(provider.similarity("x", null)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("similarity: equal → 1.0")
        void similarity_equal_returnsOne() {
            assertThat(provider.similarity("a", "a")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("similarity: not equal → 0.0")
        void similarity_notEqual_returnsZero() {
            assertThat(provider.similarity("a", "b")).isEqualTo(0.0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // tracking/cache Package
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ReflectionMetaCache — all branches")
    class ReflectionMetaCacheBranchTests {

        @Test
        @DisplayName("constructor: enabled true → cache created")
        void constructor_enabledTrue_cacheCreated() {
            ReflectionMetaCache cache = new ReflectionMetaCache(true, 100, 60_000);
            List<java.lang.reflect.Field> fields = cache.getFieldsOrResolve(
                String.class, ReflectionMetaCache::defaultFieldResolver);
            assertThat(fields).isNotNull();
        }

        @Test
        @DisplayName("constructor: enabled false → pass-through")
        void constructor_enabledFalse_passThrough() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            List<java.lang.reflect.Field> fields = cache.getFieldsOrResolve(
                String.class, ReflectionMetaCache::defaultFieldResolver);
            assertThat(fields).isNotNull();
        }

        @Test
        @DisplayName("getFieldsOrResolve: enabled false → resolver called")
        void getFieldsOrResolve_disabled_callsResolver() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            List<java.lang.reflect.Field> f = cache.getFieldsOrResolve(
                Object.class, ReflectionMetaCache::defaultFieldResolver);
            assertThat(f).isNotNull();
        }

        @Test
        @DisplayName("getFieldsOrResolve: cache hit")
        void getFieldsOrResolve_cacheHit_returnsCached() {
            ReflectionMetaCache cache = new ReflectionMetaCache(true, 100, 60_000);
            cache.getFieldsOrResolve(String.class, ReflectionMetaCache::defaultFieldResolver);
            List<java.lang.reflect.Field> f2 = cache.getFieldsOrResolve(
                String.class, cl -> { throw new RuntimeException("should not be called"); });
            assertThat(f2).isNotNull();
        }

        @Test
        @DisplayName("getFieldsOrResolve: cache miss then put")
        void getFieldsOrResolve_cacheMiss_putsAndReturns() {
            ReflectionMetaCache cache = new ReflectionMetaCache(true, 100, 60_000);
            List<java.lang.reflect.Field> f = cache.getFieldsOrResolve(
                HashMap.class, ReflectionMetaCache::defaultFieldResolver);
            assertThat(f).isNotNull();
        }

        @Test
        @DisplayName("getFieldsOrResolve: resolver returns null → no put")
        void getFieldsOrResolve_resolverReturnsNull_noPut() {
            ReflectionMetaCache cache = new ReflectionMetaCache(true, 100, 60_000);
            List<java.lang.reflect.Field> f = cache.getFieldsOrResolve(
                String.class, cl -> null);
            assertThat(f).isNull();
        }

        @Test
        @DisplayName("getHitRate: disabled → -1.0")
        void getHitRate_disabled_returnsMinusOne() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            assertThat(cache.getHitRate()).isEqualTo(-1.0);
        }

        @Test
        @DisplayName("getHitRate: enabled")
        void getHitRate_enabled_returnsRate() {
            ReflectionMetaCache cache = new ReflectionMetaCache(true, 100, 60_000);
            cache.getFieldsOrResolve(String.class, ReflectionMetaCache::defaultFieldResolver);
            assertThat(cache.getHitRate()).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("getRequestCount: disabled → 0")
        void getRequestCount_disabled_returnsZero() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            assertThat(cache.getRequestCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("getCacheSize: disabled → 0")
        void getCacheSize_disabled_returnsZero() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            assertThat(cache.getCacheSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("invalidateAll: disabled → no-op")
        void invalidateAll_disabled_noOp() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            cache.invalidateAll();
        }

        @Test
        @DisplayName("invalidateAll: enabled → clears")
        void invalidateAll_enabled_clears() {
            ReflectionMetaCache cache = new ReflectionMetaCache(true, 100, 60_000);
            cache.getFieldsOrResolve(String.class, ReflectionMetaCache::defaultFieldResolver);
            cache.invalidateAll();
            assertThat(cache.getCacheSize()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("StrategyCache — all branches")
    class StrategyCacheBranchTests {

        @Test
        @DisplayName("constructor: enabled true")
        void constructor_enabledTrue_cacheCreated() {
            StrategyCache cache = new StrategyCache(true, 100, 60_000);
            CompareStrategy<?> s = cache.getOrResolve(String.class, t -> null);
            assertThat(s).isNull();
        }

        @Test
        @DisplayName("constructor: enabled false")
        void constructor_enabledFalse_passThrough() {
            StrategyCache cache = new StrategyCache(false, 100, 60_000);
            CompareStrategy<?> s = cache.getOrResolve(String.class, t -> null);
            assertThat(s).isNull();
        }

        @Test
        @DisplayName("getOrResolve: disabled → resolver called")
        void getOrResolve_disabled_callsResolver() {
            StrategyCache cache = new StrategyCache(false, 100, 60_000);
            CompareStrategy<?>[] called = new CompareStrategy<?>[1];
            cache.getOrResolve(Map.class, t -> {
                called[0] = new com.syy.taskflowinsight.tracking.compare.MapCompareStrategy();
                return called[0];
            });
            assertThat(called[0]).isNotNull();
        }

        @Test
        @DisplayName("getOrResolve: cache hit")
        void getOrResolve_cacheHit_returnsCached() {
            StrategyCache cache = new StrategyCache(true, 100, 60_000);
            CompareStrategy<?> strat = new com.syy.taskflowinsight.tracking.compare.MapCompareStrategy();
            cache.getOrResolve(Map.class, t -> strat);
            CompareStrategy<?> s2 = cache.getOrResolve(Map.class, t -> { throw new RuntimeException("no"); });
            assertThat(s2).isSameAs(strat);
        }

        @Test
        @DisplayName("getOrResolve: resolver returns null → no put")
        void getOrResolve_resolverReturnsNull_noPut() {
            StrategyCache cache = new StrategyCache(true, 100, 60_000);
            CompareStrategy<?> s = cache.getOrResolve(String.class, t -> null);
            assertThat(s).isNull();
        }

        @Test
        @DisplayName("getHitRate: disabled → -1.0")
        void getHitRate_disabled_returnsMinusOne() {
            StrategyCache cache = new StrategyCache(false, 100, 60_000);
            assertThat(cache.getHitRate()).isEqualTo(-1.0);
        }

        @Test
        @DisplayName("getRequestCount: disabled → 0")
        void getRequestCount_disabled_returnsZero() {
            StrategyCache cache = new StrategyCache(false, 100, 60_000);
            assertThat(cache.getRequestCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("invalidateAll: disabled → no-op")
        void invalidateAll_disabled_noOp() {
            StrategyCache cache = new StrategyCache(false, 100, 60_000);
            cache.invalidateAll();
        }

        @Test
        @DisplayName("invalidateAll: enabled → clears")
        void invalidateAll_enabled_clears() {
            StrategyCache cache = new StrategyCache(true, 100, 60_000);
            CompareStrategy<?> strat = new com.syy.taskflowinsight.tracking.compare.MapCompareStrategy();
            cache.getOrResolve(Map.class, t -> strat);
            cache.invalidateAll();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // registry Package
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DiffRegistry — all branches")
    class DiffRegistryBranchTests {

        @BeforeEach
        void setUp() {
            DiffRegistry.clear();
        }

        @Test
        @DisplayName("registerEntity: null class throws NPE")
        void registerEntity_nullClass_throwsNpe() {
            assertThatThrownBy(() -> DiffRegistry.registerEntity(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("registerValueObject: null clazz throws NPE")
        void registerValueObject_nullClazz_throwsNpe() {
            assertThatThrownBy(() -> DiffRegistry.registerValueObject(null, ValueObjectCompareStrategy.FIELDS))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("registerValueObject: null strategy throws NPE")
        void registerValueObject_nullStrategy_throwsNpe() {
            assertThatThrownBy(() -> DiffRegistry.registerValueObject(String.class, null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("getRegisteredStrategy: unregistered → AUTO")
        void getRegisteredStrategy_unregistered_returnsAuto() {
            assertThat(DiffRegistry.getRegisteredStrategy(String.class))
                .isEqualTo(ValueObjectCompareStrategy.AUTO);
        }

        @Test
        @DisplayName("getRegisteredStrategy: registered → returns strategy")
        void getRegisteredStrategy_registered_returnsStrategy() {
            DiffRegistry.registerValueObject(TestVo.class, ValueObjectCompareStrategy.EQUALS);
            assertThat(DiffRegistry.getRegisteredStrategy(TestVo.class))
                .isEqualTo(ValueObjectCompareStrategy.EQUALS);
        }

        @Test
        @DisplayName("unregister: existing → removedType non-null")
        void unregister_existing_removes() {
            DiffRegistry.registerEntity(TestEntity.class);
            DiffRegistry.unregister(TestEntity.class);
            assertThat(DiffRegistry.getRegisteredType(TestEntity.class)).isNull();
        }

        @Test
        @DisplayName("unregister: non-existing → no exception")
        void unregister_nonExisting_noException() {
            DiffRegistry.unregister(String.class);
        }

        static class TestEntity { }
        static class TestVo { }
    }

    @Nested
    @DisplayName("ObjectTypeResolver — all branches")
    class ObjectTypeResolverBranchTests {

        @BeforeEach
        void setUp() {
            ObjectTypeResolver.clearCache();
            DiffRegistry.clear();
        }

        @Test
        @DisplayName("resolveType(object): null → BASIC_TYPE")
        void resolveType_nullObject_returnsBasicType() {
            assertThat(ObjectTypeResolver.resolveType((Object) null)).isEqualTo(ObjectType.BASIC_TYPE);
        }

        @Test
        @DisplayName("resolveType(class): null → BASIC_TYPE")
        void resolveType_nullClass_returnsBasicType() {
            assertThat(ObjectTypeResolver.resolveType((Class<?>) null)).isEqualTo(ObjectType.BASIC_TYPE);
        }

        @Test
        @DisplayName("@Entity → ENTITY")
        void entityAnnotation_returnsEntity() {
            assertThat(ObjectTypeResolver.resolveType(AnnotatedEntity.class)).isEqualTo(ObjectType.ENTITY);
        }

        @Test
        @DisplayName("@ValueObject → VALUE_OBJECT")
        void valueObjectAnnotation_returnsValueObject() {
            assertThat(ObjectTypeResolver.resolveType(AnnotatedValueObject.class)).isEqualTo(ObjectType.VALUE_OBJECT);
        }

        @Test
        @DisplayName("hasKeyFields → ENTITY")
        void hasKeyFields_returnsEntity() {
            assertThat(ObjectTypeResolver.resolveType(WithKeyField.class)).isEqualTo(ObjectType.ENTITY);
        }

        @Test
        @DisplayName("programmatic registration → overrides")
        void programmaticRegistration_overrides() {
            DiffRegistry.registerEntity(TestOrder.class);
            ObjectTypeResolver.clearCache();
            assertThat(ObjectTypeResolver.resolveType(TestOrder.class)).isEqualTo(ObjectType.ENTITY);
        }

        @Test
        @DisplayName("primitive/wrapper → BASIC_TYPE")
        void primitiveOrWrapper_returnsBasicType() {
            assertThat(ObjectTypeResolver.resolveType(Integer.class)).isEqualTo(ObjectType.BASIC_TYPE);
            assertThat(ObjectTypeResolver.resolveType(String.class)).isEqualTo(ObjectType.BASIC_TYPE);
        }

        @Test
        @DisplayName("collection → COLLECTION")
        void collection_returnsCollection() {
            assertThat(ObjectTypeResolver.resolveType(List.class)).isEqualTo(ObjectType.COLLECTION);
            assertThat(ObjectTypeResolver.resolveType(Map.class)).isEqualTo(ObjectType.COLLECTION);
        }

        @Test
        @DisplayName("default → VALUE_OBJECT")
        void default_returnsValueObject() {
            assertThat(ObjectTypeResolver.resolveType(PlainPojo.class)).isEqualTo(ObjectType.VALUE_OBJECT);
        }

        @Entity
        static class AnnotatedEntity { }
        @ValueObject
        static class AnnotatedValueObject { }
        static class WithKeyField { @Key long id; }
        static class TestOrder { }
        static class PlainPojo { String x; }
    }

    @Nested
    @DisplayName("ValueObjectStrategyResolver — all branches")
    class ValueObjectStrategyResolverBranchTests {

        @BeforeEach
        void setUp() {
            DiffRegistry.clear();
        }

        @Test
        @DisplayName("resolveStrategy(object): null → FIELDS")
        void resolveStrategy_nullObject_returnsFields() {
            assertThat(ValueObjectStrategyResolver.resolveStrategy((Object) null))
                .isEqualTo(ValueObjectCompareStrategy.FIELDS);
        }

        @Test
        @DisplayName("resolveStrategy(class): null → FIELDS")
        void resolveStrategy_nullClass_returnsFields() {
            assertThat(ValueObjectStrategyResolver.resolveStrategy((Class<?>) null))
                .isEqualTo(ValueObjectCompareStrategy.FIELDS);
        }

        @Test
        @DisplayName("@ValueObject strategy AUTO → FIELDS")
        void valueObjectAuto_returnsFields() {
            assertThat(ValueObjectStrategyResolver.resolveStrategy(ValueObjectAuto.class))
                .isEqualTo(ValueObjectCompareStrategy.FIELDS);
        }

        @Test
        @DisplayName("@ValueObject strategy FIELDS → FIELDS")
        void valueObjectFields_returnsFields() {
            assertThat(ValueObjectStrategyResolver.resolveStrategy(ValueObjectFields.class))
                .isEqualTo(ValueObjectCompareStrategy.FIELDS);
        }

        @Test
        @DisplayName("@ValueObject strategy EQUALS → EQUALS")
        void valueObjectEquals_returnsEquals() {
            assertThat(ValueObjectStrategyResolver.resolveStrategy(ValueObjectEquals.class))
                .isEqualTo(ValueObjectCompareStrategy.EQUALS);
        }

        @Test
        @DisplayName("programmatic registration non-AUTO → returns strategy")
        void programmaticRegistration_returnsStrategy() {
            DiffRegistry.registerValueObject(PlainPojo.class, ValueObjectCompareStrategy.EQUALS);
            assertThat(ValueObjectStrategyResolver.resolveStrategy(PlainPojo.class))
                .isEqualTo(ValueObjectCompareStrategy.EQUALS);
        }

        @Test
        @DisplayName("no annotation no registration → FIELDS")
        void default_returnsFields() {
            assertThat(ValueObjectStrategyResolver.resolveStrategy(PlainPojo.class))
                .isEqualTo(ValueObjectCompareStrategy.FIELDS);
        }

        @ValueObject(strategy = ValueObjectCompareStrategy.AUTO)
        static class ValueObjectAuto { }
        @ValueObject(strategy = ValueObjectCompareStrategy.FIELDS)
        static class ValueObjectFields { }
        @ValueObject(strategy = ValueObjectCompareStrategy.EQUALS)
        static class ValueObjectEquals { }
        static class PlainPojo { }
    }

    // ═══════════════════════════════════════════════════════════════
    // tracking/compare/comparators Package
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UrlCanonicalComparator — all branches")
    class UrlCanonicalComparatorBranchTests {

        private final UrlCanonicalComparator comparator = new UrlCanonicalComparator();

        @Test
        @DisplayName("areEqual: same reference → true")
        void areEqual_sameRef_returnsTrue() {
            String s = "https://x.com";
            assertThat(comparator.areEqual(s, s, null)).isTrue();
        }

        @Test
        @DisplayName("areEqual: left null → false")
        void areEqual_leftNull_returnsFalse() {
            assertThat(comparator.areEqual(null, "x", null)).isFalse();
        }

        @Test
        @DisplayName("areEqual: right null → false")
        void areEqual_rightNull_returnsFalse() {
            assertThat(comparator.areEqual("x", null, null)).isFalse();
        }

        @Test
        @DisplayName("areEqual: string equals → true")
        void areEqual_stringEquals_returnsTrue() {
            assertThat(comparator.areEqual("https://a.com", "https://a.com", null)).isTrue();
        }

        @Test
        @DisplayName("areEqual: different URLs → false")
        void areEqual_differentUrls_returnsFalse() {
            assertThat(comparator.areEqual("https://a.com", "https://b.com", null)).isFalse();
        }

        @Test
        @DisplayName("normalize: http port 80 removed")
        void normalize_httpPort80_removed() {
            assertThat(comparator.areEqual("http://x.com:80/p", "http://x.com/p", null)).isTrue();
        }

        @Test
        @DisplayName("normalize: https port 443 removed")
        void normalize_httpsPort443_removed() {
            assertThat(comparator.areEqual("https://x.com:443/p", "https://x.com/p", null)).isTrue();
        }

        @Test
        @DisplayName("normalize: trailing slash removed when path length > 1")
        void normalize_trailingSlash_removed() {
            assertThat(comparator.areEqual("https://x.com/path/", "https://x.com/path", null)).isTrue();
        }

        @Test
        @DisplayName("normalize: invalid URI → trim fallback")
        void normalize_invalidUri_trimFallback() {
            assertThat(comparator.areEqual("not-a-url", "not-a-url", null)).isTrue();
        }

        @Test
        @DisplayName("normalize: null/blank → empty")
        void normalize_blank_returnsEmpty() {
            assertThat(comparator.areEqual("  ", "  ", null)).isTrue();
        }
    }

    @Nested
    @DisplayName("TrimCaseComparator — all branches")
    class TrimCaseComparatorBranchTests {

        private final TrimCaseComparator comparator = new TrimCaseComparator();

        @Test
        @DisplayName("areEqual: same reference → true")
        void areEqual_sameRef_returnsTrue() {
            String s = "x";
            assertThat(comparator.areEqual(s, s, null)).isTrue();
        }

        @Test
        @DisplayName("areEqual: left null → false")
        void areEqual_leftNull_returnsFalse() {
            assertThat(comparator.areEqual(null, "x", null)).isFalse();
        }

        @Test
        @DisplayName("areEqual: right null → false")
        void areEqual_rightNull_returnsFalse() {
            assertThat(comparator.areEqual("x", null, null)).isFalse();
        }

        @Test
        @DisplayName("areEqual: equal after normalize")
        void areEqual_equalAfterNormalize_returnsTrue() {
            assertThat(comparator.areEqual("  HELLO  ", "hello", null)).isTrue();
        }

        @Test
        @DisplayName("areEqual: different content")
        void areEqual_different_returnsFalse() {
            assertThat(comparator.areEqual("a", "b", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("CaseInsensitiveComparator — all branches")
    class CaseInsensitiveComparatorBranchTests {

        private final CaseInsensitiveComparator comparator = new CaseInsensitiveComparator();

        @Test
        @DisplayName("areEqual: same reference → true")
        void areEqual_sameRef_returnsTrue() {
            String s = "x";
            assertThat(comparator.areEqual(s, s, null)).isTrue();
        }

        @Test
        @DisplayName("areEqual: left null → false")
        void areEqual_leftNull_returnsFalse() {
            assertThat(comparator.areEqual(null, "x", null)).isFalse();
        }

        @Test
        @DisplayName("areEqual: right null → false")
        void areEqual_rightNull_returnsFalse() {
            assertThat(comparator.areEqual("x", null, null)).isFalse();
        }

        @Test
        @DisplayName("areEqual: equal ignore case")
        void areEqual_ignoreCase_returnsTrue() {
            assertThat(comparator.areEqual("HELLO", "hello", null)).isTrue();
        }

        @Test
        @DisplayName("areEqual: different")
        void areEqual_different_returnsFalse() {
            assertThat(comparator.areEqual("a", "b", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("EnumSemanticComparator — all branches")
    class EnumSemanticComparatorBranchTests {

        private final EnumSemanticComparator comparator = new EnumSemanticComparator();

        @Test
        @DisplayName("areEqual: same reference → true")
        void areEqual_sameRef_returnsTrue() {
            String s = "x";
            assertThat(comparator.areEqual(s, s, null)).isTrue();
        }

        @Test
        @DisplayName("areEqual: left null → false")
        void areEqual_leftNull_returnsFalse() {
            assertThat(comparator.areEqual(null, "x", null)).isFalse();
        }

        @Test
        @DisplayName("areEqual: right null → false")
        void areEqual_rightNull_returnsFalse() {
            assertThat(comparator.areEqual("x", null, null)).isFalse();
        }

        @Test
        @DisplayName("areEqual: equal string value")
        void areEqual_equal_returnsTrue() {
            assertThat(comparator.areEqual("A", "A", null)).isTrue();
        }

        @Test
        @DisplayName("areEqual: different")
        void areEqual_different_returnsFalse() {
            assertThat(comparator.areEqual("A", "B", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("BigDecimalScaleComparator — all branches")
    class BigDecimalScaleComparatorBranchTests {

        @Test
        @DisplayName("constructor: scale < 0 throws")
        void constructor_scaleNegative_throws() {
            assertThatThrownBy(() -> new BigDecimalScaleComparator(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
        }

        @Test
        @DisplayName("constructor: tolerance < 0 throws")
        void constructor_toleranceNegative_throws() {
            assertThatThrownBy(() -> new BigDecimalScaleComparator(2, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tolerance");
        }

        @Test
        @DisplayName("areEqual: same reference → true")
        void areEqual_sameRef_returnsTrue() {
            BigDecimalScaleComparator c = new BigDecimalScaleComparator(2);
            BigDecimal b = BigDecimal.ONE;
            assertThat(c.areEqual(b, b, null)).isTrue();
        }

        @Test
        @DisplayName("areEqual: left null → false")
        void areEqual_leftNull_returnsFalse() {
            BigDecimalScaleComparator c = new BigDecimalScaleComparator(2);
            assertThat(c.areEqual(null, BigDecimal.ONE, null)).isFalse();
        }

        @Test
        @DisplayName("areEqual: right null → false")
        void areEqual_rightNull_returnsFalse() {
            BigDecimalScaleComparator c = new BigDecimalScaleComparator(2);
            assertThat(c.areEqual(BigDecimal.ONE, null, null)).isFalse();
        }

        @Test
        @DisplayName("areEqual: tolerance > 0 path")
        void areEqual_tolerancePositive_usesTolerance() {
            BigDecimalScaleComparator c = new BigDecimalScaleComparator(2, 0.01);
            assertThat(c.areEqual(10.0, 10.005, null)).isTrue();
        }

        @Test
        @DisplayName("areEqual: tolerance 0 path")
        void areEqual_toleranceZero_usesCompareTo() {
            BigDecimalScaleComparator c = new BigDecimalScaleComparator(2, 0);
            assertThat(c.areEqual(10.0, 10.0, null)).isTrue();
            assertThat(c.areEqual(10.0, 10.01, null)).isFalse();
        }

        @Test
        @DisplayName("toScaled: BigDecimal instance")
        void toScaled_bigDecimalInstance_usesDirectly() {
            BigDecimalScaleComparator c = new BigDecimalScaleComparator(2);
            assertThat(c.areEqual(new BigDecimal("10.00"), new BigDecimal("10.0"), null)).isTrue();
        }

        @Test
        @DisplayName("toScaled: non-BigDecimal")
        void toScaled_nonBigDecimal_converts() {
            BigDecimalScaleComparator c = new BigDecimalScaleComparator(2);
            assertThat(c.areEqual(10, 10.0, null)).isTrue();
        }

        @Test
        @DisplayName("supports: Number → true")
        void supports_number_returnsTrue() {
            BigDecimalScaleComparator c = new BigDecimalScaleComparator(2);
            assertThat(c.supports(Integer.class)).isTrue();
            assertThat(c.supports(BigDecimal.class)).isTrue();
        }

        @Test
        @DisplayName("supports: CharSequence → true")
        void supports_charSequence_returnsTrue() {
            BigDecimalScaleComparator c = new BigDecimalScaleComparator(2);
            assertThat(c.supports(String.class)).isTrue();
        }

        @Test
        @DisplayName("supports: other → false")
        void supports_other_returnsFalse() {
            BigDecimalScaleComparator c = new BigDecimalScaleComparator(2);
            assertThat(c.supports(Object.class)).isFalse();
        }

        @Test
        @DisplayName("getName contains scale and tolerance")
        void getName_containsScaleAndTolerance() {
            BigDecimalScaleComparator c = new BigDecimalScaleComparator(2, 0.01);
            assertThat(c.getName()).contains("2").contains("0.01");
        }
    }
}
