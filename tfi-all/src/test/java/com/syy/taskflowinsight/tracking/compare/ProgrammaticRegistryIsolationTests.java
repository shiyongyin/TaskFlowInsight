package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.api.builder.TfiContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ProgrammaticRegistryIsolationTests {

    static class Foo { String name; Foo(String n) { this.name = n; } }

    @Test
    @DisplayName("parallel batch uses comparator and does not leak to next compares")
    void parallel_batch_should_use_comparator_and_not_leak() {
        // Service A：注册路径级比较器（name 始终视为相等）
        TfiContext ctxA = DiffBuilder.create()
            .withDeepCompare(true)
            .withMaxDepth(5)
            .withPropertyComparator("name", (left, right, field) -> true)
            .build();

        CompareOptions deep = CompareOptions.builder().enableDeepCompare(true).maxDepth(5).build();

        // 构造 50 对对象，触发 parallel 分支
        List<Pair<Object, Object>> pairs = IntStream.range(0, 50)
            .<Pair<Object, Object>>mapToObj(i -> Pair.of(new Foo("A"), new Foo("B")))
            .toList();

        List<CompareResult> results = ctxA.compareService().compareBatch(pairs, deep);
        assertEquals(50, results.size());
        assertTrue(results.stream().allMatch(CompareResult::isIdentical),
            "all results should be identical due to property comparator");

        // Service B：未注册比较器，不应受到 ThreadLocal 污染
        TfiContext ctxB = DiffBuilder.create().withDeepCompare(true).withMaxDepth(5).build();
        CompareResult r = ctxB.compare(new Foo("A"), new Foo("B"), deep);
        assertFalse(r.isIdentical(), "no leakage to subsequent compares in same thread");
    }

    static class ThrowType { int v; ThrowType(int v){ this.v = v; } }

    static class ThrowingStrategy implements CompareStrategy<ThrowType> {
        @Override
        public CompareResult compare(ThrowType obj1, ThrowType obj2, CompareOptions options) {
            throw new RuntimeException("intentional");
        }

        @Override
        public String getName() { return "THROW"; }

        @Override
        public boolean supports(Class<?> type) { return ThrowType.class.equals(type); }
    }

    @Test
    @DisplayName("exception path does not leak programmatic service")
    void exception_should_not_leak_programmatic_service() {
        // Service A：注册比较器（name 始终相等），并注册抛异常的策略
        TfiContext ctxA = DiffBuilder.create()
            .withDeepCompare(true)
            .withMaxDepth(5)
            .withPropertyComparator("name", (left, right, field) -> true)
            .build();

        CompareService svcA = ctxA.compareService();
        svcA.registerStrategy(ThrowType.class, new ThrowingStrategy());

        // 触发异常（Engine 内部会兜底为降级结果），但应完成 ThreadLocal 清理
        CompareResult rr = svcA.compare(new ThrowType(1), new ThrowType(2), CompareOptions.DEFAULT);
        assertNotNull(rr);

        // 随后使用未注册比较器的 Service B 比较，应不受污染
        TfiContext ctxB = DiffBuilder.create().withDeepCompare(true).withMaxDepth(5).build();
        CompareResult r = ctxB.compare(new Foo("A"), new Foo("B"), CompareOptions.DEEP);
        assertFalse(r.isIdentical(), "no leakage after exception path");
    }
}
