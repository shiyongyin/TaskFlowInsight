package com.syy.taskflowinsight.tracking.monitoring;

import com.syy.taskflowinsight.metrics.TfiMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Deep coverage tests for tracking/monitoring package.
 * Covers DegradationManager, DegradationDecisionEngine, ResourceMonitor,
 * DegradationConfig, DegradationLevel, DegradationContext,
 * DegradationPerformanceMonitor, DegradationLevelChangedEvent,
 * SystemMetrics, DegradationConfiguration.
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@DisplayName("Tracking/Monitoring — Deep Coverage Tests")
class MonitoringDeepCoverageTests {

    @AfterEach
    void tearDown() {
        DegradationContext.clear();
    }

    // ── DegradationLevel ──

    @Nested
    @DisplayName("DegradationLevel — Enum and behavior")
    class DegradationLevelTests {

        @Test
        @DisplayName("all levels have correct maxDepth and maxElements")
        void levels_haveCorrectLimits() {
            assertThat(DegradationLevel.FULL_TRACKING.getMaxDepth()).isEqualTo(10);
            assertThat(DegradationLevel.FULL_TRACKING.getMaxElements()).isEqualTo(Integer.MAX_VALUE);

            assertThat(DegradationLevel.SKIP_DEEP_ANALYSIS.getMaxDepth()).isEqualTo(8);
            assertThat(DegradationLevel.SKIP_DEEP_ANALYSIS.getMaxElements()).isEqualTo(10000);

            assertThat(DegradationLevel.SIMPLE_COMPARISON.getMaxDepth()).isEqualTo(5);
            assertThat(DegradationLevel.SIMPLE_COMPARISON.getMaxElements()).isEqualTo(5000);

            assertThat(DegradationLevel.SUMMARY_ONLY.getMaxDepth()).isEqualTo(3);
            assertThat(DegradationLevel.SUMMARY_ONLY.getMaxElements()).isEqualTo(1000);

            assertThat(DegradationLevel.DISABLED.getMaxDepth()).isEqualTo(0);
            assertThat(DegradationLevel.DISABLED.getMaxElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("allowsDeepAnalysis varies by level")
        void allowsDeepAnalysis() {
            assertThat(DegradationLevel.FULL_TRACKING.allowsDeepAnalysis()).isTrue();
            assertThat(DegradationLevel.SKIP_DEEP_ANALYSIS.allowsDeepAnalysis()).isFalse();
            assertThat(DegradationLevel.DISABLED.allowsDeepAnalysis()).isFalse();
        }

        @Test
        @DisplayName("allowsMoveDetection varies by level")
        void allowsMoveDetection() {
            assertThat(DegradationLevel.FULL_TRACKING.allowsMoveDetection()).isTrue();
            assertThat(DegradationLevel.SKIP_DEEP_ANALYSIS.allowsMoveDetection()).isTrue();
            assertThat(DegradationLevel.SIMPLE_COMPARISON.allowsMoveDetection()).isFalse();
        }

        @Test
        @DisplayName("onlySummaryInfo only for SUMMARY_ONLY")
        void onlySummaryInfo() {
            assertThat(DegradationLevel.SUMMARY_ONLY.onlySummaryInfo()).isTrue();
            assertThat(DegradationLevel.FULL_TRACKING.onlySummaryInfo()).isFalse();
        }

        @Test
        @DisplayName("isDisabled only for DISABLED")
        void isDisabled() {
            assertThat(DegradationLevel.DISABLED.isDisabled()).isTrue();
            assertThat(DegradationLevel.FULL_TRACKING.isDisabled()).isFalse();
        }

        @Test
        @DisplayName("isMoreRestrictiveThan compares ordinal")
        void isMoreRestrictiveThan() {
            assertThat(DegradationLevel.DISABLED.isMoreRestrictiveThan(DegradationLevel.FULL_TRACKING)).isTrue();
            assertThat(DegradationLevel.FULL_TRACKING.isMoreRestrictiveThan(DegradationLevel.DISABLED)).isFalse();
            assertThat(DegradationLevel.SUMMARY_ONLY.isMoreRestrictiveThan(DegradationLevel.SIMPLE_COMPARISON)).isTrue();
        }

        @Test
        @DisplayName("getNextMoreRestrictive returns next level")
        void getNextMoreRestrictive() {
            assertThat(DegradationLevel.FULL_TRACKING.getNextMoreRestrictive()).isEqualTo(DegradationLevel.SKIP_DEEP_ANALYSIS);
            assertThat(DegradationLevel.DISABLED.getNextMoreRestrictive()).isEqualTo(DegradationLevel.DISABLED);
        }

        @Test
        @DisplayName("getNextLessRestrictive returns previous level")
        void getNextLessRestrictive() {
            assertThat(DegradationLevel.SKIP_DEEP_ANALYSIS.getNextLessRestrictive()).isEqualTo(DegradationLevel.FULL_TRACKING);
            assertThat(DegradationLevel.FULL_TRACKING.getNextLessRestrictive()).isEqualTo(DegradationLevel.FULL_TRACKING);
        }

        @Test
        @DisplayName("toString includes description")
        void toString_includesDescription() {
            assertThat(DegradationLevel.FULL_TRACKING.toString()).contains("FULL_TRACKING");
            assertThat(DegradationLevel.DISABLED.toString()).contains("完全禁用");
        }
    }

    // ── DegradationContext ──

    @Nested
    @DisplayName("DegradationContext — ThreadLocal context")
    class DegradationContextTests {

        @Test
        @DisplayName("getCurrentLevel returns FULL_TRACKING by default")
        void getCurrentLevel_default() {
            DegradationContext.clear();
            assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
        }

        @Test
        @DisplayName("setCurrentLevel and getCurrentLevel")
        void setAndGetLevel() {
            DegradationContext.setCurrentLevel(DegradationLevel.SUMMARY_ONLY);
            assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.SUMMARY_ONLY);
        }

        @Test
        @DisplayName("setCurrentLevel ignores null")
        void setCurrentLevel_ignoresNull() {
            DegradationContext.setCurrentLevel(DegradationLevel.SKIP_DEEP_ANALYSIS);
            DegradationContext.setCurrentLevel(null);
            assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.SKIP_DEEP_ANALYSIS);
        }

