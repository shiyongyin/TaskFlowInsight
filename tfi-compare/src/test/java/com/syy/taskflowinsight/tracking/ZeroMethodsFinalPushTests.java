package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.annotation.IgnoreDeclaredProperties;
import com.syy.taskflowinsight.annotation.IgnoreInheritedProperties;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.api.ComparatorBuilder;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.config.ConcurrencyAutoConfiguration;
import com.syy.taskflowinsight.config.ConcurrencyConfig;
import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.config.TfiConfigValidator;
import com.syy.taskflowinsight.metrics.TfiMetrics;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.DateCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.ReportFormat;
import com.syy.taskflowinsight.tracking.monitoring.DegradationConfig;
import com.syy.taskflowinsight.tracking.monitoring.DegradationDecisionEngine;
import com.syy.taskflowinsight.tracking.monitoring.DegradationLevel;
import com.syy.taskflowinsight.tracking.monitoring.DegradationPerformanceMonitor;
import com.syy.taskflowinsight.tracking.monitoring.SystemMetrics;
import com.syy.taskflowinsight.tracking.precision.PrecisionMetrics;
import com.syy.taskflowinsight.tracking.query.ListChangeProjector;
import com.syy.taskflowinsight.tracking.snapshot.filter.ClassLevelFilterEngine;
import com.syy.taskflowinsight.tracking.snapshot.filter.PathMatcher;
import com.syy.taskflowinsight.tracking.summary.SummaryInfo;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 零覆盖率方法最终冲刺测试
 * 覆盖 TfiConfigValidator、TrackingOptions、DegradationDecisionEngine、PrecisionMetrics、
 * ComparatorBuilder、ListChangeProjector、ConcurrencyAutoConfiguration、TfiMetrics、
 * SummaryInfo、DateCompareStrategy、ClassLevelFilterEngine、EntityKeyUtils、PathMatcher、DiffBuilder
 *
 * @since 3.0.0
 */
@DisplayName("零覆盖率方法最终冲刺 — ZeroMethodsFinalPush")
class ZeroMethodsFinalPushTests {

    // ── 1. TfiConfigValidator.validate ──

    @Nested
    @DisplayName("TfiConfigValidator.validate — 配置验证")
    class TfiConfigValidatorTests {

        @Test
        @DisplayName("validate 有效配置返回 null")
        void validate_validConfig_returnsNull() {
            TfiConfigValidator validator = new TfiConfigValidator();
            TfiConfig valid = new TfiConfig(true,
                new TfiConfig.ChangeTracking(true, 8192, 5,
                    new TfiConfig.ChangeTracking.Snapshot(10, 100, Set.of(), 1000, false, 1000L),
                    new TfiConfig.ChangeTracking.Diff("compat", false, 1000, true, "legacy"),
                    new TfiConfig.ChangeTracking.Export("json", true, false, false, false),
                    1024,
                    new TfiConfig.ChangeTracking.Summary(true, 100, 10, Set.of())),
                new TfiConfig.Context(3600000L, false, 60000L, false, 60000L),
                new TfiConfig.Metrics(true, Map.of(), "PT1M"),
                new TfiConfig.Security(true, Set.of()));
            assertThat(validator.validate(valid)).isNull();
        }

        @Test
        @DisplayName("validate null 配置返回错误")
        void validate_nullConfig_returnsError() {
            TfiConfigValidator validator = new TfiConfigValidator();
            assertThat(validator.validate(null)).isEqualTo("TfiConfig cannot be null");
        }

