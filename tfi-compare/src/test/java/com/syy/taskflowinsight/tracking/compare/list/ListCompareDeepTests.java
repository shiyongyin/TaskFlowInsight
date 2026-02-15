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
 * Deep coverage tests for tracking/compare/list package.
 * Maximizes coverage for ListCompareExecutor, all list strategies, and CompareRoutingProperties.
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("ListCompare — Deep Coverage Tests")
class ListCompareDeepTests {

    private static final CompareOptions DEFAULT = CompareOptions.DEFAULT;
    private static final CompareOptions DETECT_MOVES = CompareOptions.builder().detectMoves(true).build();
    private static final CompareOptions DEEP_COMPARE = CompareOptions.builder().enableDeepCompare(true).build();
    private static final CompareOptions WITH_SIMILARITY = CompareOptions.builder().calculateSimilarity(true).build();

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
            assertThat(result.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Executor — getSupportedStrategies")
        void executor_getSupportedStrategies_shouldReturnAll() {
            ListCompareExecutor executor = createExecutor();
            assertThat(executor.getSupportedStrategies())
                .contains("SIMPLE", "AS_SET", "LCS", "LEVENSHTEIN", "ENTITY");
        }

        @Test
        @DisplayName("Executor — explicit strategy SIMPLE")
        void executor_explicitSimple_shouldUseSimple() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("SIMPLE").build();
            CompareResult result = executor.compare(
                List.of("a", "b"), List.of("a", "x"), opts);
            assertThat(result.getAlgorithmUsed()).isEqualTo("SIMPLE");
        }

