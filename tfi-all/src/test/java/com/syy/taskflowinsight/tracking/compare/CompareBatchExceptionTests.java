package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.api.builder.TfiContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发批处理 + 异常场景覆盖测试
 *
 * 目标：
 * - 覆盖 compareBatch 在并行分支中某个 compare 发生异常时的降级路径（Engine catch）。
 * - 验证批处理总体不失败，返回的对应结果为空变更（identical=false, changes=0）。
 */
class CompareBatchExceptionTests {

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

    static class Foo { String name; int id; Foo(String n, int i){ this.name=n; this.id=i; } }

    @Test
    @DisplayName("parallel batch continues when one pair throws in strategy")
    void parallel_batch_should_continue_on_single_failure() {
        // 基础上下文（深度比较）
        TfiContext ctx = DiffBuilder.create().withDeepCompare(true).withMaxDepth(5).build();
        CompareService svc = ctx.compareService();

        // 注册抛异常的策略（仅作用于 ThrowType）
        svc.registerStrategy(ThrowType.class, new ThrowingStrategy());

        // 构造12对对象，超过并行阈值；第一对为 ThrowType 触发异常
        List<Pair<Object,Object>> pairs = new ArrayList<>();
        pairs.add(Pair.of(new ThrowType(1), new ThrowType(2))); // index 0: 触发异常
        for (int i = 0; i < 11; i++) {
            pairs.add(Pair.of(new Foo("A" + i, i), new Foo("B" + i, i))); // 正常比较（name 变更）
        }

        CompareOptions opts = CompareOptions.builder()
            .enableDeepCompare(true)
            .maxDepth(5)
            .parallelThreshold(5) // 强制并行
            .build();

        List<CompareResult> results = svc.compareBatch(pairs, opts);

        assertEquals(12, results.size());

        // 异常对：应由 Engine 捕获并返回空变更（非 identical，以便上游可识别发生了降级）
        CompareResult r0 = results.get(0);
        assertNotNull(r0);
        assertFalse(r0.isIdentical());
        assertEquals(0, r0.getChangeCount());

        // 其余对：应产生非空变更（name 字段变化）
        for (int i = 1; i < results.size(); i++) {
            CompareResult ri = results.get(i);
            assertNotNull(ri);
            assertFalse(ri.isIdentical());
        }
    }
}

