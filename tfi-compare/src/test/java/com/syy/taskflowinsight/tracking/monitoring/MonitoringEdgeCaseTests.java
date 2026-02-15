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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Surgical coverage tests for DegradationManager, DegradationDecisionEngine.
 */
@DisplayName("Monitoring — Surgical Tests")
class MonitoringEdgeCaseTests {

    private DegradationManager manager;
    private DegradationConfig config;
    private DegradationDecisionEngine engine;
    private DegradationPerformanceMonitor performanceMonitor;
    private ResourceMonitor resourceMonitor;
    private TfiMetrics tfiMetrics;

    @BeforeEach
    void setUp() {
        config = new DegradationConfig(
            true,
            Duration.ofSeconds(5),
            Duration.ofMillis(50),
            Duration.ofSeconds(10),
            200L,
            90.0,
            1000L,
            new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
            new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
            500,
            5,
            10000
        );
        performanceMonitor = new DegradationPerformanceMonitor();
        resourceMonitor = new ResourceMonitor();
        tfiMetrics = new TfiMetrics(Optional.of(new SimpleMeterRegistry()));
        engine = new DegradationDecisionEngine(config);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        manager = new DegradationManager(
            performanceMonitor,
            resourceMonitor,
            tfiMetrics,
            config,
            engine,
            eventPublisher
        );
    }

    @AfterEach
    void tearDown() {
        DegradationContext.clear();
    }

    // ── DegradationManager ──

    @Nested
    @DisplayName("DegradationManager — State and API")
    class DegradationManagerTests {

        @Test
        @DisplayName("getCurrentLevel returns initial FULL_TRACKING")
        void getCurrentLevel() {
            assertThat(manager.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
        }

        @Test
        @DisplayName("forceLevel changes level")
        void forceLevel() {
            manager.forceLevel(DegradationLevel.SUMMARY_ONLY, "manual-test");
            assertThat(manager.getCurrentLevel()).isEqualTo(DegradationLevel.SUMMARY_ONLY);
        }

        @Test
        @DisplayName("forceLevel to DISABLED")
        void forceLevel_disabled() {
            manager.forceLevel(DegradationLevel.DISABLED, "emergency");
            assertThat(manager.getCurrentLevel()).isEqualTo(DegradationLevel.DISABLED);
        }

        @Test
        @DisplayName("evaluateAndAdjust runs without error")
        void evaluateAndAdjust() {
            manager.evaluateAndAdjust();
            assertThat(manager.getLastMetrics()).isNotNull();
        }

        @Test
        @DisplayName("getLastMetrics returns metrics after evaluate")
        void getLastMetrics() {
            assertThat(manager.getLastMetrics()).isNull();
            manager.evaluateAndAdjust();
            assertThat(manager.getLastMetrics()).isNotNull();
        }

        @Test
        @DisplayName("isSystemHealthy true when no metrics")
        void isSystemHealthy_noMetrics() {
            assertThat(manager.isSystemHealthy()).isTrue();
        }

        @Test
        @DisplayName("isSystemHealthy false when level DISABLED")
        void isSystemHealthy_disabled() {
            manager.forceLevel(DegradationLevel.DISABLED, "test");
            manager.evaluateAndAdjust();
            assertThat(manager.isSystemHealthy()).isFalse();
        }

        @Test
        @DisplayName("getStatusSummary")
        void getStatusSummary() {
            String summary = manager.getStatusSummary();
            assertThat(summary).contains("DegradationManager");
            assertThat(summary).contains("level=");
        }
    }

    // ── DegradationDecisionEngine ──

    @Nested
    @DisplayName("DegradationDecisionEngine — calculateOptimalLevel scenarios")
    class DegradationDecisionEngineTests {

        @Test
        @DisplayName("healthy metrics → FULL_TRACKING")
        void calculateOptimalLevel_healthy() {
            SystemMetrics metrics = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(50))
                .memoryUsagePercent(50)
                .cpuUsagePercent(40)
                .availableMemoryMB(2000)
                .threadCount(50)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, performanceMonitor);
            assertThat(level).isEqualTo(DegradationLevel.FULL_TRACKING);
        }