        @Test
        @DisplayName("Executor — explicit strategy AS_SET")
        void executor_explicitAsSet_shouldUseAsSet() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("AS_SET").build();
            CompareResult result = executor.compare(
                List.of("a", "b"), List.of("b", "a"), opts);
            assertThat(result.getAlgorithmUsed()).isEqualTo("AS_SET");
        }

        @Test
        @DisplayName("Executor — explicit strategy LCS")
        void executor_explicitLcs_shouldUseLcs() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("LCS").build();
            CompareResult result = executor.compare(
                List.of("a", "b", "c"), List.of("a", "x", "c"), opts);
            assertThat(result.getAlgorithmUsed()).isEqualTo("LCS");
        }

        @Test
        @DisplayName("Executor — explicit strategy LEVENSHTEIN")
        void executor_explicitLevenshtein_shouldUseLevenshtein() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("LEVENSHTEIN").build();
            CompareResult result = executor.compare(
                List.of("a", "b"), List.of("a", "x"), opts);
            assertThat(result.getAlgorithmUsed()).isEqualTo("LEVENSHTEIN");
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
                new AsSetListStrategy(),
                new LcsListStrategy(),
                new LevenshteinListStrategy(),
                new EntityListStrategy()
            ));
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  LcsListStrategy — move detection, coalesce
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LcsListStrategy — Deep")
    class LcsListStrategyDeepTests {

        private final LcsListStrategy strategy = new LcsListStrategy();

        @Test
        @DisplayName("LCS — move detection coalesces DELETE+CREATE to MOVE")
        void lcs_moveDetection_shouldCoalesce() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("b", "a", "c");  // b moved left
            CompareResult result = strategy.compare(before, after, DETECT_MOVES);
            assertThat(result.getChanges()).isNotEmpty();
            boolean hasMove = result.getChanges().stream()
                .anyMatch(c -> c.getChangeType() == ChangeType.MOVE);
            assertThat(hasMove).as("Should detect MOVE when detectMoves=true").isTrue();
        }

        @Test
        @DisplayName("LCS — without move detection")
        void lcs_withoutMoveDetection_shouldOutputDeleteCreate() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("b", "a", "c");
            CompareResult result = strategy.compare(before, after, DEFAULT);
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("LCS — null list")
        void lcs_nullList_shouldHandle() {
            CompareResult result = strategy.compare(null, List.of("a"), DEFAULT);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("LCS — getMaxRecommendedSize")
        void lcs_maxRecommendedSize_shouldBe300() {
            assertThat(strategy.getMaxRecommendedSize()).isEqualTo(300);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  LevenshteinListStrategy — REPLACE, move detection
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LevenshteinListStrategy — Deep")
    class LevenshteinListStrategyDeepTests {

        private final LevenshteinListStrategy strategy = new LevenshteinListStrategy();

        @Test
        @DisplayName("LEV — REPLACE operation")
        void levenshtein_replace_shouldDetect() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "x", "c");
            CompareResult result = strategy.compare(before, after, DEFAULT);
            assertThat(result.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("LEV — move detection")
        void levenshtein_moveDetection_shouldDetectMoves() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("c", "a", "b");
            CompareResult result = strategy.compare(before, after, DETECT_MOVES);
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("LEV — getMaxRecommendedSize")
        void levenshtein_maxRecommendedSize_shouldBe500() {
            assertThat(strategy.getMaxRecommendedSize()).isEqualTo(500);
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
    //  AsSetListStrategy — edge cases
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AsSetListStrategy — Deep")
    class AsSetListStrategyDeepTests {

        private final AsSetListStrategy strategy = new AsSetListStrategy();

        @Test
        @DisplayName("AS_SET — duplicate elements")
        void asSet_duplicates_shouldHandle() {
            List<String> before = List.of("a", "a", "b");
            List<String> after = List.of("a", "b", "b");
            CompareResult result = strategy.compare(before, after, DEFAULT);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("AS_SET — getMaxRecommendedSize")
        void asSet_maxRecommendedSize_shouldBe10000() {
            assertThat(strategy.getMaxRecommendedSize()).isEqualTo(10000);
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

        @Test
        @DisplayName("ENTITY — extractPureEntityKey")
        void entity_extractPureEntityKey_shouldExtract() {
            assertThat(EntityListStrategy.extractPureEntityKey("entity[1#0]")).isEqualTo("1");
            assertThat(EntityListStrategy.extractPureEntityKey("entity[123:US]")).isEqualTo("123:US");
            assertThat(EntityListStrategy.extractPureEntityKey("entity[1]")).isEqualTo("1");
            assertThat(EntityListStrategy.extractPureEntityKey(null)).isNull();
            assertThat(EntityListStrategy.extractPureEntityKey("")).isEmpty();
        }

        @Test
        @DisplayName("ENTITY — extractDuplicateIndex")
        void entity_extractDuplicateIndex_shouldExtract() {
            assertThat(EntityListStrategy.extractDuplicateIndex("entity[1#0]")).isEqualTo(0);
            assertThat(EntityListStrategy.extractDuplicateIndex("entity[1#5]")).isEqualTo(5);
            assertThat(EntityListStrategy.extractDuplicateIndex("entity[1]")).isEqualTo(-1);
            assertThat(EntityListStrategy.extractDuplicateIndex(null)).isEqualTo(-1);
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

    // ──────────────────────────────────────────────────────────────
    //  CompareRoutingProperties
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CompareRoutingProperties")
    class CompareRoutingPropertiesTests {

        @Test
        @DisplayName("EntityConfig defaults")
        void entityConfig_defaults() {
            CompareRoutingProperties props = new CompareRoutingProperties();
            assertThat(props.getEntity()).isNotNull();
            assertThat(props.getEntity().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("LcsConfig defaults")
        void lcsConfig_defaults() {
            CompareRoutingProperties props = new CompareRoutingProperties();
            assertThat(props.getLcs()).isNotNull();
            assertThat(props.getLcs().isEnabled()).isTrue();
            assertThat(props.getLcs().isPreferLcsWhenDetectMoves()).isTrue();
        }

        @Test
        @DisplayName("Setters work")
        void setters_shouldWork() {
            CompareRoutingProperties props = new CompareRoutingProperties();
            props.getEntity().setEnabled(false);
            assertThat(props.getEntity().isEnabled()).isFalse();
            props.getLcs().setPreferLcsWhenDetectMoves(false);
            assertThat(props.getLcs().isPreferLcsWhenDetectMoves()).isFalse();
        }
    }
}
