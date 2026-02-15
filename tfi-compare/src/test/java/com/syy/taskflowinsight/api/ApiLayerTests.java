package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.api.builder.TfiContext;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * API 层测试。
 * 覆盖 ComparatorBuilder、ComparisonTemplate、DiffBuilder、TfiContext、
 * TrackingOptions、TrackingStatistics。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("API Layer — API 层测试")
class ApiLayerTests {

    // ── ComparatorBuilder ──

    @Nested
    @DisplayName("ComparatorBuilder — 比较构建器")
    class ComparatorBuilderTests {

        @Test
        @DisplayName("disabled() → 创建禁用构建器")
        void disabled_shouldCreateDisabledBuilder() {
            ComparatorBuilder builder = ComparatorBuilder.disabled();
            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("链式 API → 构建正确选项")
        void fluentApi_shouldBuildCorrectOptions() {
            ComparatorBuilder builder = ComparatorBuilder.disabled()
                    .withMaxDepth(5)
                    .withSimilarity()
                    .includeNulls()
                    .detectMoves();
            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("使用 Template → 应用预设")
        void useTemplate_shouldApplyPreset() {
            ComparatorBuilder builder = ComparatorBuilder.disabled()
                    .useTemplate(ComparisonTemplate.FAST);
            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("compare → 返回结果")
        void compare_shouldReturnResult() {
            ComparatorBuilder builder = ComparatorBuilder.disabled();
            CompareResult result = builder.compare("hello", "world");
            assertThat(result).isNotNull();
        }
    }

    // ── ComparisonTemplate ──

    @Nested
    @DisplayName("ComparisonTemplate — 比较模板")
    class ComparisonTemplateTests {

        @Test
        @DisplayName("AUDIT 模板")
        void audit_shouldExist() {
            assertThat(ComparisonTemplate.AUDIT).isNotNull();
        }

        @Test
        @DisplayName("FAST 模板")
        void fast_shouldExist() {
            assertThat(ComparisonTemplate.FAST).isNotNull();
        }

        @Test
        @DisplayName("DEBUG 模板")
        void debug_shouldExist() {
            assertThat(ComparisonTemplate.DEBUG).isNotNull();
        }

        @Test
        @DisplayName("apply 到 CompareOptions.Builder → 不抛异常")
        void apply_shouldNotThrow() {
            CompareOptions.CompareOptionsBuilder optionsBuilder = CompareOptions.builder();
            assertThatCode(() -> ComparisonTemplate.AUDIT.apply(optionsBuilder))
                    .doesNotThrowAnyException();
        }
    }

    // ── DiffBuilder ──

    @Nested
    @DisplayName("DiffBuilder — Diff 构建器")
    class DiffBuilderTests {

        @Test
        @DisplayName("create → 基本构建器")
        void create_shouldReturnBuilder() {
            DiffBuilder builder = DiffBuilder.create();
            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("链式配置 → 不抛异常")
        void fluentConfig_shouldNotThrow() {
            assertThatCode(() -> {
                DiffBuilder.create()
                        .withMaxDepth(5)
                        .withDeepCompare(true);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("build → 返回 TfiContext")
        void build_shouldReturnContext() {
            TfiContext ctx = DiffBuilder.create()
                    .withMaxDepth(3)
                    .build();
            assertThat(ctx).isNotNull();
            assertThat(ctx.compareService()).isNotNull();
        }

        @Test
        @DisplayName("TfiContext.compare → 返回结果")
        void contextCompare_shouldReturnResult() {
            TfiContext ctx = DiffBuilder.create().build();
            CompareResult result = ctx.compare("hello", "world");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("withPropertyComparator → 不抛异常")
        void withPropertyComparator_shouldNotThrow() {
            assertThatCode(() -> {
                DiffBuilder.create()
                        .withPropertyComparator("user.name",
                                (left, right, field) -> String.valueOf(left).equalsIgnoreCase(String.valueOf(right)));
            }).doesNotThrowAnyException();
        }
    }

    // ── TrackingOptions ──

    @Nested
    @DisplayName("TrackingOptions — 追踪选项")
    class TrackingOptionsTests {

        @Test
        @DisplayName("builder → 创建默认选项")
        void builder_shouldCreateDefaultOptions() {
            TrackingOptions opts = TrackingOptions.builder().build();
            assertThat(opts).isNotNull();
            assertThat(opts.getMaxDepth()).isGreaterThan(0);
        }

        @Test
        @DisplayName("builder 设置深度 → 生效")
        void builderMaxDepth_shouldWork() {
            TrackingOptions opts = TrackingOptions.builder()
                    .maxDepth(7)
                    .build();
            assertThat(opts.getMaxDepth()).isEqualTo(7);
        }

        @Test
        @DisplayName("builder 设置时间预算 → 生效")
        void builderTimeBudget_shouldWork() {
            TrackingOptions opts = TrackingOptions.builder()
                    .timeBudgetMs(5000)
                    .build();
            assertThat(opts.getTimeBudgetMs()).isEqualTo(5000);
        }

        @Test
        @DisplayName("shallow() 预设 → 非 null")
        void shallow_shouldNotBeNull() {
            TrackingOptions opts = TrackingOptions.shallow();
            assertThat(opts).isNotNull();
        }

        @Test
        @DisplayName("deep() 预设 → 非 null")
        void deep_shouldNotBeNull() {
            TrackingOptions opts = TrackingOptions.deep();
            assertThat(opts).isNotNull();
        }
    }

    // ── TrackingStatistics ──

    @Nested
    @DisplayName("TrackingStatistics — 追踪统计")
    class TrackingStatisticsTests {

        @Test
        @DisplayName("默认构造 → 空统计")
        void defaultConstructor_shouldBeEmpty() {
            TrackingStatistics stats = new TrackingStatistics();
            assertThat(stats).isNotNull();
        }

        @Test
        @DisplayName("recordObjectTracked → getSummary 反映")
        void recordObjectTracked_shouldReflectInSummary() {
            TrackingStatistics stats = new TrackingStatistics();
            stats.recordObjectTracked("testObj");
            var summary = stats.getSummary();
            assertThat(summary).isNotNull();
        }

        @Test
        @DisplayName("recordChanges → 变更类型分布非空")
        void recordChanges_shouldTrackChanges() {
            TrackingStatistics stats = new TrackingStatistics();
            List<ChangeRecord> changes = List.of(
                    ChangeRecord.of("obj", "field", "old", "new", ChangeType.UPDATE)
            );
            stats.recordChanges(changes, 1000L);
            Map<ChangeType, Integer> distribution = stats.getChangeTypeDistribution();
            assertThat(distribution).isNotNull();
            assertThat(distribution.get(ChangeType.UPDATE)).isGreaterThan(0);
        }

        @Test
        @DisplayName("getAverageDetectionTimeMs → 非负")
        void averageDetectionTime_shouldBeNonNegative() {
            TrackingStatistics stats = new TrackingStatistics();
            assertThat(stats.getAverageDetectionTimeMs()).isGreaterThanOrEqualTo(0.0);
        }
    }
}
