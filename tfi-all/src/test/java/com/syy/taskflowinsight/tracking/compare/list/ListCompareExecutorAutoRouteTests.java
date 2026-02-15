package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ListCompareExecutor 自动路由单元测试
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
class ListCompareExecutorAutoRouteTests {

    private ListCompareExecutor executor;

    // 测试用策略桩
    private static class StubSimpleStrategy implements ListCompareStrategy {
        @Override
        public String getStrategyName() {
            return "SIMPLE";
        }

        @Override
        public int getMaxRecommendedSize() {
            return 10000;
        }

        @Override
        public boolean supportsMoveDetection() {
            return false;
        }

        @Override
        public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
            // 返回带有策略名标记的结果（使用 report 字段标识）
            return CompareResult.builder()
                    .object1(list1)
                    .object2(list2)
                    .changes(Collections.emptyList())
                    .identical(true)
                    .report("strategy:SIMPLE")
                    .build();
        }
    }

    private static class StubEntityStrategy implements ListCompareStrategy {
        @Override
        public String getStrategyName() {
            return "ENTITY";
        }

        @Override
        public int getMaxRecommendedSize() {
            return 100;
        }

        @Override
        public boolean supportsMoveDetection() {
            return false;
        }

        @Override
        public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
            // 返回带有策略名标记的结果（使用 report 字段标识）
            return CompareResult.builder()
                    .object1(list1)
                    .object2(list2)
                    .changes(Collections.emptyList())
                    .identical(true)
                    .report("strategy:ENTITY")
                    .build();
        }
    }

    // 测试实体类：带 @Entity 注解
    @Entity
    static class TestEntityWithAnnotation {
        @Key
        private Long id;
        private String name;

        public TestEntityWithAnnotation(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    // 测试实体类：无 @Entity 但含 @Key 字段
    static class TestEntityWithKeyField {
        @Key
        private String code;
        private String value;

        public TestEntityWithKeyField(String code, String value) {
            this.code = code;
            this.value = value;
        }
    }

    // 测试实体类：父类含 @Key 字段
    static class ParentEntity {
        @Key
        private Integer parentId;

        public ParentEntity(Integer parentId) {
            this.parentId = parentId;
        }
    }

    static class ChildEntity extends ParentEntity {
        private String childData;

        public ChildEntity(Integer parentId, String childData) {
            super(parentId);
            this.childData = childData;
        }
    }

    // 普通POJO（无注解）
    static class PlainObject {
        private String data;

        public PlainObject(String data) {
            this.data = data;
        }
    }

    @BeforeEach
    void setUp() {
        List<ListCompareStrategy> strategies = Arrays.asList(
                new StubSimpleStrategy(),
                new StubEntityStrategy()
        );
        executor = new ListCompareExecutor(strategies);
    }

    @Test
    void testAutoRouteToEntityStrategy_WithEntityAnnotation() {
        // Given: 列表包含 @Entity 注解的对象
        List<TestEntityWithAnnotation> list1 = Arrays.asList(
                new TestEntityWithAnnotation(1L, "Alice"),
                new TestEntityWithAnnotation(2L, "Bob")
        );
        List<TestEntityWithAnnotation> list2 = Arrays.asList(
                new TestEntityWithAnnotation(1L, "Alice"),
                new TestEntityWithAnnotation(2L, "Bob")
        );

        CompareOptions options = CompareOptions.builder().build();

        // When: 执行比较
        CompareResult result = executor.compare(list1, list2, options);

        // Then: 应该自动路由到 ENTITY 策略
        assertNotNull(result);
        assertEquals("strategy:ENTITY", result.getReport(),
                "Should auto-route to ENTITY strategy for @Entity annotated classes");
    }

    @Test
    void testAutoRouteToEntityStrategy_WithKeyField() {
        // Given: 列表包含 @Key 字段的对象（无 @Entity）
        List<TestEntityWithKeyField> list1 = Arrays.asList(
                new TestEntityWithKeyField("CODE1", "Value1"),
                new TestEntityWithKeyField("CODE2", "Value2")
        );
        List<TestEntityWithKeyField> list2 = Arrays.asList(
                new TestEntityWithKeyField("CODE1", "Value1")
        );

        CompareOptions options = CompareOptions.builder().build();

        // When: 执行比较
        CompareResult result = executor.compare(list1, list2, options);

        // Then: 应该自动路由到 ENTITY 策略
        assertNotNull(result);
        assertEquals("strategy:ENTITY", result.getReport(),
                "Should auto-route to ENTITY strategy for classes with @Key fields");
    }

    @Test
    void testAutoRouteToEntityStrategy_WithKeyFieldInParent() {
        // Given: 父类含 @Key 字段
        List<ChildEntity> list1 = Arrays.asList(
                new ChildEntity(100, "Data1"),
                new ChildEntity(200, "Data2")
        );
        List<ChildEntity> list2 = Arrays.asList(
                new ChildEntity(100, "Data1")
        );

        CompareOptions options = CompareOptions.builder().build();

        // When: 执行比较
        CompareResult result = executor.compare(list1, list2, options);

        // Then: 应该自动路由到 ENTITY 策略（检测到父类 @Key）
        assertNotNull(result);
        assertEquals("strategy:ENTITY", result.getReport(),
                "Should auto-route to ENTITY strategy for classes with @Key fields in parent class");
    }

    @Test
    void testExplicitStrategyOverridesAutoRoute() {
        // Given: 显式指定 SIMPLE 策略，即使列表包含实体
        List<TestEntityWithAnnotation> list1 = Arrays.asList(
                new TestEntityWithAnnotation(1L, "Alice")
        );
        List<TestEntityWithAnnotation> list2 = Arrays.asList(
                new TestEntityWithAnnotation(1L, "Alice")
        );

        CompareOptions options = CompareOptions.builder()
                .strategyName("SIMPLE")
                .build();

        // When: 执行比较
        CompareResult result = executor.compare(list1, list2, options);

        // Then: 应该使用显式指定的 SIMPLE 策略
        assertNotNull(result);
        assertEquals("strategy:SIMPLE", result.getReport(),
                "Explicit strategy should override auto-routing");
    }

    @Test
    void testNoEntityStrategyRegisteredFallback() {
        // Given: 执行器仅注册 SIMPLE 策略
        ListCompareExecutor executorWithSimpleOnly = new ListCompareExecutor(
                Collections.singletonList(new StubSimpleStrategy())
        );

        List<TestEntityWithAnnotation> list1 = Arrays.asList(
                new TestEntityWithAnnotation(1L, "Alice")
        );
        List<TestEntityWithAnnotation> list2 = Arrays.asList(
                new TestEntityWithAnnotation(1L, "Alice")
        );

        CompareOptions options = CompareOptions.builder().build();

        // When: 执行比较
        CompareResult result = executorWithSimpleOnly.compare(list1, list2, options);

        // Then: 应该回退到 SIMPLE 策略
        assertNotNull(result);
        assertEquals("strategy:SIMPLE", result.getReport(),
                "Should fallback to SIMPLE when ENTITY strategy not registered");
    }

    @Test
    void testNullOrEmptyListsNoAutoRoute() {
        // Given: null 或空列表
        CompareOptions options = CompareOptions.builder().build();

        // When/Then: null列表
        CompareResult result1 = executor.compare(null, null, options);
        assertNotNull(result1);

        // When/Then: 空列表
        CompareResult result2 = executor.compare(
                Collections.emptyList(),
                Collections.emptyList(),
                options
        );
        assertNotNull(result2);

        // When/Then: 全 null 元素的列表
        List<TestEntityWithAnnotation> listWithNulls = Arrays.asList(null, null, null);
        CompareResult result3 = executor.compare(listWithNulls, listWithNulls, options);
        assertNotNull(result3);
        // 应该使用默认 SIMPLE 策略（因为无法检测到实体）
        assertEquals("strategy:SIMPLE", result3.getReport(),
                "Should use SIMPLE for lists with all null elements");
    }

    @Test
    void testPlainObjectsUsesSimpleStrategy() {
        // Given: 普通POJO（无 @Entity 和 @Key）
        List<PlainObject> list1 = Arrays.asList(
                new PlainObject("data1"),
                new PlainObject("data2")
        );
        List<PlainObject> list2 = Arrays.asList(
                new PlainObject("data1")
        );

        CompareOptions options = CompareOptions.builder().build();

        // When: 执行比较
        CompareResult result = executor.compare(list1, list2, options);

        // Then: 应该使用默认 SIMPLE 策略
        assertNotNull(result);
        assertEquals("strategy:SIMPLE", result.getReport(),
                "Should use SIMPLE strategy for plain objects without annotations");
    }

    @Test
    void testAutoRoutePerformance_OnlyChecksFirst3Elements() {
        // Given: 大列表（10000个元素），前3个是普通对象，第4个是实体
        List<Object> list = new ArrayList<>();
        list.add(new PlainObject("1"));
        list.add(new PlainObject("2"));
        list.add(new PlainObject("3"));

        // 从第4个元素开始都是实体（但不应该被检查到）
        for (int i = 4; i <= 10000; i++) {
            list.add(new TestEntityWithAnnotation((long) i, "Entity" + i));
        }

        CompareOptions options = CompareOptions.builder().build();

        // When: 执行比较
        long startTime = System.nanoTime();
        CompareResult result = executor.compare(list, list, options);
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        // Then: 应该使用 SIMPLE 策略（因为仅检查前3个元素）
        assertNotNull(result);
        assertEquals("strategy:SIMPLE", result.getReport(),
                "Should use SIMPLE when first 3 elements are not entities");

        // 性能验证：策略选择应该很快（< 50ms）
        assertTrue(durationMs < 50,
                "Strategy selection should be fast (< 50ms), actual: " + durationMs + "ms");
    }

    @Test
    void testAutoRoute_MixedNullAndEntityElements() {
        // Given: 列表包含 null 和实体混合
        List<TestEntityWithAnnotation> list1 = new ArrayList<>();
        list1.add(null);
        list1.add(null);
        list1.add(new TestEntityWithAnnotation(1L, "Entity")); // 第3个非null元素是实体

        CompareOptions options = CompareOptions.builder().build();

        // When: 执行比较
        CompareResult result = executor.compare(list1, list1, options);

        // Then: 应该检测到实体并路由到 ENTITY 策略
        assertNotNull(result);
        assertEquals("strategy:ENTITY", result.getReport(),
                "Should detect entity even when list contains null elements");
    }
}