package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.api.builder.TfiContext;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LcsListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.render.ChangeReportRenderer;
import com.syy.taskflowinsight.tracking.render.MarkdownRenderer;
import com.syy.taskflowinsight.tracking.render.RenderStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Maximum coverage tests for api package.
 * Covers ComparatorBuilder, ComparisonTemplate, TfiListDiffFacade, DiffBuilder, TfiContext,
 * TrackingOptions, TrackingStatistics.
 *
 * @since 3.0.0
 */
@DisplayName("API Max Coverage — API 层全覆盖测试")
class ApiMaxCoverageTests {

    // ── ComparatorBuilder ──

    @Nested
    @DisplayName("ComparatorBuilder — All Methods")
    class ComparatorBuilderDeepTests {

        @Test
        @DisplayName("ignoring — applies ignoreFields")
        void ignoring_appliesIgnoreFields() {
            ComparatorBuilder builder = ComparatorBuilder.disabled().ignoring("id", "createdAt");
            CompareResult result = builder.compare("a", "b");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("exclude — applies excludeFields")
        void exclude_appliesExcludeFields() {
            ComparatorBuilder builder = ComparatorBuilder.disabled().exclude("*.id");
            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("withMaxDepth — throws on invalid")
        void withMaxDepth_invalid_throws() {
            assertThatThrownBy(() -> ComparatorBuilder.disabled().withMaxDepth(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("withReport")
        void withReport() {
            ComparatorBuilder builder = ComparatorBuilder.disabled().withReport();
            CompareResult result = builder.compare("x", "y");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("withPatch")
        void withPatch() {
            ComparatorBuilder builder = ComparatorBuilder.disabled()
                    .withPatch(com.syy.taskflowinsight.tracking.compare.PatchFormat.JSON_PATCH);
            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("withStrategyName")
        void withStrategyName() {
            ComparatorBuilder builder = ComparatorBuilder.disabled().withStrategyName("SIMPLE");
            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("withParallelThreshold — throws on invalid")
        void withParallelThreshold_invalid_throws() {
            assertThatThrownBy(() -> ComparatorBuilder.disabled().withParallelThreshold(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("forceObjectType")
        void forceObjectType() {
            ComparatorBuilder builder = ComparatorBuilder.disabled()
                    .forceObjectType(com.syy.taskflowinsight.annotation.ObjectType.ENTITY);
            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("forceStrategy")
        void forceStrategy() {
            ComparatorBuilder builder = ComparatorBuilder.disabled()
                    .forceStrategy(com.syy.taskflowinsight.annotation.ValueObjectCompareStrategy.FIELDS);
            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("withTrackEntityKeyAttributes")
        void withTrackEntityKeyAttributes() {
            ComparatorBuilder builder = ComparatorBuilder.disabled().withTrackEntityKeyAttributes(true);
            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("withStrictDuplicateKey")
        void withStrictDuplicateKey() {
            ComparatorBuilder builder = ComparatorBuilder.disabled().withStrictDuplicateKey(true);
            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("useTemplate null — no-op")
        void useTemplate_null_noop() {
            ComparatorBuilder builder = ComparatorBuilder.disabled().useTemplate(null);
            assertThat(builder).isNotNull();
        }
    }

    // ── ComparisonTemplate ──

    @Nested
    @DisplayName("ComparisonTemplate — All Templates")
    class ComparisonTemplateDeepTests {

        @Test
        @DisplayName("FAST apply")
        void fast_apply() {
            CompareOptions.CompareOptionsBuilder b = CompareOptions.builder();
            ComparisonTemplate.FAST.apply(b);
            CompareOptions opts = b.build();
            assertThat(opts.isEnableDeepCompare()).isFalse();
        }

        @Test
        @DisplayName("DEBUG apply")
        void debug_apply() {
            CompareOptions.CompareOptionsBuilder b = CompareOptions.builder();
            ComparisonTemplate.DEBUG.apply(b);
            CompareOptions opts = b.build();
            assertThat(opts.isTypeAwareEnabled()).isTrue();
            assertThat(opts.isDetectMoves()).isTrue();
        }

        @Test
        @DisplayName("AUDIT apply")
        void audit_apply() {
            CompareOptions.CompareOptionsBuilder b = CompareOptions.builder();
            ComparisonTemplate.AUDIT.apply(b);
            CompareOptions opts = b.build();
            assertThat(opts.isGenerateReport()).isTrue();
            assertThat(opts.getMaxDepth()).isEqualTo(10);
        }
    }

    // ── TfiListDiffFacade ──

    @Nested
    @DisplayName("TfiListDiffFacade — Manual Construction")
    class TfiListDiffFacadeTests {

        private TfiListDiffFacade createFacade() {
            List<ListCompareStrategy> strategies = List.of(
                    new SimpleListStrategy(),
                    new AsSetListStrategy(),
                    new EntityListStrategy(),
                    new LevenshteinListStrategy(),
                    new LcsListStrategy()
            );
            ListCompareExecutor executor = new ListCompareExecutor(strategies);
            ChangeReportRenderer renderer = new MarkdownRenderer();
            return new TfiListDiffFacade(executor, renderer);
        }

        @Test
        @DisplayName("diff — null lists")
        void diff_nullLists() {
            TfiListDiffFacade facade = createFacade();
            CompareResult result = facade.diff(null, null);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("diff — with strategy")
        void diff_withStrategy() {
            TfiListDiffFacade facade = createFacade();
            CompareResult result = facade.diff(List.of("a", "b"), List.of("a", "c"), "SIMPLE");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("diff — with options")
        void diff_withOptions() {
            TfiListDiffFacade facade = createFacade();
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult result = facade.diff(List.of(1, 2), List.of(1, 2, 3), opts);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("diffEntities")
        void diffEntities() {
            TfiListDiffFacade facade = createFacade();
            EntityListDiffResult result = facade.diffEntities(List.of("a"), List.of("a", "b"));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("diffEntities with strategy")
        void diffEntities_withStrategy() {
            TfiListDiffFacade facade = createFacade();
            EntityListDiffResult result = facade.diffEntities(
                    List.of("x"), List.of("y"), "SIMPLE");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("diffEntities with options")
        void diffEntities_withOptions() {
            TfiListDiffFacade facade = createFacade();
            EntityListDiffResult result = facade.diffEntities(
                    List.of(1), List.of(2),
                    CompareOptions.builder().build());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("render — null result")
        void render_nullResult() {
            TfiListDiffFacade facade = createFacade();
            assertThat(facade.render(null)).isEmpty();
        }

        @Test
        @DisplayName("render — style simple")
        void render_styleSimple() {
            TfiListDiffFacade facade = createFacade();
            CompareResult cr = facade.diff(List.of("a"), List.of("b"));
            String report = facade.render(cr, "simple");
            assertThat(report).isNotBlank();
        }

        @Test
        @DisplayName("render — style detailed")
        void render_styleDetailed() {
            TfiListDiffFacade facade = createFacade();
            CompareResult cr = facade.diff(List.of("a"), List.of("b"));
            String report = facade.render(cr, "detailed");
            assertThat(report).isNotBlank();
        }

        @Test
        @DisplayName("render — RenderStyle object")
        void render_renderStyleObject() {
            TfiListDiffFacade facade = createFacade();
            CompareResult cr = facade.diff(List.of("a"), List.of("b"));
            String report = facade.render(cr, RenderStyle.standard());
            assertThat(report).isNotBlank();
        }

        @Test
        @DisplayName("render — unknown style string")
        void render_unknownStyle() {
            TfiListDiffFacade facade = createFacade();
            CompareResult cr = facade.diff(List.of("a"), List.of("b"));
            String report = facade.render(cr, "unknown");
            assertThat(report).isNotBlank();
        }
    }

    // ── DiffBuilder ──

    @Nested
    @DisplayName("DiffBuilder — All Methods")
    class DiffBuilderDeepTests {

        @Test
        @DisplayName("fromSpring — null env")
        void fromSpring_nullEnv() {
            DiffBuilder b = DiffBuilder.fromSpring(null);
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("fromSpring — StandardEnvironment")
        void fromSpring_standardEnv() {
            DiffBuilder b = DiffBuilder.fromSpring(new StandardEnvironment());
            TfiContext ctx = b.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withExcludePatterns")
        void withExcludePatterns() {
            TfiContext ctx = DiffBuilder.create()
                    .withExcludePatterns("*.id", "*.version")
                    .build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withPropertyComparator — null path no-op")
        void withPropertyComparator_nullPath() {
            DiffBuilder builder = DiffBuilder.create();
            builder.withPropertyComparator(null, (a, b, f) -> true);
            TfiContext ctx = builder.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("withPropertyComparator — null comparator no-op")
        void withPropertyComparator_nullComparator() {
            DiffBuilder builder = DiffBuilder.create();
            builder.withPropertyComparator("x", null);
            TfiContext ctx = builder.build();
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("compare with custom options")
        void compare_withCustomOptions() {
            TfiContext ctx = DiffBuilder.create().build();
            CompareOptions opts = CompareOptions.builder().maxDepth(5).build();
            CompareResult result = ctx.compare("hello", "world", opts);
            assertThat(result).isNotNull();
        }
    }

    // ── TrackingOptions ──

    @Nested
    @DisplayName("TrackingOptions — Deep")
    class TrackingOptionsDeepTests {

        @Test
        @DisplayName("builder — all options")
        void builder_allOptions() {
            TrackingOptions opts = TrackingOptions.builder()
                    .depth(TrackingOptions.TrackingDepth.DEEP)
                    .maxDepth(15)
                    .collectionStrategy(TrackingOptions.CollectionStrategy.ELEMENT)
                    .compareStrategy(TrackingOptions.CompareStrategy.EQUALS)
                    .includeFields("a", "b")
                    .excludeFields("c")
                    .enableCycleDetection(true)
                    .enablePerformanceMonitoring(true)
                    .timeBudgetMs(2000)
                    .collectionSummaryThreshold(50)
                    .enableTypeAware(true)
                    .build();
            assertThat(opts.getMaxDepth()).isEqualTo(15);
            assertThat(opts.getIncludeFields()).contains("a", "b");
            assertThat(opts.getExcludeFields()).contains("c");
        }

        @Test
        @DisplayName("shallow depth")
        void shallow_depth() {
            TrackingOptions opts = TrackingOptions.shallow();
            assertThat(opts.getDepth()).isEqualTo(TrackingOptions.TrackingDepth.SHALLOW);
        }

        @Test
        @DisplayName("deep depth")
        void deep_depth() {
            TrackingOptions opts = TrackingOptions.deep();
            assertThat(opts.getDepth()).isEqualTo(TrackingOptions.TrackingDepth.DEEP);
        }
    }

    // ── TrackingStatistics ──

    @Nested
    @DisplayName("TrackingStatistics — Deep")
    class TrackingStatisticsDeepTests {

        @Test
        @DisplayName("recordObjectTracked → summary")
        void recordObjectTracked_summary() {
            TrackingStatistics stats = new TrackingStatistics();
            stats.recordObjectTracked("Order");
            stats.recordObjectTracked("User");
            var summary = stats.getSummary();
            assertThat(summary).isNotNull();
        }

        @Test
        @DisplayName("recordChanges — MOVE type")
        void recordChanges_moveType() {
            TrackingStatistics stats = new TrackingStatistics();
            List<ChangeRecord> changes = List.of(
                    ChangeRecord.of("L", "i", "a", "a", ChangeType.MOVE)
            );
            stats.recordChanges(changes, 100L);
            Map<ChangeType, Integer> dist = stats.getChangeTypeDistribution();
            assertThat(dist).isNotNull();
        }

        @Test
        @DisplayName("recordChanges — empty list")
        void recordChanges_emptyList() {
            TrackingStatistics stats = new TrackingStatistics();
            stats.recordChanges(Collections.emptyList(), 0L);
            assertThat(stats.getAverageDetectionTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("getChangeTypeDistribution — no changes")
        void getChangeTypeDistribution_noChanges() {
            TrackingStatistics stats = new TrackingStatistics();
            Map<ChangeType, Integer> dist = stats.getChangeTypeDistribution();
            assertThat(dist).isNotNull();
            assertThat(dist.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(0);
        }
    }

    // ── ConfigPriority ──

    @Nested
    @DisplayName("ConfigurationResolver.ConfigPriority")
    class ConfigPriorityTests {

        @Test
        @DisplayName("hasHigherPriorityThan")
        void hasHigherPriorityThan() {
            assertThat(com.syy.taskflowinsight.config.resolver.ConfigurationResolver.ConfigPriority.RUNTIME_API
                    .hasHigherPriorityThan(com.syy.taskflowinsight.config.resolver.ConfigurationResolver.ConfigPriority.DEFAULT_VALUE))
                    .isTrue();
        }

        @Test
        @DisplayName("ConfigSource toString")
        void configSource_toString() {
            var config = com.syy.taskflowinsight.config.resolver.ConfigurationResolver.ConfigPriority.RUNTIME_API;
            var source = new com.syy.taskflowinsight.config.resolver.ConfigurationResolver.ConfigSource(
                    config, "key", "value", "detail");
            assertThat(source.toString()).contains("Runtime API").contains("key");
        }
    }
}
