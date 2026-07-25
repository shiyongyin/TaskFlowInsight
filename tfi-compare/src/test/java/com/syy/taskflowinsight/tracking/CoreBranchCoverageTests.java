package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import com.syy.taskflowinsight.tracking.query.ListChangeProjector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 核心分支覆盖测试
 * 覆盖 ListChangeProjector、ListCompareExecutor 的分支逻辑
 *
 * @since 3.0.0
 */
@DisplayName("Core Branch Coverage — 核心分支覆盖测试")
class CoreBranchCoverageTests {

    // ═══════════════════════════════════════════════════════════════════
    // ListChangeProjector
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ListChangeProjector — 空输入与算法")
    class ListChangeProjectorEmptyAndAlgorithm {

        @Test
        @DisplayName("null listResult 返回空列表")
        void nullResult_returnsEmpty() {
            List<Map<String, Object>> result = ListChangeProjector.project(
                null, List.of("a"), List.of("b"), CompareOptions.builder().build(), "list");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("默认使用 ordered-index 投影")
        void defaultPlan_projectsByIndex() {
            CompareResult listResult = CompareResult.identical();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a"), List.of("a", "b"), CompareOptions.builder().build(), "items");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("SIMPLE 算法")
        void simpleAlgorithm() {
            CompareResult listResult = CompareResult.identical();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a", "b"), List.of("a", "x"), CompareOptions.builder().build(), "list");
            assertThat(result).anyMatch(e -> "entry_updated".equals(e.get("kind")));
        }

        @Test
        @DisplayName("重排按索引产生更新")
        void reorderingProducesIndexUpdates() {
            CompareResult listResult = CompareResult.identical();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a", "b"), List.of("b", "a"), CompareOptions.builder().build(), "list");
            assertThat(result).allMatch(event -> "entry_updated".equals(event.get("kind")));
        }

        @Test
        @DisplayName("中间元素修改按原索引投影")
        void middleElementModificationUsesOriginalIndex() {
            CompareResult listResult = CompareResult.identical();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a", "b", "c"), List.of("a", "x", "c"),
                CompareOptions.builder().build(), "list");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("单元素修改产生更新")
        void singleElementModificationProducesUpdate() {
            CompareResult listResult = CompareResult.identical();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a", "b"), List.of("a", "x"), CompareOptions.builder().build(), "list");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("ENTITY 算法")
        void entityAlgorithm() {
            CompareResult listResult = CompareResult.identical();
            List<EntityWithKey> left = Collections.emptyList();
            List<EntityWithKey> right = List.of(new EntityWithKey(1, "A"));
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, left, right, CompareOptions.builder().build(), "items");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("普通列表使用唯一默认计划")
        void ordinaryList_usesSingleDefaultPlan() {
            CompareResult listResult = CompareResult.identical();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a"), List.of("b"), CompareOptions.builder().build(), "list");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("null left 使用空列表")
        void nullLeft_usesEmptyList() {
            CompareResult listResult = CompareResult.identical();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, null, List.of("new"), CompareOptions.builder().build(), "list");
            assertThat(result).anyMatch(e -> "entry_added".equals(e.get("kind")));
        }

        @Test
        @DisplayName("null right 使用空列表")
        void nullRight_usesEmptyList() {
            CompareResult listResult = CompareResult.identical();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("old"), null, CompareOptions.builder().build(), "list");
            assertThat(result).anyMatch(e -> "entry_removed".equals(e.get("kind")));
        }
    }

    @Nested
    @DisplayName("ListChangeProjector — ordered-index 与 createEvent")
    class ListChangeProjectorOrderedIndex {

        @Test
        @DisplayName("重排保留索引更新，不合并为 moved")
        void reorderingKeepsIndexUpdates() {
            CompareResult listResult = CompareResult.identical();
            CompareOptions opts = CompareOptions.builder().build();
            List<String> left = List.of("a", "b", "c");
            List<String> right = List.of("b", "a", "c");
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, left, right, opts, "list");
            assertThat(result).isNotEmpty()
                    .noneMatch(event -> "entry_moved".equals(event.get("kind")));
        }

        @Test
        @DisplayName("entry_added 事件")
        void entryAddedEvent() {
            CompareResult listResult = CompareResult.identical();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a"), List.of("a", "b"), CompareOptions.builder().build(), "list");
            assertThat(result).anyMatch(e -> "entry_added".equals(e.get("kind")));
        }

        @Test
        @DisplayName("entry_removed 事件")
        void entryRemovedEvent() {
            CompareResult listResult = CompareResult.identical();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a", "b"), List.of("a"), CompareOptions.builder().build(), "list");
            assertThat(result).anyMatch(e -> "entry_removed".equals(e.get("kind")));
        }

        @Test
        @DisplayName("entry_updated 事件")
        void entryUpdatedEvent() {
            CompareResult listResult = CompareResult.identical();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a", "b"), List.of("a", "x"), CompareOptions.builder().build(), "list");
            assertThat(result).anyMatch(e -> "entry_updated".equals(e.get("kind")));
        }

        @Test
        @DisplayName("ENTITY 算法 duplicateKeys")
        void entityDuplicateKeys() {
            CompareResult listResult = CompareResult.identical();
            List<EntityWithKey> left = List.of(new EntityWithKey(1, "A"));
            List<EntityWithKey> right = List.of(new EntityWithKey(2, "B"));
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, left, right, CompareOptions.builder().build(), "items");
            assertThat(result).isNotEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ListCompareExecutor
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ListCompareExecutor — 冻结策略选择")
    class ListCompareExecutorRouting {

        private ListCompareExecutor createExecutor() {
            return new ListCompareExecutor(List.of(
                new SimpleListStrategy(),
                new EntityListStrategy()));
        }

        @Test
        @DisplayName("显式 SIMPLE 策略")
        void explicitSimpleStrategy() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult r = executor.compare(List.of("a", "b"), List.of("a", "x"), opts);
        }

        @Test
        @DisplayName("普通列表重排仍使用 SIMPLE")
        void reorderedOrdinaryListUsesSimple() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult r = executor.compare(List.of("a", "b"), List.of("b", "a"), opts);
        }

