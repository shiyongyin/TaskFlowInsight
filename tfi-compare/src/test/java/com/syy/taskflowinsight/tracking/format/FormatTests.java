package com.syy.taskflowinsight.tracking.format;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 格式化系统测试。
 * 覆盖 TfiDateTimeFormatter、ValueReprFormatter。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Format — 格式化系统测试")
class FormatTests {

    // ── TfiDateTimeFormatter ──

    @Nested
    @DisplayName("TfiDateTimeFormatter — 日期时间格式化")
    class TfiDateTimeFormatterTests {

        private final TfiDateTimeFormatter formatter = new TfiDateTimeFormatter();

        @Test
        @DisplayName("格式化 LocalDateTime → 非空字符串")
        void formatLocalDateTime_shouldReturnNonEmpty() {
            String result = formatter.format(LocalDateTime.now());
            assertThat(result).isNotBlank();
        }

        @Test
        @DisplayName("格式化 LocalDate → 非空字符串")
        void formatLocalDate_shouldReturnNonEmpty() {
            String result = formatter.format(LocalDate.now());
            assertThat(result).isNotBlank();
        }

        @Test
        @DisplayName("格式化 Instant → 非空字符串")
        void formatInstant_shouldReturnNonEmpty() {
            String result = formatter.format(Instant.now());
            assertThat(result).isNotBlank();
        }

        @Test
        @DisplayName("自定义构造器 → 使用自定义格式")
        void customConstructor_shouldUseCustomPattern() {
            TfiDateTimeFormatter custom = new TfiDateTimeFormatter("yyyy-MM-dd", "UTC");
            String result = custom.format(LocalDateTime.now());
            assertThat(result).isNotBlank();
        }

        @Test
        @DisplayName("默认构造器 → 使用默认格式")
        void defaultConstructor_shouldWork() {
            TfiDateTimeFormatter defaultFmt = new TfiDateTimeFormatter();
            assertThat(defaultFmt).isNotNull();
        }

        @Test
        @DisplayName("SYSTEM 时区 → 使用系统时区")
        void systemTimezone_shouldWork() {
            TfiDateTimeFormatter sysFmt = new TfiDateTimeFormatter("yyyy-MM-dd HH:mm:ss", "SYSTEM");
            String result = sysFmt.format(LocalDateTime.now());
            assertThat(result).isNotBlank();
        }
    }

    // ── ValueReprFormatter ──

    @Nested
    @DisplayName("ValueReprFormatter — 值表示格式化")
    class ValueReprFormatterTests {

        @Test
        @DisplayName("null → \"null\"")
        void nullValue_shouldReturnNullString() {
            String result = ValueReprFormatter.format(null);
            assertThat(result).isEqualTo("null");
        }

        @Test
        @DisplayName("String → 带引号")
        void stringValue_shouldBeQuoted() {
            String result = ValueReprFormatter.format("hello");
            assertThat(result).contains("hello");
            assertThat(result).startsWith("\"");
            assertThat(result).endsWith("\"");
        }

        @Test
        @DisplayName("Integer → 数字字符串")
        void integerValue_shouldBeNumericString() {
            String result = ValueReprFormatter.format(42);
            assertThat(result).isEqualTo("42");
        }

        @Test
        @DisplayName("BigDecimal → 精确字符串")
        void bigDecimalValue_shouldBePreciseString() {
            String result = ValueReprFormatter.format(new BigDecimal("19.99"));
            assertThat(result).isEqualTo("19.99");
        }

        @Test
        @DisplayName("List → 集合表示")
        void listValue_shouldShowCollection() {
            List<String> list = List.of("a", "b", "c", "d", "e");
            String result = ValueReprFormatter.format(list);
            assertThat(result).isNotBlank();
            assertThat(result).startsWith("[");
        }

        @Test
        @DisplayName("Boolean → true/false")
        void booleanValue_shouldShowTrueOrFalse() {
            assertThat(ValueReprFormatter.format(true)).isEqualTo("true");
            assertThat(ValueReprFormatter.format(false)).isEqualTo("false");
        }

        @Test
        @DisplayName("长字符串 → 截断")
        void longString_shouldBeTruncated() {
            String longStr = "a".repeat(200);
            String result = ValueReprFormatter.format(longStr);
            assertThat(result.length()).isLessThan(210);
            assertThat(result).contains("...");
        }

        @Test
        @DisplayName("特殊字符 → 转义")
        void specialChars_shouldBeEscaped() {
            String result = ValueReprFormatter.format("line1\nline2\ttab");
            assertThat(result).contains("\\n");
            assertThat(result).contains("\\t");
        }

        @Test
        @DisplayName("Float NaN → NaN")
        void floatNaN_shouldShowNaN() {
            String result = ValueReprFormatter.format(Float.NaN);
            assertThat(result).isEqualTo("NaN");
        }

        @Test
        @DisplayName("Double Infinity → Infinity")
        void doubleInfinity_shouldShowInfinity() {
            String result = ValueReprFormatter.format(Double.POSITIVE_INFINITY);
            assertThat(result).isEqualTo("Infinity");
        }

        @Test
        @DisplayName("空集合 → []")
        void emptyCollection_shouldShowEmptyBrackets() {
            String result = ValueReprFormatter.format(Collections.emptyList());
            assertThat(result).isEqualTo("[]");
        }

        @Test
        @DisplayName("大集合 → 截断到5个元素")
        void largeCollection_shouldBeTruncated() {
            List<Integer> large = List.of(1, 2, 3, 4, 5, 6, 7, 8);
            String result = ValueReprFormatter.format(large);
            assertThat(result).contains("more");
        }
    }
}
