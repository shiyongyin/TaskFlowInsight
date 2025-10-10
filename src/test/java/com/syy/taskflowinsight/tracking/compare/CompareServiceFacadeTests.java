package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompareService 门面化行为测试
 * - 委托 Engine 执行
 * - 排序唯一在 Engine（通过 Spy 验证 sortResult 调用一次）
 */
class CompareServiceFacadeTests {

    /**
     * 简单的 Stub 执行器：返回乱序变化列表，不做排序与打点
     */
    static class StubListCompareExecutor extends ListCompareExecutor {
        StubListCompareExecutor() { super(java.util.Collections.emptyList()); }

        @Override
        public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
            FieldChange c1 = FieldChange.builder().fieldName("b").fieldPath("b").build();
            FieldChange c2 = FieldChange.builder().fieldName("a").fieldPath("a").build();
            return CompareResult.builder()
                .object1(list1)
                .object2(list2)
                .identical(false)
                .changes(Arrays.asList(c1, c2)) // 故意乱序
                .build();
        }
    }

    /**
     * Spy Engine：统计 sortResult 调用次数
     */
    static class SpyCompareEngine extends CompareEngine {
        private final AtomicInteger sortCalls = new AtomicInteger(0);

        public SpyCompareEngine(StrategyResolver resolver,
                                ListCompareExecutor listCompareExecutor,
                                Map<Class<?>, CompareStrategy<?>> custom,
                                Map<String, CompareStrategy<?>> named) {
            super(resolver, null, null, listCompareExecutor, custom, named);
        }

        @Override
        CompareResult sortResult(CompareResult result, long startNanos, String path) {
            sortCalls.incrementAndGet();
            return super.sortResult(result, startNanos, path);
        }

        int getSortCalls() { return sortCalls.get(); }
    }

    @Test
    void facade_delegates_to_engine_and_engine_sorts_once_for_list() throws Exception {
        StubListCompareExecutor stub = new StubListCompareExecutor();
        CompareService service = new CompareService(stub);

        SpyCompareEngine spy = new SpyCompareEngine(new StrategyResolver(), stub,
            new ConcurrentHashMap<>(), new ConcurrentHashMap<>());

        // 通过反射替换 CompareService 内部的 CompareEngine 实例
        Field f = CompareService.class.getDeclaredField("compareEngine");
        f.setAccessible(true);
        f.set(service, spy);

        List<Integer> a = Arrays.asList(1,2);
        List<Integer> b = Arrays.asList(2,3);

        CompareResult result = service.compare(a, b, CompareOptions.DEFAULT);

        assertNotNull(result);
        assertFalse(result.isIdentical());
        // 验证排序结果按字段名 a,b 升序（由 Engine 统一排序）
        assertEquals("a", result.getChanges().get(0).getFieldName());
        assertEquals("b", result.getChanges().get(1).getFieldName());

        // 验证 Engine 的 sortResult 仅调用一次
        assertEquals(1, spy.getSortCalls());
    }

    static class Person {
        String name; int age;
        Person(String n, int a) { this.name = n; this.age = a; }
        public String getName() { return name; }
        public int getAge() { return age; }
    }

    @Test
    void facade_delegates_to_engine_for_deep_fallback_and_builds_report() throws Exception {
        StubListCompareExecutor stub = new StubListCompareExecutor();
        CompareService service = new CompareService(stub);

        SpyCompareEngine spy = new SpyCompareEngine(new StrategyResolver(), stub,
            new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        java.lang.reflect.Field f = CompareService.class.getDeclaredField("compareEngine");
        f.setAccessible(true);
        f.set(service, spy);

        Person p1 = new Person("Alice", 20);
        Person p2 = new Person("Bob", 21);

        CompareOptions opts = CompareOptions.builder()
            .generateReport(true)
            .reportFormat(ReportFormat.TEXT)
            .enableDeepCompare(true)
            .maxDepth(5)
            .build();

        CompareResult result = service.compare(p1, p2, opts);

        assertNotNull(result);
        assertFalse(result.isIdentical());
        assertNotNull(result.getReport());
        assertTrue(result.getReport().contains("Change Report") || result.getReport().contains("Total changes"));
        // Engine 排序发生一次
        assertEquals(1, spy.getSortCalls());
    }
}
