package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证在关闭自动路由时不会选择 ENTITY 策略
 */
class ListCompareExecutorAutoRouteConfigTests {

    private ListCompareExecutor executor;

    // 简单策略桩：标记 report= strategy:SIMPLE
    static class StubSimpleStrategy implements ListCompareStrategy {
        @Override public String getStrategyName() { return "SIMPLE"; }
        @Override public int getMaxRecommendedSize() { return 10000; }
        @Override public boolean supportsMoveDetection() { return false; }
        @Override public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
            return CompareResult.builder()
                    .object1(list1)
                    .object2(list2)
                    .changes(Collections.emptyList())
                    .identical(true)
                    .report("strategy:SIMPLE")
                    .build();
        }
    }

    // ENTITY 策略桩：标记 report= strategy:ENTITY
    static class StubEntityStrategy implements ListCompareStrategy {
        @Override public String getStrategyName() { return "ENTITY"; }
        @Override public int getMaxRecommendedSize() { return 100; }
        @Override public boolean supportsMoveDetection() { return false; }
        @Override public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
            return CompareResult.builder()
                    .object1(list1)
                    .object2(list2)
                    .changes(Collections.emptyList())
                    .identical(true)
                    .report("strategy:ENTITY")
                    .build();
        }
    }

    @Entity
    static class U {
        @Key Long id; String name;
        U(Long id, String name) { this.id = id; this.name = name; }
    }

    @BeforeEach
    void setUp() {
        ListCompareStrategy simple = new StubSimpleStrategy();
        ListCompareStrategy entity = new StubEntityStrategy();
        executor = new ListCompareExecutor(List.of(simple, entity));

        // 注入关闭的配置
        CompareRoutingProperties props = new CompareRoutingProperties();
        CompareRoutingProperties.EntityConfig entityConfig = new CompareRoutingProperties.EntityConfig();
        entityConfig.setEnabled(false);
        props.setEntity(entityConfig);
        try {
            Field f = ListCompareExecutor.class.getDeclaredField("autoRouteProps");
            f.setAccessible(true);
            f.set(executor, props);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldNotAutoRouteToEntityWhenDisabled() {
        List<U> oldList = List.of(new U(1L, "A"));
        List<U> newList = List.of(new U(1L, "A"), new U(2L, "B"));

        CompareOptions options = CompareOptions.builder().build(); // 未指定策略
        CompareResult result = executor.compare(oldList, newList, options);

        assertNotNull(result);
        // 由于关闭自动路由，应选择 SIMPLE 策略
        assertEquals("strategy:SIMPLE", result.getReport());
    }
}

