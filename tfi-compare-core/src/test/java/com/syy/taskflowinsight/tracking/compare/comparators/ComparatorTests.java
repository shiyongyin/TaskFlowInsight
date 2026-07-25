package com.syy.taskflowinsight.tracking.compare.comparators;

import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import com.syy.taskflowinsight.tracking.compare.PropertyComparisonException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * 属性比较器测试。
 * 覆盖 BigDecimalScaleComparator、TrimCaseComparator、
 * CaseInsensitiveComparator、EnumSemanticComparator、UrlCanonicalComparator。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Comparators — 属性比较器测试")
class ComparatorTests {

    // ── BigDecimalScaleComparator ──

    @Nested
    @DisplayName("BigDecimalScaleComparator")
    class BigDecimalScaleComparatorTests {

        @Test
        @DisplayName("相同值不同 scale → 相等")
        void sameValueDifferentScale_shouldBeEqual() throws PropertyComparisonException {
            BigDecimalScaleComparator comparator = new BigDecimalScaleComparator(2, 0);
            boolean result = comparator.areEqual(
                    new BigDecimal("10.00"), new BigDecimal("10.0"), null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("不同值 → 不等")
        void differentValues_shouldNotBeEqual() throws PropertyComparisonException {
            BigDecimalScaleComparator comparator = new BigDecimalScaleComparator(2, 0);
            boolean result = comparator.areEqual(
                    new BigDecimal("10.00"), new BigDecimal("10.01"), null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("tolerance 容差内 → 相等")
        void withinTolerance_shouldBeEqual() throws PropertyComparisonException {
            BigDecimalScaleComparator comparator = new BigDecimalScaleComparator(2, 0.005);
            boolean result = comparator.areEqual(
                    new BigDecimal("10.000"), new BigDecimal("10.004"), null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Number 类型支持")
        void numberTypes_shouldBeSupported() {
            BigDecimalScaleComparator comparator = new BigDecimalScaleComparator(2, 0);
            assertThat(comparator.supports(BigDecimal.class)).isTrue();
            assertThat(comparator.supports(Integer.class)).isTrue();
            assertThat(comparator.supports(Double.class)).isTrue();
        }

        @Test
        @DisplayName("String 数字 → 支持")
        void stringNumbers_shouldBeSupported() {
            BigDecimalScaleComparator comparator = new BigDecimalScaleComparator(2, 0);
            assertThat(comparator.supports(String.class)).isTrue();
        }

        @Test
        @DisplayName("Integer 比较 → 正确处理")
        void integerComparison_shouldWork() throws PropertyComparisonException {
            BigDecimalScaleComparator comparator = new BigDecimalScaleComparator(0, 0);
            boolean result = comparator.areEqual(100, 100, null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("非数字字符串 → 抛 PropertyComparisonException")
        void nonNumericString_shouldThrow() {
            BigDecimalScaleComparator comparator = new BigDecimalScaleComparator(2, 0);
            assertThatThrownBy(() -> comparator.areEqual("abc", "def", null))
                    .isInstanceOf(PropertyComparisonException.class);
        }

        @Test
        @DisplayName("getName 非空")
        void getName_shouldNotBeBlank() {
            BigDecimalScaleComparator comparator = new BigDecimalScaleComparator(2, 0);
            assertThat(comparator.getName()).isNotBlank();
        }
    }

    // ── TrimCaseComparator ──

    @Nested
    @DisplayName("TrimCaseComparator")
    class TrimCaseComparatorTests {

        private final TrimCaseComparator comparator = new TrimCaseComparator();

        @Test
        @DisplayName("trim + 大小写不敏感 → 相等")
        void trimAndCaseInsensitive_shouldBeEqual() throws PropertyComparisonException {
            assertThat(comparator.areEqual("  Hello World  ", "hello world", null)).isTrue();
        }

        @Test
        @DisplayName("多空格规范化 → 相等")
        void multipleSpaces_shouldBeNormalized() throws PropertyComparisonException {
            assertThat(comparator.areEqual("hello  world", "Hello World", null)).isTrue();
        }

        @Test
        @DisplayName("不同内容 → 不等")
        void differentContent_shouldNotBeEqual() throws PropertyComparisonException {
            assertThat(comparator.areEqual("hello", "world", null)).isFalse();
        }
    }

    // ── CaseInsensitiveComparator ──

    @Nested
    @DisplayName("CaseInsensitiveComparator")
    class CaseInsensitiveComparatorTests {

        private final CaseInsensitiveComparator comparator = new CaseInsensitiveComparator();

        @Test
        @DisplayName("大小写不同 → 相等")
        void differentCase_shouldBeEqual() throws PropertyComparisonException {
            assertThat(comparator.areEqual("HELLO", "hello", null)).isTrue();
        }

        @Test
        @DisplayName("不同内容 → 不等")
        void differentContent_shouldNotBeEqual() throws PropertyComparisonException {
            assertThat(comparator.areEqual("hello", "world", null)).isFalse();
        }
    }

    // ── EnumSemanticComparator ──

    @Nested
    @DisplayName("EnumSemanticComparator")
    class EnumSemanticComparatorTests {

        private final EnumSemanticComparator comparator = new EnumSemanticComparator();

        @Test
        @DisplayName("相同枚举值 → 相等")
        void sameEnum_shouldBeEqual() throws PropertyComparisonException {
            assertThat(comparator.areEqual(TestEnum.ACTIVE, TestEnum.ACTIVE, null)).isTrue();
        }

        @Test
        @DisplayName("不同枚举值 → 不等")
        void differentEnum_shouldNotBeEqual() throws PropertyComparisonException {
            assertThat(comparator.areEqual(TestEnum.ACTIVE, TestEnum.INACTIVE, null)).isFalse();
        }

        @Test
        @DisplayName("字符串 vs 枚举 → valueOf 语义")
        void stringVsEnum_shouldCompareByName() throws PropertyComparisonException {
            assertThat(comparator.areEqual("ACTIVE", TestEnum.ACTIVE, null)).isTrue();
        }
    }

    // ── UrlCanonicalComparator ──

    @Nested
    @DisplayName("UrlCanonicalComparator")
    class UrlCanonicalComparatorTests {

        private final UrlCanonicalComparator comparator = new UrlCanonicalComparator();

        @Test
        @DisplayName("相同 URL → 相等")
        void sameUrl_shouldBeEqual() throws PropertyComparisonException {
            assertThat(comparator.areEqual(
                    "https://example.com/path",
                    "https://example.com/path", null)).isTrue();
        }

        @Test
        @DisplayName("不同 URL → 不等")
        void differentUrl_shouldNotBeEqual() throws PropertyComparisonException {
            assertThat(comparator.areEqual(
                    "https://example.com/a",
                    "https://example.com/b", null)).isFalse();
        }
    }

    enum TestEnum { ACTIVE, INACTIVE }
}
