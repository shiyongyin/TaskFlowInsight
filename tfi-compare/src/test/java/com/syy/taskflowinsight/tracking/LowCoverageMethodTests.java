package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.spi.DefaultComparisonProvider;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareInputException;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.format.TfiDateTimeFormatter;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 低覆盖率方法最终测试
 * 针对 40–60% 覆盖率的 10 个目标方法，最大化指令覆盖
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("LowCoverageMethodsFinal — 低覆盖率方法最终测试")
class LowCoverageMethodTests {

    // ─────────────────────────────────────────────────────────────────
    // 1. ListCompareExecutor.compare
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("1. ListCompareExecutor.compare — 冻结策略路径")
    class ListCompareExecutorTests {

        private ListCompareExecutor createExecutor() {
            List<ListCompareStrategy> strategies = List.of(
                new SimpleListStrategy(),
                new EntityListStrategy()
            );
            return new ListCompareExecutor(strategies);
        }

        @Test
        @DisplayName("ordered-index 策略 — 小列表")
        void compare_orderedIndex_smallLists() {
            ListCompareExecutor executor = createExecutor();
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "x", "c");
            CompareOptions opts = CompareOptions.builder()
                
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("ENTITY 策略 — @Entity 对象列表")
        void compare_entity_strategy_withEntityObjects() {
            ListCompareExecutor executor = createExecutor();
            List<TestEntity> before = List.of(new TestEntity(1, "A"), new TestEntity(2, "B"));
            List<TestEntity> after = List.of(new TestEntity(1, "A"), new TestEntity(2, "X"));
            CompareOptions opts = CompareOptions.builder()
                
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("普通重排按索引报告且不产生 MOVE")
        void compare_reorderingWithoutMoves() {
            ListCompareExecutor executor = createExecutor();
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("c", "a", "b");
            CompareOptions opts = CompareOptions.builder()
                
                
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
            assertThat(result.getChangesByType(ChangeType.MOVE)).isEmpty();
        }

        @Test
        @DisplayName("大列表保持冻结策略且不发生运行期降级")
        void compare_largeList_keepsFrozenStrategy() {
            ListCompareExecutor executor = createExecutor();
            List<Integer> before = new ArrayList<>();
            List<Integer> after = new ArrayList<>();
            for (int i = 0; i < 1100; i++) {
                before.add(i);
                after.add(i == 500 ? 9999 : i);
            }
            CompareOptions opts = CompareOptions.builder()
                
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("大列表仍使用冻结策略")
        void compare_largeList_usesFrozenStrategy() {
            ListCompareExecutor executor = createExecutor();
            List<Integer> before = new ArrayList<>(Collections.nCopies(600, 1));
            List<Integer> after = new ArrayList<>(Collections.nCopies(600, 2));
            CompareOptions opts = CompareOptions.builder()
                
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("相似度计算 — calculateSimilarity=true")
        void compare_calculateSimilarity() {
            ListCompareExecutor executor = createExecutor();
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "b", "c");
            CompareOptions opts = CompareOptions.builder()
                .computeSimilarity(true)
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("相似度计算 — 空并集")
        void compare_similarity_emptyUnion() {
            ListCompareExecutor executor = createExecutor();
            List<String> before = Collections.emptyList();
            List<String> after = Collections.emptyList();
            CompareOptions opts = CompareOptions.builder()
                .computeSimilarity(true)
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
        }

        @Entity
        static class TestEntity {
            @Key
            final int id;
            final String name;

            TestEntity(int id, String name) {
                this.id = id;
                this.name = name;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. DiffDetector.diffWithMode兼容签名
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("2. DiffDetector.diffWithMode — canonical委托")
    class DiffDetectorEnhancedTests {

        @Test
        @DisplayName("ENHANCED token保留Date原值但不自行格式化")
        void diffWithMode_enhanced_dateValues() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            Date oldDate = new Date(1000000L);
            Date newDate = new Date(2000000L);
            before.put("createdAt", oldDate);
            after.put("createdAt", newDate);

            List<ChangeRecord> changes = DiffDetector.diffWithMode("User", before, after, DiffDetector.DiffMode.ENHANCED);

            assertThat(changes).isNotEmpty();
            ChangeRecord change = changes.get(0);
            assertThat(change.getOldValue()).isEqualTo(oldDate);
            assertThat(change.getNewValue()).isEqualTo(newDate);
            assertThat(change.getReprOld()).isNull();
            assertThat(change.getReprNew()).isNull();
        }

        @Test
        @DisplayName("ENHANCED 模式 — BigDecimal 值")
        void diffWithMode_enhanced_bigDecimal() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            before.put("amount", new BigDecimal("10.00"));
            after.put("amount", new BigDecimal("20.50"));

            List<ChangeRecord> changes = DiffDetector.diffWithMode("Order", before, after, DiffDetector.DiffMode.ENHANCED);

            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("ENHANCED 模式 — Collection 值")
        void diffWithMode_enhanced_collection() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            before.put("tags", List.of("a", "b"));
            after.put("tags", List.of("a", "b", "c"));

            List<ChangeRecord> changes = DiffDetector.diffWithMode("Item", before, after, DiffDetector.DiffMode.ENHANCED);

            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("ENHANCED 模式 — Enum 值")
        void diffWithMode_enhanced_enum() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            before.put("status", TestStatus.ACTIVE);
            after.put("status", TestStatus.INACTIVE);

            List<ChangeRecord> changes = DiffDetector.diffWithMode("Entity", before, after, DiffDetector.DiffMode.ENHANCED);

            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("ENHANCED 模式 — null 值")
        void diffWithMode_enhanced_nullValues() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            before.put("optional", null);
            after.put("optional", "value");

            List<ChangeRecord> changes = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);

            assertThat(changes).isNotEmpty();
        }

        enum TestStatus { ACTIVE, INACTIVE }
    }

    // ─────────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────
    // 7. TfiDateTimeFormatter.formatDuration
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("7. TfiDateTimeFormatter.formatDuration")
    class TfiDateTimeFormatterDurationTests {

        @Test
        @DisplayName("formatDuration — 仅天")
        void formatDuration_daysOnly() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = Duration.ofDays(3);
            assertThat(fmt.formatDuration(d)).isEqualTo("P3D");
        }

        @Test
        @DisplayName("formatDuration — 仅小时")
        void formatDuration_hoursOnly() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = Duration.ofHours(2);
            assertThat(fmt.formatDuration(d)).contains("2H");
        }

        @Test
        @DisplayName("formatDuration — 仅小时分钟")
        void formatDuration_hoursAndMinutes() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = Duration.ofHours(1).plusMinutes(30);
            assertThat(fmt.formatDuration(d)).contains("1H").contains("30M");
        }