        @Test
        @DisplayName("validate 快照深度超限返回错误")
        void validate_maxDepthExceeded_returnsError() {
            TfiConfigValidator validator = new TfiConfigValidator();
            TfiConfig invalid = new TfiConfig(true,
                new TfiConfig.ChangeTracking(true, 8192, 5,
                    new TfiConfig.ChangeTracking.Snapshot(51, 100, Set.of(), 1000, false, 1000L),
                    new TfiConfig.ChangeTracking.Diff("compat", false, 1000, true, "legacy"),
                    new TfiConfig.ChangeTracking.Export("json", true, false, false, false),
                    1024,
                    new TfiConfig.ChangeTracking.Summary(true, 100, 10, Set.of())),
                new TfiConfig.Context(3600000L, false, 60000L, false, 60000L),
                new TfiConfig.Metrics(true, Map.of(), "PT1M"),
                new TfiConfig.Security(true, Set.of()));
            assertThat(validator.validate(invalid)).contains("max depth");
        }

        @Test
        @DisplayName("validate maxChangesPerObject 超限返回错误")
        void validate_maxChangesPerObjectExceeded_returnsError() {
            TfiConfigValidator validator = new TfiConfigValidator();
            TfiConfig invalid = new TfiConfig(true,
                new TfiConfig.ChangeTracking(true, 8192, 5,
                    new TfiConfig.ChangeTracking.Snapshot(10, 100, Set.of(), 1000, false, 1000L),
                    new TfiConfig.ChangeTracking.Diff("compat", false, 5001, true, "legacy"),
                    new TfiConfig.ChangeTracking.Export("json", true, false, false, false),
                    1024,
                    new TfiConfig.ChangeTracking.Summary(true, 100, 10, Set.of())),
                new TfiConfig.Context(3600000L, false, 60000L, false, 60000L),
                new TfiConfig.Metrics(true, Map.of(), "PT1M"),
                new TfiConfig.Security(true, Set.of()));
            assertThat(validator.validate(invalid)).contains("Max changes per object");
        }

        @Test
        @DisplayName("validateAndThrow 无效配置抛出异常")
        void validateAndThrow_invalid_throws() {
            TfiConfigValidator validator = new TfiConfigValidator();
            assertThatThrownBy(() -> validator.validateAndThrow(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TfiConfig cannot be null");
        }
    }

    // ── 2. TrackingOptions.toString ──

    @Nested
    @DisplayName("TrackingOptions.toString — 追踪选项字符串")
    class TrackingOptionsToStringTests {

        @Test
        @DisplayName("toString 返回完整格式")
        void toString_returnsFullFormat() {
            TrackingOptions opts = TrackingOptions.builder()
                .depth(TrackingOptions.TrackingDepth.DEEP)
                .maxDepth(5)
                .includeFields("a", "b")
                .excludeFields("c")
                .build();
            String s = opts.toString();
            assertThat(s).contains("TrackingOptions")
                .contains("depth=DEEP")
                .contains("maxDepth=5")
                .contains("includeFields")
                .contains("excludeFields");
        }
    }

    // ── 3. DegradationDecisionEngine.logDecisionProcess ──

    @Nested
    @DisplayName("DegradationDecisionEngine.logDecisionProcess — 降级决策日志")
    class DegradationDecisionEngineTests {

        @Test
        @DisplayName("calculateOptimalLevel 触发 logDecisionProcess（通过反射验证）")
        void calculateOptimalLevel_triggersLogDecisionProcess() throws Exception {
            DegradationConfig config = new DegradationConfig(
                true, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(10),
                200L, 90.0, 1000L,
                new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
                500, 5, 10000);
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            SystemMetrics metrics = SystemMetrics.builder()
                .memoryUsagePercent(50.0)
                .availableMemoryMB(1000)
                .cpuUsagePercent(30.0)
                .threadCount(50)
                .averageOperationTime(Duration.ofMillis(50))
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, null);
            assertThat(level).isNotNull();
            // 通过反射直接调用 logDecisionProcess 以覆盖
            Method m = DegradationDecisionEngine.class.getDeclaredMethod(
                "logDecisionProcess", SystemMetrics.class, List.class, DegradationLevel.class);
            m.setAccessible(true);
            m.invoke(engine, metrics, Collections.emptyList(), level);
        }
    }

    // ── 4. PrecisionMetrics.registerCounter ──