        @Test
        @DisplayName("reset restores FULL_TRACKING")
        void reset() {
            DegradationContext.setCurrentLevel(DegradationLevel.DISABLED);
            DegradationContext.reset();
            assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
        }

        @Test
        @DisplayName("clear removes thread local")
        void clear() {
            DegradationContext.setCurrentLevel(DegradationLevel.SUMMARY_ONLY);
            DegradationContext.clear();
            assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
        }

        @Test
        @DisplayName("setPerformanceMonitor and getPerformanceMonitor")
        void setAndGetPerformanceMonitor() {
            DegradationPerformanceMonitor monitor = new DegradationPerformanceMonitor();
            DegradationContext.setPerformanceMonitor(monitor);
            assertThat(DegradationContext.getPerformanceMonitor()).isSameAs(monitor);
        }

        @Test
        @DisplayName("allowsDeepAnalysis delegates to current level")
        void allowsDeepAnalysis() {
            DegradationContext.setCurrentLevel(DegradationLevel.FULL_TRACKING);
            assertThat(DegradationContext.allowsDeepAnalysis()).isTrue();
            DegradationContext.setCurrentLevel(DegradationLevel.DISABLED);
            assertThat(DegradationContext.allowsDeepAnalysis()).isFalse();
        }

        @Test
        @DisplayName("exceedsElementLimit checks maxElements")
        void exceedsElementLimit() {
            DegradationContext.setCurrentLevel(DegradationLevel.SUMMARY_ONLY); // max 1000
            assertThat(DegradationContext.exceedsElementLimit(1500)).isTrue();
            assertThat(DegradationContext.exceedsElementLimit(500)).isFalse();
        }

