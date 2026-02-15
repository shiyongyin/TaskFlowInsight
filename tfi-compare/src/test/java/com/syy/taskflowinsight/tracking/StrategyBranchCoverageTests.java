package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.metrics.TfiMetrics;
import com.syy.taskflowinsight.tracking.compare.EnhancedDateCompareStrategy;
import com.syy.taskflowinsight.tracking.path.PathArbiter;
import com.syy.taskflowinsight.tracking.path.PathDeduplicationConfig;
import com.syy.taskflowinsight.tracking.monitoring.DegradationConfig;
import com.syy.taskflowinsight.tracking.monitoring.DegradationDecisionEngine;
import com.syy.taskflowinsight.tracking.monitoring.DegradationLevel;
import com.syy.taskflowinsight.tracking.monitoring.DegradationManager;
import com.syy.taskflowinsight.tracking.monitoring.DegradationPerformanceMonitor;
import com.syy.taskflowinsight.tracking.monitoring.ResourceMonitor;
import com.syy.taskflowinsight.tracking.monitoring.SystemMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 策略分支覆盖测试
 * 覆盖 EnhancedDateCompareStrategy、DegradationManager、PathArbiter 的分支逻辑
 *
 * @since 3.0.0
 */
@DisplayName("Strategy Branch Coverage — 策略分支覆盖测试")
class StrategyBranchCoverageTests {

    @AfterEach
    void tearDown() {
        com.syy.taskflowinsight.tracking.monitoring.DegradationContext.clear();
    }

    // ═══════════════════════════════════════════════════════════════════
    // EnhancedDateCompareStrategy
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EnhancedDateCompareStrategy — Date 比较")
    class EnhancedDateCompareStrategyDate {

        private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();

        @Test
        @DisplayName("compareDates 两者 null 返回 true")
        void compareDates_bothNull() {
            assertThat(strategy.compareDates(null, null)).isTrue();
        }

        @Test
        @DisplayName("compareDates 一方 null 返回 false")
        void compareDates_oneNull() {
            assertThat(strategy.compareDates(new Date(), null)).isFalse();
            assertThat(strategy.compareDates(null, new Date())).isFalse();
        }

        @Test
        @DisplayName("compareDates 相等")
        void compareDates_equal() {
            Date d = new Date();
            assertThat(strategy.compareDates(d, new Date(d.getTime()))).isTrue();
        }

        @Test
        @DisplayName("compareDates 容差内")
        void compareDates_withinTolerance() {
            Date a = new Date(1000);
            Date b = new Date(1050);
            assertThat(strategy.compareDates(a, b, 100)).isTrue();
        }

        @Test
        @DisplayName("compareDates 超出容差")
        void compareDates_outsideTolerance() {
            Date a = new Date(1000);
            Date b = new Date(2000);
            assertThat(strategy.compareDates(a, b, 100)).isFalse();
        }

        @Test
        @DisplayName("compareDates 默认容差 0")
        void compareDates_defaultTolerance() {
            Date a = new Date(1000);
            Date b = new Date(1001);
            assertThat(strategy.compareDates(a, b)).isFalse();
        }
    }

    @Nested
    @DisplayName("EnhancedDateCompareStrategy — Instant 比较")
    class EnhancedDateCompareStrategyInstant {

        private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();

        @Test
        @DisplayName("compareInstants 两者 null")
        void compareInstants_bothNull() {
            assertThat(strategy.compareInstants(null, null, 0)).isTrue();
        }

        @Test
        @DisplayName("compareInstants 一方 null")
        void compareInstants_oneNull() {
            assertThat(strategy.compareInstants(Instant.now(), null, 0)).isFalse();
        }

        @Test
        @DisplayName("compareInstants 容差内")
        void compareInstants_withinTolerance() {
            Instant a = Instant.now();
            Instant b = a.plusMillis(50);
            assertThat(strategy.compareInstants(a, b, 100)).isTrue();
        }
    }

    @Nested
    @DisplayName("EnhancedDateCompareStrategy — LocalDateTime 比较")
    class EnhancedDateCompareStrategyLocalDateTime {

        private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();

        @Test
        @DisplayName("compareLocalDateTimes 容差 0 精确比较")
        void compareLocalDateTimes_zeroTolerance() {
            LocalDateTime t = LocalDateTime.now();
            assertThat(strategy.compareLocalDateTimes(t, t, 0)).isTrue();
        }

