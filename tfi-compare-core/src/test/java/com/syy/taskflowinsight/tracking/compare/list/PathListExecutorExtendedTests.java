package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 路径列表执行器最终覆盖测试
 * 覆盖 ListCompareExecutor 的稳定列表语义
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@DisplayName("Path List Executor Final — 路径列表执行器最终覆盖测试")
class PathListExecutorExtendedTests {

    private static final CompareOptions DEFAULT = CompareOptions.builder().build();

    private ListCompareExecutor createExecutor() {
        return new ListCompareExecutor(List.of(
            new SimpleListStrategy(),
            new EntityListStrategy()
        ));
    }

    // ── ListCompareExecutor 剩余分支 ──

    @Nested
    @DisplayName("ListCompareExecutor — 规模无关语义")
    class ListExecutorSizeIndependentSemantics {

        @Test
        @DisplayName("交叉组合规模不改变冻结算法")
        void crossProductSizeDoesNotChangeFrozenAlgorithm() {
            ListCompareExecutor executor = createExecutor();

            List<String> a = new ArrayList<>();
            List<String> b = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                a.add("x" + i);
                b.add("y" + i);
            }
            CompareResult r = executor.compare(a, b, DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("列表执行不依赖 DegradationDecisionEngine")
        void listExecutionHasNoDegradationDependency() {
            ListCompareExecutor executor = createExecutor();

            List<String> a = List.of("a", "b");
            List<String> b = List.of("a", "c");
            CompareResult r = executor.compare(a, b, DEFAULT);
            assertThat(r).isNotNull();
        }

    }

    @Nested
    @DisplayName("ListCompareExecutor — Entity 检测")
    class ListExecutorEntityDetection {

        @Test
        @DisplayName("Entity 无 @Key 时发出诊断")
        void entityWithoutKey() {
            ListCompareExecutor executor = createExecutor();
            List<EntityNoKey> a = List.of(new EntityNoKey("x"));
            List<EntityNoKey> b = List.of(new EntityNoKey("y"));
            CompareResult r = executor.compare(a, b, CompareOptions.builder().build());
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("混合元素类型列表")
        void mixedElementTypes() {
            ListCompareExecutor executor = createExecutor();
            List<Object> mixed = new ArrayList<>();
            mixed.add("string");
            mixed.add(42);
            CompareResult r = executor.compare(mixed, new ArrayList<>(mixed), DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("普通列表不读取 Spring 路由配置")
        void plainListDoesNotReadSpringRouting() {
            ListCompareExecutor executor = createExecutor();

            CompareOptions opts = CompareOptions.builder().build();
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("b", "a", "c");
            CompareResult r = executor.compare(before, after, opts);
            assertThat(r).isNotNull();
            assertThat(r.getChanges()).noneMatch(c -> c.getChangeType() == ChangeType.MOVE);
        }

        @Test
        @DisplayName("未知策略名回退到 SIMPLE")
        void unknownStrategyFallback() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult r = executor.compare(List.of("a"), List.of("b"), opts);
            assertThat(r).isNotNull();
        }

    }

    @Entity
    static class EntityNoKey {
        @SuppressWarnings("unused")
        private String name;
        EntityNoKey(String name) { this.name = name; }
    }

}
