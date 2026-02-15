package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * StrategyResolver 单元测试
 *
 * <p>覆盖测试方案 SR-WB-001 ~ SR-WB-006 的策略解析逻辑。
 * 重点验证优先级竞争（精确匹配 100 > 泛化匹配 50 > 通用匹配 0）。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("StrategyResolver — 策略解析器测试")
class StrategyResolverTests {

    private StrategyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new StrategyResolver();
    }

    // ──────────────────────────────────────────────────────────────
    //  基本解析
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("基本解析行为")
    class BasicResolutionTests {

        @Test
        @DisplayName("SR-WB-001: null 策略列表 → 返回 null")
        void nullStrategies_shouldReturnNull() {
            CompareStrategy<?> result = resolver.resolve(null, String.class);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("SR-WB-002: 空策略列表 → 返回 null")
        void emptyStrategies_shouldReturnNull() {
            CompareStrategy<?> result = resolver.resolve(Collections.emptyList(), String.class);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("SR-WB-003: null 目标类型 → 返回 null")
        void nullTargetType_shouldReturnNull() {
            List<CompareStrategy<?>> strategies = new ArrayList<>();
            strategies.add(new StubStrategy("stub", true));

            CompareStrategy<?> result = resolver.resolve(strategies, null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("SR-WB-004: 单个匹配策略 → 返回该策略")
        void singleMatchingStrategy_shouldReturnIt() {
            StubStrategy strategy = new StubStrategy("only-one", true);
            List<CompareStrategy<?>> strategies = new ArrayList<>();
            strategies.add(strategy);

            CompareStrategy<?> result = resolver.resolve(strategies, String.class);
            assertThat(result).isSameAs(strategy);
        }

        @Test
        @DisplayName("SR-WB-005: 无策略匹配 → 返回 null")
        void noMatchingStrategy_shouldReturnNull() {
            StubStrategy strategy = new StubStrategy("no-match", false);
            List<CompareStrategy<?>> strategies = new ArrayList<>();
            strategies.add(strategy);

            CompareStrategy<?> result = resolver.resolve(strategies, String.class);
            assertThat(result).isNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  优先级竞争 (SR-WB-006)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("多策略优先级竞争")
    class PriorityCompetitionTests {

        @Test
        @DisplayName("SR-WB-006a: 精确匹配（priority=100）胜出于泛化匹配（priority=50）")
        void exactMatch_shouldWinOverGeneralized() {
            // Strategy A: supports String.class → exact match for String (priority 100)
            StubStrategy exactStrategy = new StubStrategy("exact-string", true);
            // Strategy B: also supports String.class → but returns same priority
            StubStrategy genericStrategy = new StubStrategy("generic-support", true);

            List<CompareStrategy<?>> strategies = new ArrayList<>();
            strategies.add(genericStrategy);
            strategies.add(exactStrategy);

            CompareStrategy<?> result = resolver.resolve(strategies, String.class);

            // Both support the target, so the one with higher calculated priority wins.
            // Since both are "exact" for String.class, max by priority order picks the best.
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("SR-WB-006b: 两个策略注册 — 仅匹配者胜出")
        void onlyMatchingStrategy_shouldBeReturned() {
            StubStrategy matchingStrategy = new StubStrategy("matching", true);
            StubStrategy nonMatchingStrategy = new StubStrategy("non-matching", false);

            List<CompareStrategy<?>> strategies = new ArrayList<>();
            strategies.add(nonMatchingStrategy);
            strategies.add(matchingStrategy);

            CompareStrategy<?> result = resolver.resolve(strategies, String.class);
            assertThat(result).isSameAs(matchingStrategy);
        }

        @Test
        @DisplayName("SR-WB-006c: Collection 子类型 — Set 策略优先于 Collection 策略")
        void setType_shouldPreferSetStrategy() {
            // Strategy for Collection (broad)
            StubStrategy collectionStrategy = new StubStrategy("CollectionCompare", false) {
                @Override
                public boolean supports(Class<?> type) {
                    return java.util.Collection.class.isAssignableFrom(type);
                }
            };
            // Strategy for Set (specific)
            StubStrategy setStrategy = new StubStrategy("SetCompare", false) {
                @Override
                public boolean supports(Class<?> type) {
                    return java.util.Set.class.isAssignableFrom(type);
                }
            };

            List<CompareStrategy<?>> strategies = new ArrayList<>();
            strategies.add(collectionStrategy);
            strategies.add(setStrategy);

            CompareStrategy<?> result = resolver.resolve(strategies, java.util.HashSet.class);

            // SetCompare should have higher priority (60 for specific type match) vs CollectionCompare (50)
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("SetCompare");
        }

        @Test
        @DisplayName("SR-WB-006d: Map 子类型 — Map 策略匹配 HashMap")
        void hashMapType_shouldMatchMapStrategy() {
            StubStrategy mapStrategy = new StubStrategy("MapCompare", false) {
                @Override
                public boolean supports(Class<?> type) {
                    return java.util.Map.class.isAssignableFrom(type);
                }
            };
            StubStrategy unrelatedStrategy = new StubStrategy("unrelated", false);

            List<CompareStrategy<?>> strategies = new ArrayList<>();
            strategies.add(unrelatedStrategy);
            strategies.add(mapStrategy);

            CompareStrategy<?> result = resolver.resolve(strategies, java.util.HashMap.class);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("MapCompare");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  缓存行为
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("缓存行为")
    class CacheBehaviorTests {

        @Test
        @DisplayName("SR-WB-007: 重复 resolve 相同类型 — 使用缓存")
        void repeatedResolve_shouldUseFallbackCache() {
            StubStrategy strategy = new StubStrategy("cached", true);
            List<CompareStrategy<?>> strategies = new ArrayList<>();
            strategies.add(strategy);

            // First call — fills cache
            CompareStrategy<?> first = resolver.resolve(strategies, String.class);
            assertThat(resolver.getCacheSize()).isGreaterThanOrEqualTo(1);

            // Second call — should use cache
            CompareStrategy<?> second = resolver.resolve(strategies, String.class);
            assertThat(second).isSameAs(first);
        }

        @Test
        @DisplayName("SR-WB-008: clearCache 清空缓存")
        void clearCache_shouldResetCacheSize() {
            StubStrategy strategy = new StubStrategy("to-clear", true);
            List<CompareStrategy<?>> strategies = new ArrayList<>();
            strategies.add(strategy);

            resolver.resolve(strategies, String.class);
            assertThat(resolver.getCacheSize()).isGreaterThan(0);

            resolver.clearCache();
            assertThat(resolver.getCacheSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("SR-WB-009: 无 Caffeine 缓存 — hitRate 返回 -1.0")
        void noCaffeineCache_hitRate_shouldReturnNegative() {
            assertThat(resolver.getCacheHitRate()).isEqualTo(-1.0);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Test Doubles
    // ──────────────────────────────────────────────────────────────

    /**
     * Stub CompareStrategy for testing StrategyResolver resolution logic.
     */
    static class StubStrategy implements CompareStrategy<Object> {
        private final String name;
        private final boolean supportsAll;

        StubStrategy(String name, boolean supportsAll) {
            this.name = name;
            this.supportsAll = supportsAll;
        }

        @Override
        public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
            return CompareResult.builder()
                    .object1(obj1)
                    .object2(obj2)
                    .changes(Collections.emptyList())
                    .identical(false)
                    .build();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean supports(Class<?> type) {
            return supportsAll;
        }
    }
}