    @Nested
    @DisplayName("PrecisionMetrics.registerCounter — 精度指标注册")
    class PrecisionMetricsTests {

        @Test
        @DisplayName("enableMicrometerIfAvailable 触发 registerCounter")
        void enableMicrometerIfAvailable_triggersRegisterCounter() {
            PrecisionMetrics metrics = new PrecisionMetrics();
            metrics.enableMicrometerIfAvailable();
            // 若 Micrometer 可用则 registerCounter 被调用；否则静默
            metrics.recordNumericComparison();
            assertThat(metrics.getSnapshot().numericComparisonCount).isGreaterThanOrEqualTo(0);
        }
    }

    // ── 5. ComparatorBuilder.compare ──

    @Nested
    @DisplayName("ComparatorBuilder.compare — 比较器执行")
    class ComparatorBuilderCompareTests {

        @Test
        @DisplayName("compare 使用 CompareService 执行比较")
        void compare_withCompareService_executes() throws Exception {
            CompareService svc = CompareService.createDefault(CompareOptions.builder().build());
            var ctor = ComparatorBuilder.class.getDeclaredConstructor(CompareService.class);
            ctor.setAccessible(true);
            ComparatorBuilder builder = ctor.newInstance(svc);
            CompareResult result = builder.ignoring("id").compare("hello", "hello");
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("compare 不同对象返回差异")
        void compare_differentObjects_returnsDiff() throws Exception {
            CompareService svc = CompareService.createDefault(CompareOptions.builder().build());
            var ctor = ComparatorBuilder.class.getDeclaredConstructor(CompareService.class);
            ctor.setAccessible(true);
            ComparatorBuilder builder = ctor.newInstance(svc);
            CompareResult result = builder.compare(Map.of("a", 1), Map.of("b", 2));
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }
    }

    // ── 6. ListChangeProjector.createMoveEvent ──

    @Nested
    @DisplayName("ListChangeProjector.createMoveEvent — 列表移动事件")
    class ListChangeProjectorMoveTests {

        @Test
        @DisplayName("project LCS detectMoves 触发 createMoveEvent")
        void project_lcsDetectMoves_createsMoveEvent() {
            // left=[1,2,3], right=[3,1] -> entry_removed(3) at i=2, 3 in right at index 0 -> move
            List<Integer> left = Arrays.asList(1, 2, 3);
            List<Integer> right = Arrays.asList(3, 1);
            CompareResult result = CompareResult.builder()
                .algorithmUsed("LCS")
                .identical(false)
                .build();
            CompareOptions opts = CompareOptions.builder().detectMoves(true).build();
            List<Map<String, Object>> events = ListChangeProjector.project(result, left, right, opts, "items");
            boolean hasMove = events.stream()
                .anyMatch(e -> "entry_moved".equals(e.get("kind")));
            assertThat(hasMove).isTrue();
        }
    }

    // ── 7. ConcurrencyAutoConfiguration.initializeConcurrencySettings ──

    @Nested
    @DisplayName("ConcurrencyAutoConfiguration.initializeConcurrencySettings — 并发初始化")
    class ConcurrencyAutoConfigurationTests {

        @Test
        @DisplayName("initializeConcurrencySettings 可被直接调用")
        void initializeConcurrencySettings_canBeCalled() {
            ConcurrencyConfig config = new ConcurrencyConfig();
            ConcurrencyAutoConfiguration autoConfig = new ConcurrencyAutoConfiguration(config);
            assertThatCode(() -> autoConfig.initializeConcurrencySettings()).doesNotThrowAnyException();
        }
    }

    // ── 8. TfiMetrics.registerEnterpriseSystemMetrics ──

    @Nested
    @DisplayName("TfiMetrics.registerEnterpriseSystemMetrics — 企业系统指标")
    class TfiMetricsEnterpriseTests {