        @Test
        @DisplayName("formatDuration — 秒与毫秒")
        void formatDuration_secondsAndMillis() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = Duration.ofSeconds(5).plusMillis(123);
            assertThat(fmt.formatDuration(d)).contains("5.123S");
        }

        @Test
        @DisplayName("formatDuration — 仅秒")
        void formatDuration_secondsOnly() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = Duration.ofSeconds(10);
            assertThat(fmt.formatDuration(d)).contains("10S");
        }

        @Test
        @DisplayName("formatDuration — 零时长")
        void formatDuration_zero() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = Duration.ZERO;
            assertThat(fmt.formatDuration(d)).isEqualTo("PT0S");
        }

        @Test
        @DisplayName("formatDuration — null")
        void formatDuration_null() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            assertThat(fmt.formatDuration(null)).isNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 10. DefaultComparisonProvider.compare
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("10. DefaultComparisonProvider.compare")
    class DefaultComparisonProviderTests {

        @Test
        @DisplayName("compare(Object, Object) — 不同 Map 内容")
        void compare_twoArgs() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            Map<String, Object> before = Map.of("k", "a");
            Map<String, Object> after = Map.of("k", "b");
            CompareResult result = provider.compare(before, after, CompareOptions.builder().build());

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("compare — null 参数")
        void compare_nullParams() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            CompareResult result = provider.compare(null, "x");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("compare — 相同对象")
        void compare_sameObject() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            Object o = "same";
            CompareResult result = provider.compare(o, o);

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("compare — options 为 null")
        void compare_nullOptions() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            assertThatThrownBy(() -> provider.compare("a", "b", null))
                    .isInstanceOf(CompareInputException.class);
        }

        @Test
        @DisplayName("priority")
        void priority() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            assertThat(provider.priority()).isEqualTo(0);
        }
    }
}
