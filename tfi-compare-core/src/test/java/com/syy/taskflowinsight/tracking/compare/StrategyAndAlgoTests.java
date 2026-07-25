package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 比较策略和算法测试。
 * 覆盖 MapCompareStrategy、SetCompareStrategy、CollectionCompareStrategy、
 * ArrayCompareStrategy。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Strategy & Algorithm — 策略和算法测试")
class StrategyAndAlgoTests {

    // ── MapCompareStrategy ──

    @Nested
    @DisplayName("MapCompareStrategy — Map 比较")
    class MapCompareStrategyTests {

        private final MapCompareStrategy strategy = new MapCompareStrategy();

        @Test
        @DisplayName("相同 Map → identical")
        void sameMaps_shouldBeIdentical() {
            Map<String, Object> map = Map.of("k1", "v1", "k2", "v2");
            CompareResult result = strategy.compare(map, map, CompareOptions.builder().build());
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("不同值 → 检测到变更")
        void differentValues_shouldDetectChanges() {
            Map<String, Object> a = new HashMap<>(Map.of("k1", "v1", "k2", "v2"));
            Map<String, Object> b = new HashMap<>(Map.of("k1", "v1", "k2", "changed"));
            CompareResult result = strategy.compare(a, b, CompareOptions.builder().build());
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("新增键 → 检测到新增")
        void addedKey_shouldDetectAddition() {
            Map<String, Object> a = new HashMap<>(Map.of("k1", "v1"));
            Map<String, Object> b = new HashMap<>(Map.of("k1", "v1", "k2", "v2"));
            CompareResult result = strategy.compare(a, b, CompareOptions.builder().build());
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).anyMatch(c ->
                    c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("删除键 → 检测到删除")
        void removedKey_shouldDetectRemoval() {
            Map<String, Object> a = new HashMap<>(Map.of("k1", "v1", "k2", "v2"));
            Map<String, Object> b = new HashMap<>(Map.of("k1", "v1"));
            CompareResult result = strategy.compare(a, b, CompareOptions.builder().build());
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).anyMatch(c ->
                    c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("空 Map vs 非空 → 检测到差异")
        void emptyVsNonEmpty_shouldDetectDifferences() {
            CompareResult result = strategy.compare(
                    new HashMap<>(),
                    new HashMap<>(Map.of("k1", "v1")),
                    CompareOptions.builder().build()
            );
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("supports Map → true")
        void supportsMap_shouldReturnTrue() {
            assertThat(strategy.supports(Map.class)).isTrue();
            assertThat(strategy.supports(HashMap.class)).isTrue();
        }

        @Test
        @DisplayName("嵌套 Map → 递归比较")
        void nestedMaps_shouldCompareRecursively() {
            Map<String, Object> a = new HashMap<>();
            a.put("outer", new HashMap<>(Map.of("inner", "v1")));
            Map<String, Object> b = new HashMap<>();
            b.put("outer", new HashMap<>(Map.of("inner", "v2")));
            CompareResult result = strategy.compare(a, b, CompareOptions.builder().build());
            assertThat(result.isIdentical()).isFalse();
        }
    }

    // ── SetCompareStrategy ──

    @Nested
    @DisplayName("SetCompareStrategy — Set 比较")
    class SetCompareStrategyTests {

        private final SetCompareStrategy strategy = new SetCompareStrategy();

        @Test
        @DisplayName("相同 Set → identical")
        void sameSets_shouldBeIdentical() {
            Set<String> set = Set.of("a", "b", "c");
            CompareResult result = strategy.compare(set, set, CompareOptions.builder().build());
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("新增元素 → 检测到")
        void addedElement_shouldBeDetected() {
            Set<String> a = Set.of("a", "b");
            Set<String> b = Set.of("a", "b", "c");
            CompareResult result = strategy.compare(a, b, CompareOptions.builder().build());
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("删除元素 → 检测到")
        void removedElement_shouldBeDetected() {
            Set<String> a = Set.of("a", "b", "c");
            Set<String> b = Set.of("a", "b");
            CompareResult result = strategy.compare(a, b, CompareOptions.builder().build());
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("supports Set → true")
        void supportsSet_shouldReturnTrue() {
            assertThat(strategy.supports(Set.class)).isTrue();
            assertThat(strategy.supports(HashSet.class)).isTrue();
        }
    }

    // ── ArrayCompareStrategy ──

    @Nested
    @DisplayName("ArrayCompareStrategy — 数组比较")
    class ArrayCompareStrategyTests {

        private final ArrayCompareStrategy strategy = new ArrayCompareStrategy();

        @Test
        @DisplayName("相同数组 → identical")
        void sameArrays_shouldBeIdentical() {
            String[] arr = {"a", "b", "c"};
            CompareResult result = strategy.compare(arr, arr, CompareOptions.builder().build());
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("不同数组 → 检测差异")
        void differentArrays_shouldDetectDifferences() {
            String[] a = {"a", "b", "c"};
            String[] b = {"a", "d", "c"};
            CompareResult result = strategy.compare(a, b, CompareOptions.builder().build());
            assertThat(result.isIdentical()).isFalse();
        }
    }

}