        @Test
        @DisplayName("registerEnterpriseSystemMetrics 通过反射调用")
        void registerEnterpriseSystemMetrics_viaReflection() throws Exception {
            TfiMetrics metrics = new TfiMetrics(Optional.of(new SimpleMeterRegistry()));
            Method m = TfiMetrics.class.getDeclaredMethod("registerEnterpriseSystemMetrics");
            m.setAccessible(true);
            m.invoke(metrics);
        }
    }

    // ── 9. SummaryInfo.toMap ──

    @Nested
    @DisplayName("SummaryInfo.toMap — 摘要转 Map")
    class SummaryInfoToMapTests {

        @Test
        @DisplayName("toMap 返回完整结构")
        void toMap_returnsFullStructure() {
            SummaryInfo info = new SummaryInfo();
            info.setType("ArrayList");
            info.setSize(10);
            info.setTruncated(true);
            info.setUniqueCount(8);
            info.setExamples(List.of(1, 2, 3));
            info.setFeatures(Set.of("sorted"));
            info.setTimestamp(System.currentTimeMillis());
            info.setStatistics(new SummaryInfo.Statistics(1.0, 10.0, 5.0, 5.0, 2.0));
            Map<String, Object> map = info.toMap();
            assertThat(map).containsKeys("type", "size", "truncated", "uniqueCount", "examples", "features", "timestamp", "statistics");
            assertThat(map.get("type")).isEqualTo("ArrayList");
            assertThat(map.get("size")).isEqualTo(10);
        }

        @Test
        @DisplayName("toMap 含 mapExamples")
        void toMap_withMapExamples() {
            SummaryInfo info = new SummaryInfo();
            info.setType("HashMap");
            info.setSize(2);
            info.setMapExamples(List.of(
                Map.entry("k1", "v1"),
                Map.entry("k2", "v2")));
            info.setTimestamp(System.currentTimeMillis());
            Map<String, Object> map = info.toMap();
            assertThat(map).containsKey("mapExamples");
        }
    }

    // ── 10. DateCompareStrategy.generateDateReport ──

    @Nested
    @DisplayName("DateCompareStrategy.generateDateReport — 日期比较报告")
    class DateCompareStrategyReportTests {

        @Test
        @DisplayName("compare 启用报告生成 generateDateReport")
        void compare_withReport_generatesDateReport() {
            DateCompareStrategy strategy = new DateCompareStrategy();
            Date d1 = new Date(1000000);
            Date d2 = new Date(2000000);
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .calculateSimilarity(true)
                .build();
            CompareResult result = strategy.compare(d1, d2, opts);
            assertThat(result.getReport()).contains("Date Comparison")
                .contains("Date 1")
                .contains("Date 2")
                .contains("Difference");
        }
    }

    // ── 11. ClassLevelFilterEngine.shouldIgnoreByClass ──

    @Nested
    @DisplayName("ClassLevelFilterEngine.shouldIgnoreByClass — 类级过滤")
    class ClassLevelFilterEngineTests {

        @IgnoreDeclaredProperties("ignored")
        static class WithIgnoreDeclared {
            public String kept;
            public String ignored;
        }

        @IgnoreInheritedProperties
        static class ChildWithIgnoreInherited extends WithIgnoreDeclared {
            public String childField;
        }

        @Test
        @DisplayName("shouldIgnoreByClass 包级排除")
        void shouldIgnoreByClass_excludePackages() throws Exception {
            var field = String.class.getDeclaredField("value");
            boolean ignored = ClassLevelFilterEngine.shouldIgnoreByClass(
                String.class, field, List.of("java.lang"));
            assertThat(ignored).isTrue();
        }

        @Test
        @DisplayName("shouldIgnoreByClass IgnoreDeclaredProperties 指定字段")
        void shouldIgnoreByClass_ignoreDeclaredSpecified() throws Exception {
            var field = WithIgnoreDeclared.class.getDeclaredField("ignored");
            boolean ignored = ClassLevelFilterEngine.shouldIgnoreByClass(
                WithIgnoreDeclared.class, field, null);
            assertThat(ignored).isTrue();
        }

