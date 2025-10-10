package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StrategyResolver 单元测试
 * M2: 验证策略解析的优先级规则（精确>泛化>通用）
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M2
 * @since 2025-10-04
 */
class StrategyResolverTests {

    private StrategyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new StrategyResolver();
    }

    @Test
    void resolve_exactType_overGeneric() {
        // 模拟策略：Date 精确策略 + Collection 泛化策略
        CompareStrategy<Date> dateStrategy = new TestCompareStrategy<>("DateStrategy", Date.class);
        CompareStrategy<?> genericStrategy = new TestCompareStrategy<>("GenericStrategy", Object.class);

        List<CompareStrategy<?>> strategies = Arrays.asList(dateStrategy, genericStrategy);

        CompareStrategy<?> result = resolver.resolve(strategies, Date.class);

        assertNotNull(result);
        assertEquals("DateStrategy", result.getName());
    }

    @Test
    void resolve_returnsMatchingStrategy() {
        // 模拟策略：ArrayList 精确策略优先于泛化策略
        CompareStrategy<?> exactStrategy = new TestCompareStrategy<>("ExactStrategy", java.util.ArrayList.class);
        CompareStrategy<?> genericStrategy = new TestCompareStrategy<>("GenericStrategy", Object.class);

        List<CompareStrategy<?>> strategies = Arrays.asList(exactStrategy, genericStrategy);

        CompareStrategy<?> result = resolver.resolve(strategies, java.util.ArrayList.class);

        assertNotNull(result);
        // 精确匹配优先（ArrayList == ArrayList）
        assertEquals("ExactStrategy", result.getName());
    }

    @Test
    void resolve_cachesResults() {
        CompareStrategy<?> strategy = new TestCompareStrategy<>("TestStrategy", String.class);
        List<CompareStrategy<?>> strategies = Arrays.asList(strategy);

        // 首次解析
        CompareStrategy<?> result1 = resolver.resolve(strategies, String.class);
        // 再次解析（应该命中缓存）
        CompareStrategy<?> result2 = resolver.resolve(strategies, String.class);

        assertSame(result1, result2);
        assertTrue(resolver.getCacheSize() > 0);
    }

    @Test
    void resolve_noMatch_returnsNull() {
        List<CompareStrategy<?>> strategies = java.util.Collections.emptyList();

        CompareStrategy<?> result = resolver.resolve(strategies, String.class);

        assertNull(result);
    }

    @Test
    void clearCache_removesEntries() {
        CompareStrategy<?> strategy = new TestCompareStrategy<>("TestStrategy", String.class);
        resolver.resolve(Arrays.asList(strategy), String.class);

        assertTrue(resolver.getCacheSize() > 0);

        resolver.clearCache();

        assertEquals(0, resolver.getCacheSize());
    }

    // ========== 测试辅助类 ==========

    static class TestCompareStrategy<T> implements CompareStrategy<T> {
        private final String name;
        private final Class<?> supportedType;

        TestCompareStrategy(String name, Class<?> supportedType) {
            this.name = name;
            this.supportedType = supportedType;
        }

        @Override
        public CompareResult compare(T obj1, T obj2, CompareOptions options) {
            return CompareResult.identical();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean supports(Class<?> type) {
            return supportedType.isAssignableFrom(type);
        }
    }
}