        @Test
        @DisplayName("getAdjustedSize caps at maxElements")
        void getAdjustedSize() {
            DegradationContext.setCurrentLevel(DegradationLevel.SUMMARY_ONLY);
            assertThat(DegradationContext.getAdjustedSize(2000)).isEqualTo(1000);
            assertThat(DegradationContext.getAdjustedSize(500)).isEqualTo(500);
        }

        @Test
        @DisplayName("getAdjustedSize returns original when maxElements unlimited")
        void getAdjustedSize_unlimited() {
            DegradationContext.setCurrentLevel(DegradationLevel.FULL_TRACKING);
            assertThat(DegradationContext.getAdjustedSize(5000)).isEqualTo(5000);
        }

        @Test
        @DisplayName("recordOperationIfEnabled does nothing when no monitor")
        void recordOperationIfEnabled_noMonitor() {
            DegradationContext.clear();
            DegradationContext.recordOperationIfEnabled("test", System.nanoTime());
        }
    }

    // ── SystemMetrics ──

    @Nested
    @DisplayName("SystemMetrics — Builder and methods")
    class SystemMetricsTests {

        @Test
        @DisplayName("builder creates metrics")
        void builder() {
            SystemMetrics m = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(50))
                .slowOperationCount(5)
                .memoryUsagePercent(75.5)
                .availableMemoryMB(500)
                .cpuUsagePercent(60.0)
                .threadCount(25)
                .timestamp(System.currentTimeMillis())
                .build();

            assertThat(m.averageOperationTime()).isEqualTo(Duration.ofMillis(50));
            assertThat(m.memoryUsagePercent()).isEqualTo(75.5);
            assertThat(m.availableMemoryMB()).isEqualTo(500);
        }

        @Test
        @DisplayName("hasMemoryPressure")
        void hasMemoryPressure() {
            SystemMetrics m = SystemMetrics.builder()
                .memoryUsagePercent(85.0)
                .build();
            assertThat(m.hasMemoryPressure(80.0)).isTrue();
            assertThat(m.hasMemoryPressure(90.0)).isFalse();
        }

