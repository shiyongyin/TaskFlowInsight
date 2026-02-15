package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Branch coverage tests for tracking/compare/list package.
 * Targets ListCompareExecutor, all list strategies, CompareRoutingProperties,
 * size degradation, K-pairs degradation, entity detection, and mixed types.
 *
 * @since 3.0.0
 */
@DisplayName("List Branch Coverage Tests")
class ListBranchCoverageTests {

    private static final CompareOptions DEFAULT = CompareOptions.DEFAULT;
    private static final CompareOptions DETECT_MOVES = CompareOptions.builder().detectMoves(true).build();
    private static final CompareOptions DEEP = CompareOptions.builder().enableDeepCompare(true).build();
    private static final CompareOptions WITH_SIMILARITY = CompareOptions.builder().calculateSimilarity(true).build();

    private ListCompareExecutor createExecutor() {
        return new ListCompareExecutor(List.of(
            new SimpleListStrategy(),
            new AsSetListStrategy(),
            new LcsListStrategy(),
            new LevenshteinListStrategy(),
            new EntityListStrategy()
        ));
    }

    // ── ListCompareExecutor ──

    @Nested
    @DisplayName("ListCompareExecutor — null/empty")
    class ExecutorNullEmpty {

        @Test
        @DisplayName("null list1")
        void nullList1() {
            ListCompareExecutor executor = createExecutor();
            CompareResult r = executor.compare(null, List.of("a"), DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("null list2")
        void nullList2() {
            ListCompareExecutor executor = createExecutor();
            CompareResult r = executor.compare(List.of("a"), null, DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("both null")
        void bothNull() {
            ListCompareExecutor executor = createExecutor();
            CompareResult r = executor.compare(null, null, DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("empty lists identical")
        void emptyLists() {
            ListCompareExecutor executor = createExecutor();
            CompareResult r = executor.compare(
                Collections.emptyList(),
                Collections.emptyList(),
                DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("same reference identical")
        void sameReference() {
            ListCompareExecutor executor = createExecutor();
            List<String> list = List.of("a", "b");
            CompareResult r = executor.compare(list, list, DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }
    }

    @Nested
    @DisplayName("ListCompareExecutor — strategy selection")
    class ExecutorStrategySelection {

        @Test
        @DisplayName("explicit SIMPLE")
        void explicitSimple() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("SIMPLE").build();
            CompareResult r = executor.compare(List.of("a", "b"), List.of("a", "x"), opts);
            assertThat(r.getAlgorithmUsed()).isEqualTo("SIMPLE");
        }

        @Test
        @DisplayName("explicit AS_SET")
        void explicitAsSet() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("AS_SET").build();
            CompareResult r = executor.compare(List.of("a", "b"), List.of("b", "a"), opts);
            assertThat(r.getAlgorithmUsed()).isEqualTo("AS_SET");
        }

        @Test
        @DisplayName("explicit LCS")
        void explicitLcs() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("LCS").build();
            CompareResult r = executor.compare(
                List.of("a", "b", "c"),
                List.of("a", "x", "c"),
                opts);
            assertThat(r.getAlgorithmUsed()).isEqualTo("LCS");
        }

        @Test
        @DisplayName("explicit LEVENSHTEIN")
        void explicitLevenshtein() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("LEVENSHTEIN").build();
            CompareResult r = executor.compare(List.of("a", "b"), List.of("a", "x"), opts);
            assertThat(r.getAlgorithmUsed()).isEqualTo("LEVENSHTEIN");
        }

        @Test
        @DisplayName("explicit ENTITY with entity list")
        void explicitEntity() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("ENTITY").build();
            List<TestEntity> a = List.of(new TestEntity(1, "A"));
            List<TestEntity> b = List.of(new TestEntity(1, "B"));
            CompareResult r = executor.compare(a, b, opts);
            assertThat(r.getAlgorithmUsed()).isEqualTo("ENTITY");
        }

        @Test
        @DisplayName("invalid strategy name falls back to auto-routing")
        void invalidStrategyName() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("INVALID_STRATEGY").build();
            CompareResult r = executor.compare(List.of("a"), List.of("b"), opts);
            assertThat(r).isNotNull();
        }
    }

    @Nested
    @DisplayName("ListCompareExecutor — similarity")
    class ExecutorSimilarity {

        @Test
        @DisplayName("calculateSimilarity empty union")
        void similarityEmptyUnion() {
            ListCompareExecutor executor = createExecutor();
            CompareResult r = executor.compare(
                Collections.emptyList(),
                Collections.emptyList(),
                WITH_SIMILARITY);
            assertThat(r.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("calculateSimilarity non-empty")
        void similarityNonEmpty() {
            ListCompareExecutor executor = createExecutor();
            CompareResult r = executor.compare(
                List.of("a", "b"),
                List.of("a", "b"),
                WITH_SIMILARITY);
            assertThat(r.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("calculateSimilarity partial overlap")
        void similarityPartialOverlap() {
            ListCompareExecutor executor = createExecutor();
            CompareResult r = executor.compare(
                List.of("a", "b"),
                List.of("a", "c"),
                WITH_SIMILARITY);
            assertThat(r.getSimilarity()).isNotNull();
            assertThat(r.getSimilarity()).isBetween(0.0, 1.0);
        }
    }

    @Nested
    @DisplayName("ListCompareExecutor — size degradation")
    class ExecutorSizeDegradation {

        @Test
        @DisplayName("size exceeds threshold triggers degradation")
        void sizeExceedsThreshold() {
            ListCompareExecutor executor = createExecutor();
            List<String> large = new ArrayList<>();
            for (int i = 0; i < 600; i++) {
                large.add("item" + i);
            }
            List<String> large2 = new ArrayList<>(large);
            large2.set(0, "changed");
            CompareResult r = executor.compare(large, large2, DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("force simple threshold")
        void forceSimpleThreshold() {
            ListCompareExecutor executor = createExecutor();
            List<String> huge = new ArrayList<>();
            for (int i = 0; i < 1100; i++) {
                huge.add("item" + i);
            }
            CompareOptions opts = CompareOptions.builder().strategyName("LCS").build();
            CompareResult r = executor.compare(huge, new ArrayList<>(huge), opts);
            assertThat(r).isNotNull();
        }
    }

    @Nested
    @DisplayName("LcsListStrategy — branches")
    class LcsListStrategyBranches {

        private final LcsListStrategy strategy = new LcsListStrategy();

        @Test
        @DisplayName("LCS size exceeds max returns degraded")
        void lcsSizeExceeded() {
            List<String> large = new ArrayList<>();
            for (int i = 0; i < 350; i++) {
                large.add("x" + i);
            }
            CompareResult r = strategy.compare(large, new ArrayList<>(large), DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.getDegradationReasons()).isNotEmpty();
        }

        @Test
        @DisplayName("LCS with detectMoves coalesces to MOVE")
        void lcsDetectMoves() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("b", "a", "c");
            CompareResult r = strategy.compare(before, after, DETECT_MOVES);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.MOVE);
        }

        @Test
        @DisplayName("LCS without detectMoves")
        void lcsNoDetectMoves() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("b", "a", "c");
            CompareResult r = strategy.compare(before, after, DEFAULT);
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("LCS single element CREATE")
        void lcsSingleCreate() {
            List<String> before = Collections.emptyList();
            List<String> after = List.of("x");
            CompareResult r = strategy.compare(before, after, DEFAULT);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("LCS single element DELETE")
        void lcsSingleDelete() {
            List<String> before = List.of("x");
            List<String> after = Collections.emptyList();
            CompareResult r = strategy.compare(before, after, DEFAULT);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("getMaxRecommendedSize")
        void lcsMaxRecommendedSize() {
            assertThat(strategy.getMaxRecommendedSize()).isEqualTo(300);
        }

        @Test
        @DisplayName("supportsMoveDetection")
        void lcsSupportsMoveDetection() {
            assertThat(strategy.supportsMoveDetection()).isTrue();
        }
    }

    @Nested
    @DisplayName("LevenshteinListStrategy — branches")
    class LevenshteinListStrategyBranches {

        private final LevenshteinListStrategy strategy = new LevenshteinListStrategy();

        @Test
        @DisplayName("REPLACE detection")
        void levenshteinReplace() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "x", "c");
            CompareResult r = strategy.compare(before, after, DEFAULT);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("move detection")
        void levenshteinMove() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("c", "a", "b");
            CompareResult r = strategy.compare(before, after, DETECT_MOVES);
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("getMaxRecommendedSize")
        void levenshteinMaxRecommendedSize() {
            assertThat(strategy.getMaxRecommendedSize()).isEqualTo(500);
        }
    }

    @Nested
    @DisplayName("SimpleListStrategy — branches")
    class SimpleListStrategyBranches {

        private final SimpleListStrategy strategy = new SimpleListStrategy();

        @Test
        @DisplayName("deep compare nested objects")
        void simpleDeepCompare() {
            List<Object> before = List.of(Map.of("x", 1));
            List<Object> after = List.of(Map.of("x", 2));
            CompareResult r = strategy.compare(before, after, DEEP);
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("getMaxRecommendedSize")
        void simpleMaxRecommendedSize() {
            assertThat(strategy.getMaxRecommendedSize()).isEqualTo(Integer.MAX_VALUE);
        }
    }

    @Nested
    @DisplayName("AsSetListStrategy — branches")
    class AsSetListStrategyBranches {

        private final AsSetListStrategy strategy = new AsSetListStrategy();

        @Test
        @DisplayName("duplicate elements")
        void asSetDuplicates() {
            List<String> before = List.of("a", "a", "b");
            List<String> after = List.of("a", "b", "b");
            CompareResult r = strategy.compare(before, after, DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("getMaxRecommendedSize")
        void asSetMaxRecommendedSize() {
            assertThat(strategy.getMaxRecommendedSize()).isEqualTo(10000);
        }
    }

    @Nested
    @DisplayName("EntityListStrategy — branches")
    class EntityListStrategyBranches {

        private final EntityListStrategy strategy = new EntityListStrategy();

        @Test
        @DisplayName("non-entity list returns empty changes")
        void nonEntityList() {
            List<String> a = List.of("a", "b");
            List<String> b = List.of("a", "c");
            CompareResult r = strategy.compare(a, b, DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.getChanges()).isEmpty();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("entity list with @Entity and @Key")
        void entityList() {
            List<TestEntity> before = List.of(new TestEntity(1, "A"));
            List<TestEntity> after = List.of(new TestEntity(1, "B"));
            CompareResult r = strategy.compare(before, after, DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("entity CREATE")
        void entityCreate() {
            List<TestEntity> before = List.of(new TestEntity(1, "A"));
            List<TestEntity> after = List.of(
                new TestEntity(1, "A"),
                new TestEntity(2, "B"));
            CompareResult r = strategy.compare(before, after, DEFAULT);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("entity DELETE")
        void entityDelete() {
            List<TestEntity> before = List.of(
                new TestEntity(1, "A"),
                new TestEntity(2, "B"));
            List<TestEntity> after = List.of(new TestEntity(1, "A"));
            CompareResult r = strategy.compare(before, after, DEFAULT);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("entity duplicate keys")
        void entityDuplicateKeys() {
            List<TestEntity> before = List.of(
                new TestEntity(1, "A"),
                new TestEntity(1, "A2"));
            List<TestEntity> after = List.of(new TestEntity(1, "B"));
            CompareResult r = strategy.compare(before, after, DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.getDuplicateKeys()).contains("1");
        }

        @Test
        @DisplayName("extractPureEntityKey with #idx")
        void extractPureEntityKeyWithIdx() {
            assertThat(EntityListStrategy.extractPureEntityKey("entity[1#0]")).isEqualTo("1");
            assertThat(EntityListStrategy.extractPureEntityKey("entity[123:US#1]")).isEqualTo("123:US");
        }

        @Test
        @DisplayName("extractPureEntityKey without #idx")
        void extractPureEntityKeyWithoutIdx() {
            assertThat(EntityListStrategy.extractPureEntityKey("entity[1]")).isEqualTo("1");
        }

        @Test
        @DisplayName("extractPureEntityKey null/empty")
        void extractPureEntityKeyNullEmpty() {
            assertThat(EntityListStrategy.extractPureEntityKey(null)).isNull();
            assertThat(EntityListStrategy.extractPureEntityKey("")).isEmpty();
        }

        @Test
        @DisplayName("extractDuplicateIndex")
        void extractDuplicateIndex() {
            assertThat(EntityListStrategy.extractDuplicateIndex("entity[1#0]")).isEqualTo(0);
            assertThat(EntityListStrategy.extractDuplicateIndex("entity[1#5]")).isEqualTo(5);
            assertThat(EntityListStrategy.extractDuplicateIndex("entity[1]")).isEqualTo(-1);
            assertThat(EntityListStrategy.extractDuplicateIndex(null)).isEqualTo(-1);
        }

        @Test
        @DisplayName("supportsMoveDetection false")
        void entitySupportsMoveDetection() {
            assertThat(strategy.supportsMoveDetection()).isFalse();
        }

        @Test
        @DisplayName("getMaxRecommendedSize")
        void entityMaxRecommendedSize() {
            assertThat(strategy.getMaxRecommendedSize()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("CompareRoutingProperties")
    class CompareRoutingPropertiesBranches {

        @Test
        @DisplayName("entity config defaults")
        void entityConfigDefaults() {
            CompareRoutingProperties props = new CompareRoutingProperties();
            assertThat(props.getEntity()).isNotNull();
            assertThat(props.getEntity().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("lcs config defaults")
        void lcsConfigDefaults() {
            CompareRoutingProperties props = new CompareRoutingProperties();
            assertThat(props.getLcs()).isNotNull();
            assertThat(props.getLcs().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("setters")
        void setters() {
            CompareRoutingProperties props = new CompareRoutingProperties();
            props.getEntity().setEnabled(false);
            assertThat(props.getEntity().isEnabled()).isFalse();
            props.getLcs().setPreferLcsWhenDetectMoves(false);
            assertThat(props.getLcs().isPreferLcsWhenDetectMoves()).isFalse();
        }
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