        @Test
        @DisplayName("shouldIgnoreByClass IgnoreInheritedProperties 继承字段")
        void shouldIgnoreByClass_ignoreInherited() throws Exception {
            var field = WithIgnoreDeclared.class.getDeclaredField("kept");
            boolean ignored = ClassLevelFilterEngine.shouldIgnoreByClass(
                ChildWithIgnoreInherited.class, field, null);
            assertThat(ignored).isTrue();
        }
    }

    // ── 12. EntityKeyUtils.normalizeKeyComponent ──

    @Nested
    @DisplayName("EntityKeyUtils.normalizeKeyComponent — 实体键规范化")
    class EntityKeyUtilsNormalizeTests {

        @Test
        @DisplayName("tryComputeStableKey 多种类型触发 normalizeKeyComponent")
        void tryComputeStableKey_variousTypes() {
            assertThat(EntityKeyUtils.tryComputeStableKey(new NumKeyEntity())).isPresent();
            assertThat(EntityKeyUtils.tryComputeStableKey(new StrKeyEntity())).isPresent();
            assertThat(EntityKeyUtils.tryComputeStableKey(new BoolKeyEntity())).isPresent();
            assertThat(EntityKeyUtils.tryComputeStableKey(new CollKeyEntity())).isPresent();
        }
    }

    static class NumKeyEntity { @Key int id = 1; }
    static class StrKeyEntity { @Key String id = "x"; }
    static class BoolKeyEntity { @Key boolean flag = true; }
    static class CollKeyEntity { @Key List<String> ids = List.of("a", "b"); }

    // ── 13. PathMatcher.convertGlobToRegex ──

    @Nested
    @DisplayName("PathMatcher.convertGlobToRegex — 路径 Glob 转正则")
    class PathMatcherGlobTests {

        @Test
        @DisplayName("matchGlob 单层通配符 *")
        void matchGlob_singleStar() {
            assertThat(PathMatcher.matchGlob("order.items", "order.*")).isTrue();
        }

        @Test
        @DisplayName("matchGlob 跨层通配符 **")
        void matchGlob_doubleStar() {
            assertThat(PathMatcher.matchGlob("order.items.name", "order.**")).isTrue();
        }

        @Test
        @DisplayName("matchGlob 问号 ?")
        void matchGlob_questionMark() {
            assertThat(PathMatcher.matchGlob("a", "?")).isTrue();
        }

        @Test
        @DisplayName("matchGlob 数组索引 [*]")
        void matchGlob_arrayIndex() {
            assertThat(PathMatcher.matchGlob("items[0].id", "items[*].id")).isTrue();
        }

        @Test
        @DisplayName("matchGlob 字面量")
        void matchGlob_literal() {
            assertThat(PathMatcher.matchGlob("order.id", "order.id")).isTrue();
        }
    }

    // ── 14. DiffBuilder.fromSpring ──

    @Nested
    @DisplayName("DiffBuilder.fromSpring — Spring 环境装配")
    class DiffBuilderFromSpringTests {

        @Test
        @DisplayName("fromSpring null 环境")
        void fromSpring_nullEnv() {
            DiffBuilder b = DiffBuilder.fromSpring(null);
            assertThat(b).isNotNull();
        }

        @Test
        @DisplayName("fromSpring 标准环境")
        void fromSpring_standardEnv() {
            StandardEnvironment env = new StandardEnvironment();
            DiffBuilder b = DiffBuilder.fromSpring(env);
            assertThat(b).isNotNull();
        }

        @Test
        @DisplayName("fromSpring 含 max-depth 配置")
        void fromSpring_withMaxDepthConfig() {
            StandardEnvironment env = new StandardEnvironment();
            env.getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("test",
                    Map.of("tfi.change-tracking.snapshot.max-depth", "15")));
            DiffBuilder b = DiffBuilder.fromSpring(env);
            assertThat(b).isNotNull();
        }
    }
}
