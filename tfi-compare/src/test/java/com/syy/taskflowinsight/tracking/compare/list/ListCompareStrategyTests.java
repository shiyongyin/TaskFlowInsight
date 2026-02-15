package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 5 种 ListCompareStrategy 单元测试
 *
 * <p>验证每种策略的基本行为、边界条件、以及移动检测特性。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("ListCompareStrategy — 5 种列表比较策略测试")
class ListCompareStrategyTests {

    private static final CompareOptions DEFAULT = CompareOptions.DEFAULT;

    // ──────────────────────────────────────────────────────────────
    //  SimpleListStrategy
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SimpleListStrategy (SIMPLE)")
    class SimpleTests {

        private final SimpleListStrategy strategy = new SimpleListStrategy();

        @Test
        @DisplayName("SL-001: 策略名称 = SIMPLE")
        void strategyName_shouldBeSimple() {
            assertThat(strategy.getStrategyName()).isEqualTo("SIMPLE");
        }

        @Test
        @DisplayName("SL-002: 不支持移动检测")
        void moveDetection_shouldBeFalse() {
            assertThat(strategy.supportsMoveDetection()).isFalse();
        }

        @Test
        @DisplayName("SL-003: 相同列表 → identical")
        void sameLists_shouldBeIdentical() {
            List<String> list = List.of("a", "b", "c");
            CompareResult result = strategy.compare(list, list, DEFAULT);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("SL-004: 元素变更 → UPDATE")
        void elementChanged_shouldDetectUpdate() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "x", "c");
            CompareResult result = strategy.compare(before, after, DEFAULT);
            assertThat(result.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("SL-005: 新增元素 → CREATE")
        void elementAdded_shouldDetectCreate() {
            List<String> before = List.of("a", "b");
            List<String> after = List.of("a", "b", "c");
            CompareResult result = strategy.compare(before, after, DEFAULT);
            assertThat(result.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("SL-006: 删除元素 → DELETE")
        void elementRemoved_shouldDetectDelete() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "b");
            CompareResult result = strategy.compare(before, after, DEFAULT);
            assertThat(result.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("SL-007: 空列表 → identical")
        void emptyLists_shouldBeIdentical() {
            CompareResult result = strategy.compare(
                    Collections.emptyList(), Collections.emptyList(), DEFAULT);
            assertThat(result.isIdentical()).isTrue();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  AsSetListStrategy
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AsSetListStrategy (AS_SET)")
    class AsSetTests {

        private final AsSetListStrategy strategy = new AsSetListStrategy();

        @Test
        @DisplayName("AS-001: 策略名称 = AS_SET")
        void strategyName_shouldBeAsSet() {
            assertThat(strategy.getStrategyName()).isEqualTo("AS_SET");
        }

        @Test
        @DisplayName("AS-002: 不支持移动检测")
        void moveDetection_shouldBeFalse() {
            assertThat(strategy.supportsMoveDetection()).isFalse();
        }

        @Test
        @DisplayName("AS-003: 忽略顺序 — 重排列 → identical")
        void reorderedList_shouldBeIdentical() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("c", "a", "b");
            CompareResult result = strategy.compare(before, after, DEFAULT);
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("AS-004: 集合差异 → CREATE / DELETE")
        void setDifference_shouldDetectAdditionsAndDeletions() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "d", "c");
            CompareResult result = strategy.compare(before, after, DEFAULT);
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("AS-005: 空列表 → identical")
        void emptyLists_shouldBeIdentical() {
            CompareResult result = strategy.compare(
                    Collections.emptyList(), Collections.emptyList(), DEFAULT);
            assertThat(result.isIdentical()).isTrue();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  LcsListStrategy
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LcsListStrategy (LCS)")
    class LcsTests {

        private final LcsListStrategy strategy = new LcsListStrategy();

        @Test
        @DisplayName("LCS-001: 策略名称")
        void strategyName_shouldNotBeNull() {
            assertThat(strategy.getStrategyName()).isNotBlank();
        }

        @Test
        @DisplayName("LCS-002: 支持移动检测")
        void moveDetection_shouldBeTrue() {
            assertThat(strategy.supportsMoveDetection()).isTrue();
        }

        @Test
        @DisplayName("LCS-003: 相同列表 → identical")
        void sameLists_shouldBeIdentical() {
            List<String> list = List.of("a", "b", "c");
            CompareResult result = strategy.compare(list, list, DEFAULT);
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("LCS-004: 插入元素检测")
        void insertedElement_shouldDetect() {
            List<String> before = List.of("a", "c");
            List<String> after = List.of("a", "b", "c");
            CompareResult result = strategy.compare(before, after, DEFAULT);
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("LCS-005: 空列表 → identical")
        void emptyLists_shouldBeIdentical() {
            CompareResult result = strategy.compare(
                    Collections.emptyList(), Collections.emptyList(), DEFAULT);
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("LCS-006: 大列表不抛异常")
        void largeLists_shouldNotThrow() {
            List<Integer> before = new ArrayList<>();
            List<Integer> after = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                before.add(i);
                after.add(i + 1);
            }
            assertThatCode(() -> strategy.compare(before, after, DEFAULT))
                    .doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  LevenshteinListStrategy
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LevenshteinListStrategy (LEVENSHTEIN)")
    class LevenshteinTests {

        private final LevenshteinListStrategy strategy = new LevenshteinListStrategy();

        @Test
        @DisplayName("LEV-001: 策略名称 = LEVENSHTEIN")
        void strategyName_shouldBeLevenshtein() {
            assertThat(strategy.getStrategyName()).isEqualTo("LEVENSHTEIN");
        }

        @Test
        @DisplayName("LEV-002: 支持移动检测")
        void moveDetection_shouldBeTrue() {
            assertThat(strategy.supportsMoveDetection()).isTrue();
        }

        @Test
        @DisplayName("LEV-003: 相同列表 → identical")
        void sameLists_shouldBeIdentical() {
            List<String> list = List.of("a", "b", "c");
            CompareResult result = strategy.compare(list, list, DEFAULT);
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("LEV-004: 单元素替换 → 检测变更")
        void singleReplacement_shouldDetectChange() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "x", "c");
            CompareResult result = strategy.compare(before, after, DEFAULT);
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("LEV-005: 空列表 → identical")
        void emptyLists_shouldBeIdentical() {
            CompareResult result = strategy.compare(
                    Collections.emptyList(), Collections.emptyList(), DEFAULT);
            assertThat(result.isIdentical()).isTrue();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  EntityListStrategy
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EntityListStrategy (ENTITY)")
    class EntityTests {

        private final EntityListStrategy strategy = new EntityListStrategy();

        @Test
        @DisplayName("EL-001: 策略名称")
        void strategyName_shouldNotBeNull() {
            assertThat(strategy.getStrategyName()).isNotBlank();
        }

        @Test
        @DisplayName("EL-002: 不支持移动检测")
        void moveDetection_shouldBeFalse() {
            assertThat(strategy.supportsMoveDetection()).isFalse();
        }

        @Test
        @DisplayName("EL-003: 非 Entity 列表 → fallback 到 identical/基本比较")
        void nonEntityList_shouldNotThrow() {
            List<String> list1 = List.of("a", "b");
            List<String> list2 = List.of("a", "c");
            assertThatCode(() -> strategy.compare(list1, list2, DEFAULT))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("EL-004: 空列表 → identical")
        void emptyLists_shouldBeIdentical() {
            CompareResult result = strategy.compare(
                    Collections.emptyList(), Collections.emptyList(), DEFAULT);
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("EL-005: extractPureEntityKey 工具方法")
        void extractPureEntityKey_shouldWork() {
            // 公开静态方法测试
            String key = EntityListStrategy.extractPureEntityKey("User[123]");
            assertThat(key).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  跨策略通用验证
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("跨策略通用验证")
    class CrossStrategyTests {

        @Test
        @DisplayName("CROSS-001: 所有策略 maxRecommendedSize > 0")
        void allStrategies_shouldHavePositiveMaxSize() {
            List<ListCompareStrategy> strategies = List.of(
                    new SimpleListStrategy(),
                    new AsSetListStrategy(),
                    new LcsListStrategy(),
                    new LevenshteinListStrategy(),
                    new EntityListStrategy()
            );
            for (ListCompareStrategy s : strategies) {
                assertThat(s.getMaxRecommendedSize())
                        .as("Strategy %s maxRecommendedSize", s.getStrategyName())
                        .isGreaterThan(0);
            }
        }

        @Test
        @DisplayName("CROSS-002: 所有策略策略名不为空")
        void allStrategies_shouldHaveNonBlankName() {
            List<ListCompareStrategy> strategies = List.of(
                    new SimpleListStrategy(),
                    new AsSetListStrategy(),
                    new LcsListStrategy(),
                    new LevenshteinListStrategy(),
                    new EntityListStrategy()
            );
            for (ListCompareStrategy s : strategies) {
                assertThat(s.getStrategyName()).isNotBlank();
            }
        }
    }
}