        @Test
        @DisplayName("critical memory → DISABLED")
        void calculateOptimalLevel_criticalMemory() {
            SystemMetrics metrics = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(50))
                .memoryUsagePercent(95)
                .cpuUsagePercent(40)
                .availableMemoryMB(50)
                .threadCount(50)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, performanceMonitor);
            assertThat(level).isEqualTo(DegradationLevel.DISABLED);
        }

        @Test
        @DisplayName("high memory → SUMMARY_ONLY")
        void calculateOptimalLevel_highMemory() {
            SystemMetrics metrics = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(50))
                .memoryUsagePercent(85)
                .cpuUsagePercent(40)
                .availableMemoryMB(500)
                .threadCount(50)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, performanceMonitor);
            assertThat(level).isEqualTo(DegradationLevel.SUMMARY_ONLY);
        }

        @Test
        @DisplayName("severe performance → DISABLED")
        void calculateOptimalLevel_severePerf() {
            DegradationPerformanceMonitor slowMonitor = new DegradationPerformanceMonitor();
            for (int i = 0; i < 10; i++) {
                slowMonitor.recordOperation(Duration.ofMillis(1500));
            }
            SystemMetrics metrics = SystemMetrics.builder()
                .averageOperationTime(Duration.ofSeconds(2))
                .memoryUsagePercent(50)
                .cpuUsagePercent(40)
                .availableMemoryMB(2000)
                .threadCount(50)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, slowMonitor);
            assertThat(level).isEqualTo(DegradationLevel.DISABLED);
        }

        @Test
        @DisplayName("high CPU → SUMMARY_ONLY")
        void calculateOptimalLevel_highCpu() {
            SystemMetrics metrics = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(50))
                .memoryUsagePercent(50)
                .cpuUsagePercent(90)
                .availableMemoryMB(2000)
                .threadCount(50)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, performanceMonitor);
            assertThat(level).isEqualTo(DegradationLevel.SUMMARY_ONLY);
        }

        @Test
        @DisplayName("critical CPU → DISABLED")
        void calculateOptimalLevel_criticalCpu() {
            SystemMetrics metrics = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(50))
                .memoryUsagePercent(50)
                .cpuUsagePercent(96)
                .availableMemoryMB(2000)
                .threadCount(50)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, performanceMonitor);
            assertThat(level).isEqualTo(DegradationLevel.DISABLED);
        }

        @Test
        @DisplayName("low available memory → SKIP_DEEP or SUMMARY")
        void calculateOptimalLevel_lowAvailableMem() {
            SystemMetrics metrics = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(50))
                .memoryUsagePercent(50)
                .cpuUsagePercent(40)
                .availableMemoryMB(80)
                .threadCount(50)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, performanceMonitor);
            assertThat(level).isIn(DegradationLevel.SUMMARY_ONLY, DegradationLevel.SKIP_DEEP_ANALYSIS);
        }

        @Test
        @DisplayName("high thread count → SKIP_DEEP_ANALYSIS")
        void calculateOptimalLevel_highThreads() {
            SystemMetrics metrics = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(50))
                .memoryUsagePercent(50)
                .cpuUsagePercent(50)
                .availableMemoryMB(2000)
                .threadCount(1500)
                .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, performanceMonitor);
            assertThat(level).isEqualTo(DegradationLevel.SKIP_DEEP_ANALYSIS);
        }

        @Test
        @DisplayName("null performanceMonitor handled")
        void calculateOptimalLevel_nullMonitor() {
            SystemMetrics metrics = SystemMetrics.builder()
                .averageOperationTime(Duration.ofMillis(50))
                .memoryUsagePercent(50)
                .cpuUsagePercent(40)
                .availableMemoryMB(2000)
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
}