        @Test
        @DisplayName("hasPerformancePressure")
        void hasPerformancePressure() {
            SystemMetrics m = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(200))
                .build();
            assertThat(m.hasPerformancePressure(Duration.ofMillis(100))).isTrue();
            assertThat(m.hasPerformancePressure(Duration.ofMillis(300))).isFalse();
        }

        @Test
        @DisplayName("hasHighSlowOperationRate")
        void hasHighSlowOperationRate() {
            SystemMetrics m = SystemMetrics.builder()
                .slowOperationCount(5)
                .build();
            assertThat(m.hasHighSlowOperationRate(0.1, 50)).isTrue();
            assertThat(m.hasHighSlowOperationRate(0.5, 50)).isFalse();
            assertThat(m.hasHighSlowOperationRate(0.1, 0)).isFalse();
        }

        @Test
        @DisplayName("getMemoryPressureLevel")
        void getMemoryPressureLevel() {
            assertThat(SystemMetrics.builder().memoryUsagePercent(95).build().getMemoryPressureLevel()).isEqualTo("critical");
            assertThat(SystemMetrics.builder().memoryUsagePercent(85).build().getMemoryPressureLevel()).isEqualTo("high");
            assertThat(SystemMetrics.builder().memoryUsagePercent(65).build().getMemoryPressureLevel()).isEqualTo("low");
            assertThat(SystemMetrics.builder().memoryUsagePercent(50).build().getMemoryPressureLevel()).isEqualTo("normal");
        }

        @Test
        @DisplayName("toString")
        void toString_() {
            SystemMetrics m = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(100))
                .slowOperationCount(2)
                .memoryUsagePercent(70)
                .cpuUsagePercent(50)
                .threadCount(10)
                .build();
            assertThat(m.toString()).contains("SystemMetrics");
        }
    }

    // ── DegradationConfig ──

    @Nested
    @DisplayName("DegradationConfig — Configuration")
    class DegradationConfigTests {

        @Test
        @DisplayName("default constructor applies defaults")
        void defaultConstructor() {
            DegradationConfig config = new DegradationConfig(
                null, null, null, null, null, null, null,
                null, null, null, null, null
            );

            assertThat(config.enabled()).isFalse();
            assertThat(config.evaluationInterval()).isEqualTo(Duration.ofSeconds(5));
            assertThat(config.listSizeThreshold()).isEqualTo(500);
        }

        @Test
        @DisplayName("MemoryThresholds getDegradationLevelForMemory")
        void memoryThresholds_getLevel() {
            DegradationConfig.MemoryThresholds mt = new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0);

            assertThat(mt.getDegradationLevelForMemory(50)).isEqualTo(DegradationLevel.FULL_TRACKING);
            assertThat(mt.getDegradationLevelForMemory(65)).isEqualTo(DegradationLevel.SKIP_DEEP_ANALYSIS);
            assertThat(mt.getDegradationLevelForMemory(75)).isEqualTo(DegradationLevel.SIMPLE_COMPARISON);
            assertThat(mt.getDegradationLevelForMemory(85)).isEqualTo(DegradationLevel.SUMMARY_ONLY);
            assertThat(mt.getDegradationLevelForMemory(95)).isEqualTo(DegradationLevel.DISABLED);
        }

        @Test
        @DisplayName("shouldDegradeForListSize")
        void shouldDegradeForListSize() {
            DegradationConfig config = new DegradationConfig(
                true, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(10),
                200L, 90.0, 1000L,
                new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
                500, 5, 10000
            );

            assertThat(config.shouldDegradeForListSize(600)).isTrue();
            assertThat(config.shouldDegradeForListSize(400)).isFalse();
        }

        @Test
        @DisplayName("shouldDegradeForKPairs")
        void shouldDegradeForKPairs() {
            DegradationConfig config = new DegradationConfig(
                true, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(10),
                200L, 90.0, 1000L,
                new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
                500, 5, 10000
            );

            assertThat(config.shouldDegradeForKPairs(15000)).isTrue();
            assertThat(config.shouldDegradeForKPairs(5000)).isFalse();
        }

        @Test
        @DisplayName("isSlowOperation")
        void isSlowOperation() {
            DegradationConfig config = new DegradationConfig(
                true, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(10),
                200L, 90.0, 1000L,
                new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
                500, 5, 10000
            );

            assertThat(config.isSlowOperation(Duration.ofMillis(250))).isTrue();
            assertThat(config.isSlowOperation(Duration.ofMillis(100))).isFalse();
        }

        @Test
        @DisplayName("isCriticalMemoryUsage")
        void isCriticalMemoryUsage() {
            DegradationConfig config = new DegradationConfig(
                true, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(10),
                200L, 90.0, 1000L,
                new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
                500, 5, 10000
            );

            assertThat(config.isCriticalMemoryUsage(95)).isTrue();
            assertThat(config.isCriticalMemoryUsage(80)).isFalse();
        }
    }

    // ── DegradationPerformanceMonitor ──

    @Nested
    @DisplayName("DegradationPerformanceMonitor — Performance tracking")
    class DegradationPerformanceMonitorTests {

        @Test
        @DisplayName("recordOperation updates counters")
        void recordOperation() {
            DegradationPerformanceMonitor monitor = new DegradationPerformanceMonitor();

            monitor.recordOperation(Duration.ofMillis(50));
            monitor.recordOperation(Duration.ofMillis(250));

            assertThat(monitor.getTotalOperations()).isEqualTo(2);
            assertThat(monitor.getSlowOperationCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("recordOperation ignores null and negative")
        void recordOperation_invalid() {
            DegradationPerformanceMonitor monitor = new DegradationPerformanceMonitor();

            monitor.recordOperation(null);
            monitor.recordOperation(Duration.ofMillis(-1));

            assertThat(monitor.getTotalOperations()).isEqualTo(0);
        }

        @Test
        @DisplayName("getAverageTime returns ZERO when no ops")
        void getAverageTime_noOps() {
            DegradationPerformanceMonitor monitor = new DegradationPerformanceMonitor();
            assertThat(monitor.getAverageTime()).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("getSlowOperationRate returns 0 when no ops")
        void getSlowOperationRate_noOps() {
            DegradationPerformanceMonitor monitor = new DegradationPerformanceMonitor();
            assertThat(monitor.getSlowOperationRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("isPerformanceDegraded")
        void isPerformanceDegraded() {
            DegradationPerformanceMonitor monitor = new DegradationPerformanceMonitor();
            monitor.recordOperation(Duration.ofMillis(200));
            monitor.recordOperation(Duration.ofMillis(200));

            assertThat(monitor.isPerformanceDegraded()).isTrue();
        }

        @Test
        @DisplayName("reset clears counters")
        void reset() {
            DegradationPerformanceMonitor monitor = new DegradationPerformanceMonitor();
            monitor.recordOperation(Duration.ofMillis(100));
            monitor.reset();

            assertThat(monitor.getTotalOperations()).isEqualTo(0);
        }

        @Test
        @DisplayName("getStatsSummary")
        void getStatsSummary() {
            DegradationPerformanceMonitor monitor = new DegradationPerformanceMonitor();
            monitor.recordOperation(Duration.ofMillis(100));

            assertThat(monitor.getStatsSummary()).contains("PerformanceStats");
        }

        @Test
        @DisplayName("isDataFresh")
        void isDataFresh() {
            DegradationPerformanceMonitor monitor = new DegradationPerformanceMonitor();
            monitor.recordOperation(Duration.ofMillis(50));
            assertThat(monitor.isDataFresh()).isTrue();
        }
    }

    // ── DegradationLevelChangedEvent ──

    @Nested
    @DisplayName("DegradationLevelChangedEvent — Event")
    class DegradationLevelChangedEventTests {

        @Test
        @DisplayName("event holds all fields")
        void eventFields() {
            SystemMetrics metrics = SystemMetrics.builder().memoryUsagePercent(85).build();
            Object source = new Object();

            DegradationLevelChangedEvent event = new DegradationLevelChangedEvent(
                source, DegradationLevel.FULL_TRACKING, DegradationLevel.SUMMARY_ONLY,
                "memory_high", metrics
            );

            assertThat(event.getPreviousLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
            assertThat(event.getCurrentLevel()).isEqualTo(DegradationLevel.SUMMARY_ONLY);
            assertThat(event.getReason()).isEqualTo("memory_high");
            assertThat(event.getMetrics()).isSameAs(metrics);
            assertThat(event.getEventTimestamp()).isGreaterThan(0);
        }

        @Test
        @DisplayName("isDegradation when level becomes more restrictive")
        void isDegradation() {
            DegradationLevelChangedEvent event = new DegradationLevelChangedEvent(
                new Object(), DegradationLevel.FULL_TRACKING, DegradationLevel.DISABLED,
                "critical", null
            );
            assertThat(event.isDegradation()).isTrue();
        }

        @Test
        @DisplayName("isRecovery when level becomes less restrictive")
        void isRecovery() {
            DegradationLevelChangedEvent event = new DegradationLevelChangedEvent(
                new Object(), DegradationLevel.DISABLED, DegradationLevel.FULL_TRACKING,
                "recovered", null
            );
            assertThat(event.isRecovery()).isTrue();
        }

        @Test
        @DisplayName("getSeverityChange")
        void getSeverityChange() {
            DegradationLevelChangedEvent event = new DegradationLevelChangedEvent(
                new Object(), DegradationLevel.FULL_TRACKING, DegradationLevel.SUMMARY_ONLY,
                "test", null
            );
            // FULL_TRACKING=0, SUMMARY_ONLY=3, |3-0|=3
            assertThat(event.getSeverityChange()).isEqualTo(3);
        }

        @Test
        @DisplayName("toString")
        void toString_() {
            DegradationLevelChangedEvent event = new DegradationLevelChangedEvent(
                new Object(), DegradationLevel.FULL_TRACKING, DegradationLevel.SUMMARY_ONLY,
                "test", null
            );
            assertThat(event.toString()).contains("FULL_TRACKING");
        }
    }

    // ── ResourceMonitor ──

    @Nested
    @DisplayName("ResourceMonitor — Resource metrics")
    class ResourceMonitorTests {

        @Test
        @DisplayName("getMemoryUsagePercent returns 0-100")
        void getMemoryUsagePercent() {
            ResourceMonitor monitor = new ResourceMonitor();
            double usage = monitor.getMemoryUsagePercent();
            assertThat(usage).isBetween(0.0, 100.0);
        }

        @Test
        @DisplayName("getAvailableMemoryMB returns non-negative")
        void getAvailableMemoryMB() {
            ResourceMonitor monitor = new ResourceMonitor();
            assertThat(monitor.getAvailableMemoryMB()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("getActiveThreadCount returns positive")
        void getActiveThreadCount() {
            ResourceMonitor monitor = new ResourceMonitor();
            assertThat(monitor.getActiveThreadCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("getCpuUsagePercent returns 0-100")
        void getCpuUsagePercent() {
            ResourceMonitor monitor = new ResourceMonitor();
            double cpu = monitor.getCpuUsagePercent();
            assertThat(cpu).isBetween(0.0, 100.0);
        }

        @Test
        @DisplayName("getAvailableProcessors returns positive")
        void getAvailableProcessors() {
            ResourceMonitor monitor = new ResourceMonitor();
            assertThat(monitor.getAvailableProcessors()).isGreaterThan(0);
        }

        @Test
        @DisplayName("isMemoryPressureHigh")
        void isMemoryPressureHigh() {
            ResourceMonitor monitor = new ResourceMonitor();
            double usage = monitor.getMemoryUsagePercent();
            assertThat(monitor.isMemoryPressureHigh(usage + 1)).isFalse();
            assertThat(monitor.isMemoryPressureHigh(0)).isTrue();
        }

        @Test
        @DisplayName("shouldTriggerDegradation")
        void shouldTriggerDegradation() {
            ResourceMonitor monitor = new ResourceMonitor();
            boolean result = monitor.shouldTriggerDegradation();
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("getResourceSummary")
        void getResourceSummary() {
            ResourceMonitor monitor = new ResourceMonitor();
            assertThat(monitor.getResourceSummary()).contains("ResourceStats");
        }
    }

    // ── DegradationDecisionEngine ──

    @Nested
    @DisplayName("DegradationDecisionEngine — Decision logic")
    class DegradationDecisionEngineTests {

        private DegradationConfig config;
        private DegradationDecisionEngine engine;

        @BeforeEach
        void setUp() {
            config = new DegradationConfig(
                true, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(10),
                200L, 90.0, 1000L,
                new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
                500, 5, 10000
            );
            engine = new DegradationDecisionEngine(config);
        }

        @Test
        @DisplayName("calculateOptimalLevel returns FULL_TRACKING for healthy metrics")
        void calculateOptimalLevel_healthy() {
            SystemMetrics metrics = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(50))
                .memoryUsagePercent(50)
                .cpuUsagePercent(40)
                .availableMemoryMB(5000)
                .threadCount(50)
                .build();

            DegradationLevel level = engine.calculateOptimalLevel(metrics, new DegradationPerformanceMonitor());
            assertThat(level).isEqualTo(DegradationLevel.FULL_TRACKING);
        }

        @Test
        @DisplayName("calculateOptimalLevel returns DISABLED for critical memory")
        void calculateOptimalLevel_criticalMemory() {
            SystemMetrics metrics = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(50))
                .memoryUsagePercent(95)
                .cpuUsagePercent(40)
                .availableMemoryMB(50)
                .threadCount(50)
                .build();

            DegradationLevel level = engine.calculateOptimalLevel(metrics, new DegradationPerformanceMonitor());
            assertThat(level).isEqualTo(DegradationLevel.DISABLED);
        }

        @Test
        @DisplayName("calculateOptimalLevel with null performanceMonitor")
        void calculateOptimalLevel_nullMonitor() {
            SystemMetrics metrics = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(50))
                .memoryUsagePercent(50)
                .cpuUsagePercent(40)
                .availableMemoryMB(5000)
                .threadCount(50)
                .build();

            DegradationLevel level = engine.calculateOptimalLevel(metrics, null);
            assertThat(level).isNotNull();
        }

        @Test
        @DisplayName("shouldDegradeForListSize delegates to config")
        void shouldDegradeForListSize() {
            assertThat(engine.shouldDegradeForListSize(600)).isTrue();
            assertThat(engine.shouldDegradeForListSize(400)).isFalse();
        }

        @Test
        @DisplayName("shouldDegradeForKPairs delegates to config")
        void shouldDegradeForKPairs() {
            assertThat(engine.shouldDegradeForKPairs(15000)).isTrue();
            assertThat(engine.shouldDegradeForKPairs(5000)).isFalse();
        }
    }

    // ── DegradationManager ──

    @Nested
    @DisplayName("DegradationManager — Integration")
    class DegradationManagerTests {

        private DegradationManager manager;
        private TfiMetrics tfiMetrics;

        @BeforeEach
        void setUp() {
            DegradationConfig config = new DegradationConfig(
                true, Duration.ofSeconds(5), Duration.ofMillis(100),
                Duration.ofSeconds(10), 200L, 90.0, 1000L,
                new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
                new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
                500, 5, 10000
            );
            tfiMetrics = new TfiMetrics(Optional.of(new SimpleMeterRegistry()));
            DegradationPerformanceMonitor perfMonitor = new DegradationPerformanceMonitor();
            ResourceMonitor resourceMonitor = new ResourceMonitor();
            DegradationDecisionEngine decisionEngine = new DegradationDecisionEngine(config);
            ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

            manager = new DegradationManager(
                perfMonitor, resourceMonitor, tfiMetrics, config, decisionEngine, eventPublisher
            );
        }

        @Test
        @DisplayName("getCurrentLevel returns initial level")
        void getCurrentLevel() {
            assertThat(manager.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
        }

        @Test
        @DisplayName("forceLevel changes level")
        void forceLevel() {
            manager.forceLevel(DegradationLevel.SUMMARY_ONLY, "test");

            assertThat(manager.getCurrentLevel()).isEqualTo(DegradationLevel.SUMMARY_ONLY);
        }

        @Test
        @DisplayName("evaluateAndAdjust runs without error")
        void evaluateAndAdjust() {
            manager.evaluateAndAdjust();
            assertThat(manager.getLastMetrics()).isNotNull();
        }

        @Test
        @DisplayName("isSystemHealthy when no metrics")
        void isSystemHealthy_noMetrics() {
            assertThat(manager.isSystemHealthy()).isTrue();
        }

        @Test
        @DisplayName("getStatusSummary")
        void getStatusSummary() {
            manager.evaluateAndAdjust();
            assertThat(manager.getStatusSummary()).contains("DegradationManager");
        }
    }
}