        @Test
        @DisplayName("compareLocalDateTimes 容差内")
        void compareLocalDateTimes_withinTolerance() {
            LocalDateTime a = LocalDateTime.now();
            LocalDateTime b = a.plus(Duration.ofMillis(50));
            assertThat(strategy.compareLocalDateTimes(a, b, 100)).isTrue();
        }
    }

    @Nested
    @DisplayName("EnhancedDateCompareStrategy — LocalDate 比较")
    class EnhancedDateCompareStrategyLocalDate {

        private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();

        @Test
        @DisplayName("compareLocalDates 容差 0")
        void compareLocalDates_zeroTolerance() {
            LocalDate d = LocalDate.now();
            assertThat(strategy.compareLocalDates(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("compareLocalDates 容差内")
        void compareLocalDates_withinTolerance() {
            LocalDate a = LocalDate.of(2025, 1, 1);
            LocalDate b = LocalDate.of(2025, 1, 2);
            assertThat(strategy.compareLocalDates(a, b, 86400_000)).isTrue();
        }
    }

    @Nested
    @DisplayName("EnhancedDateCompareStrategy — Duration/Period")
    class EnhancedDateCompareStrategyDurationPeriod {

        private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();

        @Test
        @DisplayName("compareDurations 相等")
        void compareDurations_equal() {
            Duration d = Duration.ofSeconds(10);
            assertThat(strategy.compareDurations(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("compareDurations 不同")
        void compareDurations_different() {
            Duration a = Duration.ofSeconds(10);
            Duration b = Duration.ofSeconds(20);
            assertThat(strategy.compareDurations(a, b, 0)).isFalse();
        }

        @Test
        @DisplayName("comparePeriods 相等")
        void comparePeriods_equal() {
            Period p = Period.ofDays(1);
            assertThat(strategy.comparePeriods(p, p)).isTrue();
        }

        @Test
        @DisplayName("comparePeriods 不同")
        void comparePeriods_different() {
            Period a = Period.ofDays(1);
            Period b = Period.ofDays(2);
            assertThat(strategy.comparePeriods(a, b)).isFalse();
        }
    }

    @Nested
    @DisplayName("EnhancedDateCompareStrategy — compareTemporal 分发")
    class EnhancedDateCompareStrategyCompareTemporal {

        private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();

        @Test
        @DisplayName("compareTemporal Date 类型")
        void compareTemporal_date() {
            Date d = new Date();
            assertThat(strategy.compareTemporal(d, new Date(d.getTime()), 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal Instant 类型")
        void compareTemporal_instant() {
            Instant i = Instant.now();
            assertThat(strategy.compareTemporal(i, i, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal LocalDateTime 类型")
        void compareTemporal_localDateTime() {
            LocalDateTime t = LocalDateTime.now();
            assertThat(strategy.compareTemporal(t, t, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal LocalDate 类型")
        void compareTemporal_localDate() {
            LocalDate d = LocalDate.now();
            assertThat(strategy.compareTemporal(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal Duration 类型")
        void compareTemporal_duration() {
            Duration d = Duration.ofSeconds(1);
            assertThat(strategy.compareTemporal(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal Period 类型")
        void compareTemporal_period() {
            Period p = Period.ofDays(1);
            assertThat(strategy.compareTemporal(p, p, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal 不支持类型回退 equals")
        void compareTemporal_unsupportedType() {
            String s = "hello";
            assertThat(strategy.compareTemporal(s, s, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal 类型不同返回 false")
        void compareTemporal_differentTypes() {
            assertThat(strategy.compareTemporal(new Date(), Instant.now(), 0)).isFalse();
        }
    }

    @Nested
    @DisplayName("EnhancedDateCompareStrategy — isTemporalType/needsTemporalCompare")
    class EnhancedDateCompareStrategyStatic {

        @Test
        @DisplayName("isTemporalType null 返回 false")
        void isTemporalType_null() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(null)).isFalse();
        }

        @Test
        @DisplayName("isTemporalType Date")
        void isTemporalType_date() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(new Date())).isTrue();
        }

        @Test
        @DisplayName("isTemporalType Instant")
        void isTemporalType_instant() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(Instant.now())).isTrue();
        }

        @Test
        @DisplayName("isTemporalType LocalDateTime")
        void isTemporalType_localDateTime() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(LocalDateTime.now())).isTrue();
        }

        @Test
        @DisplayName("isTemporalType LocalDate")
        void isTemporalType_localDate() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(LocalDate.now())).isTrue();
        }

        @Test
        @DisplayName("isTemporalType LocalTime")
        void isTemporalType_localTime() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(
                java.time.LocalTime.now())).isTrue();
        }

        @Test
        @DisplayName("isTemporalType Duration")
        void isTemporalType_duration() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(Duration.ZERO)).isTrue();
        }

        @Test
        @DisplayName("isTemporalType Period")
        void isTemporalType_period() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(Period.ZERO)).isTrue();
        }

        @Test
        @DisplayName("needsTemporalCompare 两者都是时间类型")
        void needsTemporalCompare_bothTemporal() {
            assertThat(EnhancedDateCompareStrategy.needsTemporalCompare(
                new Date(), Instant.now())).isTrue();
        }

        @Test
        @DisplayName("needsTemporalCompare 一方非时间类型")
        void needsTemporalCompare_oneNonTemporal() {
            assertThat(EnhancedDateCompareStrategy.needsTemporalCompare(
                new Date(), "string")).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DegradationManager
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DegradationManager — 公共 API")
    class DegradationManagerApi {

        private DegradationManager manager;

        @BeforeEach
        void setUp() {
            DegradationConfig config = new DegradationConfig(
                true, java.time.Duration.ofSeconds(5), java.time.Duration.ofMillis(100),
                java.time.Duration.ofSeconds(10), 200L, 90.0, 1000L,
                new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
                500, 5, 10000);
            TfiMetrics tfiMetrics = new TfiMetrics(Optional.of(new SimpleMeterRegistry()));
            manager = new DegradationManager(
                new DegradationPerformanceMonitor(),
                new ResourceMonitor(),
                tfiMetrics,
                config,
                new DegradationDecisionEngine(config),
                mock(ApplicationEventPublisher.class));
        }

        @Test
        @DisplayName("getCurrentLevel 初始为 FULL_TRACKING")
        void getCurrentLevel() {
            assertThat(manager.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
        }

        @Test
        @DisplayName("forceLevel 强制变更级别")
        void forceLevel() {
            manager.forceLevel(DegradationLevel.SUMMARY_ONLY, "test");
            assertThat(manager.getCurrentLevel()).isEqualTo(DegradationLevel.SUMMARY_ONLY);
        }

        @Test
        @DisplayName("getLastMetrics 初始为 null")
        void getLastMetrics_initialNull() {
            assertThat(manager.getLastMetrics()).isNull();
        }

        @Test
        @DisplayName("evaluateAndAdjust 收集指标")
        void evaluateAndAdjust() {
            manager.evaluateAndAdjust();
            assertThat(manager.getLastMetrics()).isNotNull();
        }

        @Test
        @DisplayName("isSystemHealthy 无指标时返回 true")
        void isSystemHealthy_noMetrics() {
            assertThat(manager.isSystemHealthy()).isTrue();
        }

        @Test
        @DisplayName("getStatusSummary")
        void getStatusSummary() {
            manager.evaluateAndAdjust();
            String summary = manager.getStatusSummary();
            assertThat(summary).contains("DegradationManager").contains("level=");
        }
    }

    @Nested
    @DisplayName("DegradationManager — 级别变更与恢复")
    class DegradationManagerLevelTransitions {

        private DegradationManager manager;

        @BeforeEach
        void setUp() {
            DegradationConfig config = new DegradationConfig(
                true, java.time.Duration.ofSeconds(5), java.time.Duration.ofMillis(100),
                java.time.Duration.ofSeconds(10), 200L, 90.0, 1000L,
                new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
                500, 5, 10000);
            TfiMetrics tfiMetrics = new TfiMetrics(Optional.of(new SimpleMeterRegistry()));
            manager = new DegradationManager(
                new DegradationPerformanceMonitor(),
                new ResourceMonitor(),
                tfiMetrics,
                config,
                new DegradationDecisionEngine(config),
                mock(ApplicationEventPublisher.class));
        }

        @Test
        @DisplayName("evaluateAndAdjust 级别相同时不变更")
        void evaluateAndAdjust_sameLevel() {
            manager.evaluateAndAdjust();
            DegradationLevel level = manager.getCurrentLevel();
            manager.evaluateAndAdjust();
            assertThat(manager.getCurrentLevel()).isEqualTo(level);
        }

        @Test
        @DisplayName("forceLevel 记录事件")
        void forceLevel_recordsEvent() {
            manager.forceLevel(DegradationLevel.DISABLED, "manual");
            assertThat(manager.getCurrentLevel()).isEqualTo(DegradationLevel.DISABLED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PathArbiter
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PathArbiter — selectMostSpecificConfigurable")
    class PathArbiterSelectMostSpecific {

        @Test
        @DisplayName("单候选直接返回")
        void singleCandidate_returnsDirectly() {
            PathArbiter arbiter = new PathArbiter();
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("a.b", 1, t);
            PathArbiter.PathCandidate result = arbiter.selectMostSpecificConfigurable(List.of(c));
            assertThat(result).isSameAs(c);
        }

        @Test
        @DisplayName("空候选抛出异常")
        void emptyCandidates_throws() {
            PathArbiter arbiter = new PathArbiter();
            assertThatThrownBy(() -> arbiter.selectMostSpecificConfigurable(Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");
        }

        @Test
        @DisplayName("null 候选抛出异常")
        void nullCandidates_throws() {
            PathArbiter arbiter = new PathArbiter();
            assertThatThrownBy(() -> arbiter.selectMostSpecificConfigurable(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("多候选按优先级选择")
        void multipleCandidates_selectsByPriority() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setCacheEnabled(true);
            PathArbiter arbiter = new PathArbiter(config);
            Object t = new Object();
            PathArbiter.PathCandidate deep = new PathArbiter.PathCandidate("a.b.c", 2,
                PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate shallow = new PathArbiter.PathCandidate("a", 0,
                PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate result = arbiter.selectMostSpecificConfigurable(
                List.of(deep, shallow));
            assertThat(result.getPath()).isEqualTo("a.b.c");
        }

        @Test
        @DisplayName("缓存命中返回匹配候选")
        void cacheHit_returnsMatchingCandidate() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setCacheEnabled(true);
            PathArbiter arbiter = new PathArbiter(config);
            Object t = new Object();
            PathArbiter.PathCandidate c1 = new PathArbiter.PathCandidate("x", 0, t);
            PathArbiter.PathCandidate c2 = new PathArbiter.PathCandidate("y", 0, t);
            arbiter.selectMostSpecificConfigurable(List.of(c1, c2));
            PathArbiter.PathCandidate result = arbiter.selectMostSpecificConfigurable(
                List.of(c1, c2));
            assertThat(result).isIn(c1, c2);
        }
    }

    @Nested
    @DisplayName("PathArbiter — deduplicateConfigurable")
    class PathArbiterDeduplicate {

        @Test
        @DisplayName("deduplicateConfigurable null 返回空列表")
        void deduplicateConfigurable_null() {
            PathArbiter arbiter = new PathArbiter();
            List<PathArbiter.PathCandidate> result = arbiter.deduplicateConfigurable(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateConfigurable 空列表")
        void deduplicateConfigurable_empty() {
            PathArbiter arbiter = new PathArbiter();
            List<PathArbiter.PathCandidate> result = arbiter.deduplicateConfigurable(
                Collections.emptyList());
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("PathArbiter — 静态方法")
    class PathArbiterStatic {

        @Test
        @DisplayName("selectMostSpecific 单候选")
        void selectMostSpecific_single() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("p", 0, t);
            PathArbiter.PathCandidate result = PathArbiter.selectMostSpecific(List.of(c));
            assertThat(result).isSameAs(c);
        }

        @Test
        @DisplayName("selectMostSpecific 空列表抛出")
        void selectMostSpecific_empty_throws() {
            assertThatThrownBy(() -> PathArbiter.selectMostSpecific(Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("deduplicate null 返回空")
        void deduplicate_null() {
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicate(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateMostSpecific 祖先路径移除")
        void deduplicateMostSpecific_removesAncestor() {
            Object t = new Object();
            PathArbiter.PathCandidate ancestor = new PathArbiter.PathCandidate("a.b", 1, t);
            PathArbiter.PathCandidate descendant = new PathArbiter.PathCandidate("a.b.c", 2, t);
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(
                List.of(ancestor, descendant));
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPath()).isEqualTo("a.b.c");
        }

        @Test
        @DisplayName("deduplicateMostSpecific 后代路径移除祖先")
        void deduplicateMostSpecific_descendantRemovesAncestor() {
            Object t = new Object();
            PathArbiter.PathCandidate a = new PathArbiter.PathCandidate("x", 0, t);
            PathArbiter.PathCandidate b = new PathArbiter.PathCandidate("x.y", 1, t);
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(
                List.of(a, b));
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPath()).isEqualTo("x.y");
        }

        @Test
        @DisplayName("deduplicateMostSpecific null 或空路径跳过")
        void deduplicateMostSpecific_skipsEmptyPath() {
            Object t = new Object();
            PathArbiter.PathCandidate empty = new PathArbiter.PathCandidate("", 0, t);
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(
                List.of(empty));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("verifyStability 空列表返回 true")
        void verifyStability_empty() {
            assertThat(PathArbiter.verifyStability(Collections.emptyList(), 5)).isTrue();
        }

        @Test
        @DisplayName("verifyStability iterations < 1 返回 true")
        void verifyStability_zeroIterations() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, t);
            assertThat(PathArbiter.verifyStability(List.of(c), 0)).isTrue();
        }

        @Test
        @DisplayName("verifyStability 多次裁决一致")
        void verifyStability_consistent() {
            Object t = new Object();
            PathArbiter.PathCandidate c1 = new PathArbiter.PathCandidate("a", 0, t);
            PathArbiter.PathCandidate c2 = new PathArbiter.PathCandidate("b", 0, t);
            assertThat(PathArbiter.verifyStability(List.of(c1, c2), 3)).isTrue();
        }

        @Test
        @DisplayName("selectMostSpecificAdvanced 委托基础实现")
        void selectMostSpecificAdvanced() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("p", 0, t);
            PathArbiter.PathCandidate result = PathArbiter.selectMostSpecificAdvanced(
                List.of(c), "custom");
            assertThat(result).isSameAs(c);
        }
    }

    @Nested
    @DisplayName("PathArbiter — AccessType.fromPath")
    class PathArbiterAccessType {

        @Test
        @DisplayName("AccessType fromPath 字段")
        void accessType_field() {
            assertThat(PathArbiter.AccessType.fromPath("fieldName"))
                .isEqualTo(PathArbiter.AccessType.FIELD);
        }

        @Test
        @DisplayName("AccessType fromPath Map 键双引号")
        void accessType_mapKeyQuoted() {
            assertThat(PathArbiter.AccessType.fromPath("map[\"key\"]"))
                .isEqualTo(PathArbiter.AccessType.MAP_KEY);
        }

        @Test
        @DisplayName("AccessType fromPath 数组索引")
        void accessType_arrayIndex() {
            assertThat(PathArbiter.AccessType.fromPath("list[0]"))
                .isEqualTo(PathArbiter.AccessType.ARRAY_INDEX);
        }

        @Test
        @DisplayName("AccessType fromPath Set 元素 id=")
        void accessType_setElement() {
            assertThat(PathArbiter.AccessType.fromPath("set[id=123]"))
                .isEqualTo(PathArbiter.AccessType.SET_ELEMENT);
        }

        @Test
        @DisplayName("AccessType fromPath null 返回 FIELD")
        void accessType_nullPath() {
            assertThat(PathArbiter.AccessType.fromPath(null))
                .isEqualTo(PathArbiter.AccessType.FIELD);
        }

        @Test
        @DisplayName("AccessType fromPath 嵌套路径取最后段")
        void accessType_nestedPath() {
            assertThat(PathArbiter.AccessType.fromPath("parent.child[0]"))
                .isEqualTo(PathArbiter.AccessType.ARRAY_INDEX);
        }
    }

    @Nested
    @DisplayName("PathArbiter — PathCandidate 与 getPathCache")
    class PathArbiterPathCandidate {

        @Test
        @DisplayName("PathCandidate 便捷构造器")
        void pathCandidate_convenienceConstructor() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("path", 1, t);
            assertThat(c.getPath()).isEqualTo("path");
            assertThat(c.getDepth()).isEqualTo(1);
            assertThat(c.getTarget()).isSameAs(t);
        }

        @Test
        @DisplayName("PathCandidate getTargetId null target")
        void pathCandidate_getTargetId_nullTarget() {
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("p", 0, null);
            assertThat(c.getTargetId()).isEqualTo("null-target");
        }

        @Test
        @DisplayName("getPathCache 返回缓存实例")
        void getPathCache() {
            PathArbiter arbiter = new PathArbiter();
            assertThat(arbiter.getPathCache()).isNotNull();
        }
    }
}