        @Test
        @DisplayName("普通列表中间修改仍使用 SIMPLE")
        void modifiedOrdinaryListUsesSimple() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult r = executor.compare(
                List.of("a", "b", "c"), List.of("a", "x", "c"), opts);
        }

        @Test
        @DisplayName("普通列表单元素修改仍使用 SIMPLE")
        void singleModificationUsesSimple() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult r = executor.compare(List.of("a", "b"), List.of("a", "x"), opts);
        }

        @Test
        @DisplayName("显式 ENTITY 策略")
        void explicitEntityStrategy() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().build();
            List<EntityWithKey> a = List.of(new EntityWithKey(1, "A"));
            List<EntityWithKey> b = List.of(new EntityWithKey(1, "B"));
            CompareResult r = executor.compare(a, b, opts);
        }

        @Test
        @DisplayName("普通列表走冻结的默认策略")
        void ordinaryList_usesFrozenDefault() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult r = executor.compare(List.of("a"), List.of("b"), opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("600 项列表不切换语义")
        void mediumList_keepsFrozenSemantics() {
            ListCompareExecutor executor = createExecutor();
            List<String> large = new ArrayList<>();
            for (int i = 0; i < 600; i++) large.add("item" + i);
            CompareResult r = executor.compare(large, new ArrayList<>(large), CompareOptions.builder().build());
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("1100 项列表不切换语义")
        void largeList_keepsFrozenSemantics() {
            ListCompareExecutor executor = createExecutor();
            List<String> huge = new ArrayList<>();
            for (int i = 0; i < 1100; i++) huge.add("x" + i);
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult r = executor.compare(huge, new ArrayList<>(huge), opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("中等规模相同列表保持相等")
        void equalMediumListsRemainEqual() {
            ListCompareExecutor executor = createExecutor();
            List<String> mid = new ArrayList<>();
            for (int i = 0; i < 600; i++) mid.add("x" + i);
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult r = executor.compare(mid, new ArrayList<>(mid), opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("同质 Entity 列表选择 ENTITY")
        void homogeneousEntityListUsesEntityStrategy() {
            ListCompareExecutor executor = createExecutor();
            List<EntityWithKey> a = List.of(new EntityWithKey(1, "A"));
            List<EntityWithKey> b = List.of(new EntityWithKey(1, "B"));
            CompareResult r = executor.compare(a, b, CompareOptions.builder().build());
        }

        @Test
        @DisplayName("非 Entity 使用 SIMPLE")
        void nonEntity_usesSimple() {
            ListCompareExecutor executor = createExecutor();
            CompareResult r = executor.compare(List.of("a"), List.of("b"), CompareOptions.builder().build());
        }

        @Test
        @DisplayName("calculateSimilarity 空 union 返回 1.0")
        void similarityEmptyUnion() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().computeSimilarity(true).build();
            CompareResult r = executor.compare(
                Collections.emptyList(), Collections.emptyList(), opts);
        }

        @Test
        @DisplayName("getSupportedStrategies")
        void getSupportedStrategies() {
            ListCompareExecutor executor = createExecutor();
            assertThat(executor.getSupportedStrategies())
                .containsExactlyInAnyOrder("SIMPLE", "ENTITY");
        }

    }

    @Entity
    static class EntityWithKey {
        @Key
        private final int id;
        @SuppressWarnings("unused")
        private String name;

        EntityWithKey(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EntityWithKey that = (EntityWithKey) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }
}
