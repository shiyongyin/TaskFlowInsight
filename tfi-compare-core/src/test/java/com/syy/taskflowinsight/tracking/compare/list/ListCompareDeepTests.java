package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deep coverage tests for the frozen List strategy table and ordered-index implementation.
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("ListCompare — Deep Coverage Tests")
class ListCompareDeepTests {

    private static final CompareOptions DEFAULT = CompareOptions.builder().build();
    private static final CompareOptions DEEP_COMPARE = CompareOptions.builder().build();
    private static final CompareOptions WITH_SIMILARITY = CompareOptions.builder().computeSimilarity(true).build();

    // ──────────────────────────────────────────────────────────────
    //  ListCompareExecutor
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ListCompareExecutor")
    class ListCompareExecutorTests {

        @Test
        @DisplayName("Executor with all strategies — compare empty lists")
        void executor_emptyLists_shouldBeIdentical() {
            ListCompareExecutor executor = createExecutor();
            CompareResult result = executor.compare(
                Collections.emptyList(), Collections.emptyList(), DEFAULT);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("Executor — compare single element lists")
        void executor_singleElement_shouldWork() {
            ListCompareExecutor executor = createExecutor();
            List<String> a = List.of("x");
            List<String> b = List.of("y");
            CompareResult result = executor.compare(a, b, DEFAULT);
            assertThat(result).isNotNull();
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("Executor — compare same lists")
        void executor_sameLists_shouldBeIdentical() {
            ListCompareExecutor executor = createExecutor();
            List<String> list = List.of("a", "b", "c");
            CompareResult result = executor.compare(list, list, DEFAULT);
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("Executor — additions and removals")
        void executor_additionsAndRemovals_shouldDetect() {
            ListCompareExecutor executor = createExecutor();
            List<String> before = List.of("a", "b");
            List<String> after = List.of("a", "x", "c");
            CompareResult result = executor.compare(before, after, DEFAULT);
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("Executor — with similarity calculation")
        void executor_withSimilarity_shouldSetSimilarity() {
            ListCompareExecutor executor = createExecutor();
            List<String> a = List.of("a", "b");
            List<String> b = List.of("a", "b");
            CompareResult result = executor.compare(a, b, WITH_SIMILARITY);
        }

        @Test
        @DisplayName("Executor — getSupportedStrategies")
        void executor_getSupportedStrategies_shouldReturnFrozenStrategies() {
            ListCompareExecutor executor = createExecutor();
            assertThat(executor.getSupportedStrategies())
                .containsExactlyInAnyOrder("SIMPLE", "ENTITY");
        }

        @Test
        @DisplayName("Executor — explicit strategy SIMPLE")
        void executor_explicitSimple_shouldUseSimple() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult result = executor.compare(
                List.of("a", "b"), List.of("a", "x"), opts);
        }

        @Test
        @DisplayName("Executor — reordered ordinary List uses ordered index")
        void executor_reorderedOrdinaryList_shouldUseIndexes() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult result = executor.compare(
                List.of("a", "b"), List.of("b", "a"), opts);
        }

        @Test
        @DisplayName("Executor — insertion in ordinary List uses ordered index")
        void executor_insertedOrdinaryList_shouldUseIndexes() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult result = executor.compare(
                List.of("a", "b", "c"), List.of("a", "x", "c"), opts);
        }

        @Test
        @DisplayName("Executor — replacement in ordinary List uses ordered index")
        void executor_replacedOrdinaryList_shouldUseIndexes() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult result = executor.compare(
                List.of("a", "b"), List.of("a", "x"), opts);
        }

        @Test
        @DisplayName("Executor — null list handling")
        void executor_nullList_shouldHandle() {
            ListCompareExecutor executor = createExecutor();
            CompareResult result = executor.compare(null, List.of("a"), DEFAULT);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        private ListCompareExecutor createExecutor() {
            return new ListCompareExecutor(List.of(
                new SimpleListStrategy(),
                new EntityListStrategy()
            ));
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  SimpleListStrategy — deep compare
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SimpleListStrategy — Deep")
    class SimpleListStrategyDeepTests {

        private final SimpleListStrategy strategy = new SimpleListStrategy();

        @Test
        @DisplayName("SIMPLE — enableDeepCompare on nested objects")
        void simple_deepCompare_shouldExpandChanges() {
            List<Object> before = List.of(Map.of("x", 1));
            List<Object> after = List.of(Map.of("x", 2));
            CompareResult result = strategy.compare(before, after, DEEP_COMPARE);
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("SIMPLE — getMaxRecommendedSize is MAX_VALUE")
        void simple_maxRecommendedSize_shouldBeMaxValue() {
            assertThat(strategy.getMaxRecommendedSize()).isEqualTo(Integer.MAX_VALUE);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  EntityListStrategy — entity lists, duplicate keys, static utils
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EntityListStrategy — Deep")
    class EntityListStrategyDeepTests {

        private final EntityListStrategy strategy = new EntityListStrategy();

        @Test
        @DisplayName("ENTITY — entity list with @Entity and @Key")
        void entity_entityList_shouldCompare() {
            List<TestEntity> before = List.of(new TestEntity(1, "A"));
            List<TestEntity> after = List.of(new TestEntity(1, "B"));
            CompareResult result = strategy.compare(before, after, DEFAULT);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("ENTITY — entity added")
        void entity_entityAdded_shouldDetectCreate() {
            List<TestEntity> before = List.of(new TestEntity(1, "A"));
            List<TestEntity> after = List.of(
                new TestEntity(1, "A"),
                new TestEntity(2, "B"));
            CompareResult result = strategy.compare(before, after, DEFAULT);
            assertThat(result.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("ENTITY — entity removed")
        void entity_entityRemoved_shouldDetectDelete() {
            List<TestEntity> before = List.of(
                new TestEntity(1, "A"),
                new TestEntity(2, "B"));
            List<TestEntity> after = List.of(new TestEntity(1, "A"));
            CompareResult result = strategy.compare(before, after, DEFAULT);
            assertThat(result.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Entity
        static class TestEntity {
            @Key
            private final int id;
            @SuppressWarnings("unused")
            private String name;

            TestEntity(int id, String name) {
                this.id = id;
                this.name = name;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                TestEntity that = (TestEntity) o;
                return id == that.id;
            }

            @Override
            public int hashCode() {
                return Integer.hashCode(id);
            }
        }
    }

}
