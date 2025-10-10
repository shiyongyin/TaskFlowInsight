package com.syy.taskflowinsight.tracking.monitoring;

import com.syy.taskflowinsight.metrics.TfiMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

/**
 * 降级机制集成测试
 * 
 * 验证完整的5级降级链和阈值触发
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 */
@SpringBootTest(properties = {
    "tfi.change-tracking.degradation.enabled=true",
    "tfi.change-tracking.degradation.evaluation-interval=100ms",
    "tfi.change-tracking.degradation.min-level-change-duration=50ms",
    "tfi.change-tracking.degradation.memory-thresholds.skip-deep-analysis=60.0",
    "tfi.change-tracking.degradation.memory-thresholds.simple-comparison=70.0",
    "tfi.change-tracking.degradation.memory-thresholds.summary-only=80.0",
    "tfi.change-tracking.degradation.memory-thresholds.disabled=90.0",
    "tfi.change-tracking.degradation.performance-thresholds.average-operation-time-ms=200",
    "tfi.change-tracking.degradation.performance-thresholds.slow-operation-rate=0.05",
    "tfi.change-tracking.degradation.performance-thresholds.cpu-usage-percent=80.0",
    "tfi.change-tracking.enabled=true",
    "management.endpoint.tfi.enabled=false",
    "tfi.actuator.enabled=false",
    "taskflow.monitoring.endpoint.enabled=false",
    "tfi.annotation.enabled=false",
    "tfi.performance.dashboard.enabled=false"
})
class DegradationIntegrationTest {
    
    private DegradationPerformanceMonitor performanceMonitor;
    private ResourceMonitor resourceMonitor;
    private TfiMetrics tfiMetrics;
    private DegradationConfig config;
    private DegradationDecisionEngine decisionEngine;
    private DegradationManager degradationManager;
    
    @BeforeEach
    void setUp() {
        performanceMonitor = new DegradationPerformanceMonitor();
        resourceMonitor = spy(new ResourceMonitor());
        tfiMetrics = mock(TfiMetrics.class);
        
        // 创建测试配置
        config = new DegradationConfig(
            true,                                     // enabled
            Duration.ofMillis(100),                   // evaluationInterval
            Duration.ofMillis(50),                    // minLevelChangeDuration
            Duration.ofSeconds(1),                    // metricsCacheTime
            200L,                                     // slowOperationThresholdMs
            90.0,                                     // criticalMemoryThreshold
            1000L,                                    // criticalOperationTimeMs
            new DegradationConfig.MemoryThresholds(60.0, 70.0, 80.0, 90.0),
            new DegradationConfig.PerformanceThresholds(200L, 0.05, 80.0),
            500,                                      // listSizeThreshold
            5,                                        // maxCandidates
            10000                                     // kPairsThreshold
        );
        
        decisionEngine = new DegradationDecisionEngine(config);
        degradationManager = new DegradationManager(
            performanceMonitor, resourceMonitor, tfiMetrics, config, decisionEngine, null
        );
    }
    
    @Test
    @DisplayName("正常状态下应保持FULL_TRACKING级别")
    void shouldMaintainFullTrackingUnderNormalConditions() {
        // Given - 模拟正常系统状态
        when(resourceMonitor.getMemoryUsagePercent()).thenReturn(45.0);
        when(resourceMonitor.getCpuUsagePercent()).thenReturn(30.0);
        when(resourceMonitor.getActiveThreadCount()).thenReturn(50);
        when(resourceMonitor.getAvailableMemoryMB()).thenReturn(2048L);
        
        // 记录一些正常操作
        performanceMonitor.recordOperation(Duration.ofMillis(50));
        performanceMonitor.recordOperation(Duration.ofMillis(80));
        performanceMonitor.recordOperation(Duration.ofMillis(120));
        
        // When
        degradationManager.evaluateAndAdjust();
        
        // Then
        assertThat(degradationManager.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
        assertThat(degradationManager.isSystemHealthy()).isTrue();
    }
    
    @Test
    @DisplayName("内存压力达到80%应触发SUMMARY_ONLY级别")
    void shouldTriggerSummaryOnlyAt80PercentMemory() {
        // Given - 模拟高内存使用率
        when(resourceMonitor.getMemoryUsagePercent()).thenReturn(85.0);
        when(resourceMonitor.getCpuUsagePercent()).thenReturn(40.0);
        when(resourceMonitor.getActiveThreadCount()).thenReturn(60);
        when(resourceMonitor.getAvailableMemoryMB()).thenReturn(512L);
        
        // When
        degradationManager.evaluateAndAdjust();
        
        // Then
        assertThat(degradationManager.getCurrentLevel()).isEqualTo(DegradationLevel.SUMMARY_ONLY);
        
        // 验证降级事件被记录
        verify(tfiMetrics).recordDegradationEvent(
            eq("FULL_TRACKING"), 
            eq("SUMMARY_ONLY"), 
            contains("memory_usage=85.0%")
        );
    }
    
    @Test
    @DisplayName("内存压力达到90%应触发DISABLED级别")
    void shouldTriggerDisabledAt90PercentMemory() {
        // Given - 模拟极高内存使用率
        when(resourceMonitor.getMemoryUsagePercent()).thenReturn(92.0);
        when(resourceMonitor.getCpuUsagePercent()).thenReturn(50.0);
        when(resourceMonitor.getActiveThreadCount()).thenReturn(80);
        when(resourceMonitor.getAvailableMemoryMB()).thenReturn(100L);
        
        // When
        degradationManager.evaluateAndAdjust();
        
        // Then
        assertThat(degradationManager.getCurrentLevel()).isEqualTo(DegradationLevel.DISABLED);
        assertThat(degradationManager.isSystemHealthy()).isFalse();
    }
    
    @Test
    @DisplayName("慢操作比率超过阈值应触发性能降级")
    void shouldTriggerPerformanceDegradationOnSlowOperations() {
        // Given - 模拟正常内存但高慢操作比率
        when(resourceMonitor.getMemoryUsagePercent()).thenReturn(50.0);
        when(resourceMonitor.getCpuUsagePercent()).thenReturn(40.0);
        when(resourceMonitor.getActiveThreadCount()).thenReturn(60);
        when(resourceMonitor.getAvailableMemoryMB()).thenReturn(1024L);
        
        // 记录大量慢操作（超过5%阈值）
        for (int i = 0; i < 80; i++) {
            performanceMonitor.recordOperation(Duration.ofMillis(100)); // 正常操作
        }
        for (int i = 0; i < 20; i++) {
            performanceMonitor.recordOperation(Duration.ofMillis(350)); // 慢操作 (>200ms)
        }
        
        // When
        degradationManager.evaluateAndAdjust();
        
        // Then - 20%慢操作率应触发SIMPLE_COMPARISON
        assertThat(degradationManager.getCurrentLevel()).isEqualTo(DegradationLevel.SIMPLE_COMPARISON);
        
        verify(tfiMetrics).recordDegradationEvent(
            eq("FULL_TRACKING"), 
            eq("SIMPLE_COMPARISON"), 
            anyString()
        );
    }
    
    @Test
    @DisplayName("平均操作时间过长应触发降级")
    void shouldTriggerDegradationOnLongAverageTime() {
        // Given - 模拟正常内存但长平均操作时间
        when(resourceMonitor.getMemoryUsagePercent()).thenReturn(50.0);
        when(resourceMonitor.getCpuUsagePercent()).thenReturn(40.0);
        when(resourceMonitor.getActiveThreadCount()).thenReturn(60);
        when(resourceMonitor.getAvailableMemoryMB()).thenReturn(1024L);
        
        // 记录一些长时间操作，但保持慢操作率在合理范围
        performanceMonitor.recordOperation(Duration.ofMillis(600)); // 长操作
        performanceMonitor.recordOperation(Duration.ofMillis(700)); // 长操作  
        performanceMonitor.recordOperation(Duration.ofMillis(800)); // 长操作
        // 添加一些正常操作以降低慢操作率
        for (int i = 0; i < 20; i++) {
            performanceMonitor.recordOperation(Duration.ofMillis(100)); // 正常操作
        }
        
        // When
        degradationManager.evaluateAndAdjust();
        
        // Then - 平均700ms应触发SUMMARY_ONLY
        assertThat(degradationManager.getCurrentLevel()).isEqualTo(DegradationLevel.SUMMARY_ONLY);
    }
    
    @Test
    @DisplayName("极端性能压力应触发DISABLED级别")
    void shouldTriggerDisabledOnExtremePerformance() {
        // Given - 模拟极端性能压力
        when(resourceMonitor.getMemoryUsagePercent()).thenReturn(50.0);
        when(resourceMonitor.getCpuUsagePercent()).thenReturn(98.0); // 极高CPU
        when(resourceMonitor.getActiveThreadCount()).thenReturn(60);
        when(resourceMonitor.getAvailableMemoryMB()).thenReturn(1024L);
        
        // 记录超长操作时间
        performanceMonitor.recordOperation(Duration.ofMillis(1200)); // >1000ms
        performanceMonitor.recordOperation(Duration.ofMillis(1500)); // >1000ms
        
        // When
        degradationManager.evaluateAndAdjust();
        
        // Then - 应触发DISABLED
        assertThat(degradationManager.getCurrentLevel()).isEqualTo(DegradationLevel.DISABLED);
    }
    
    @Test
    @DisplayName("滞后机制应防止频繁级别变更")
    void shouldPreventFrequentLevelChangesWithHysteresis() throws InterruptedException {
        // Given - 初始状态为正常
        when(resourceMonitor.getMemoryUsagePercent()).thenReturn(50.0);
        when(resourceMonitor.getCpuUsagePercent()).thenReturn(40.0);
        when(resourceMonitor.getActiveThreadCount()).thenReturn(60);
        when(resourceMonitor.getAvailableMemoryMB()).thenReturn(1024L);
        
        degradationManager.evaluateAndAdjust();
        assertThat(degradationManager.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
        
        // When - 快速变更到高内存使用率
        when(resourceMonitor.getMemoryUsagePercent()).thenReturn(85.0);
        degradationManager.evaluateAndAdjust(); // 第一次变更应该生效
        assertThat(degradationManager.getCurrentLevel()).isEqualTo(DegradationLevel.SUMMARY_ONLY);
        
        // 立即再次变更到更高内存使用率
        when(resourceMonitor.getMemoryUsagePercent()).thenReturn(92.0);
        degradationManager.evaluateAndAdjust(); // 第二次变更应该被滞后机制抑制
        
        // Then - 级别不应立即变更
        assertThat(degradationManager.getCurrentLevel()).isEqualTo(DegradationLevel.SUMMARY_ONLY);
        
        // 等待滞后时间过后，应该能变更
        Thread.sleep(60); // 等待超过50ms滞后时间
        degradationManager.evaluateAndAdjust();
        assertThat(degradationManager.getCurrentLevel()).isEqualTo(DegradationLevel.DISABLED);
    }
    
    @Test
    @DisplayName("强制降级应立即生效")
    void shouldApplyForcedDegradationImmediately() {
        // Given - 初始状态为正常
        assertThat(degradationManager.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
        
        // When - 强制降级
        degradationManager.forceLevel(DegradationLevel.DISABLED, "circuit_breaker_triggered");
        
        // Then
        assertThat(degradationManager.getCurrentLevel()).isEqualTo(DegradationLevel.DISABLED);
        
        verify(tfiMetrics).recordDegradationEvent(
            eq("FULL_TRACKING"), 
            eq("DISABLED"), 
            eq("forced:circuit_breaker_triggered")
        );
    }
    
    @Test
    @DisplayName("决策引擎应支持列表大小降级检查")
    void shouldSupportListSizeDegradationCheck() {
        // When & Then
        assertThat(decisionEngine.shouldDegradeForListSize(300)).isFalse();
        assertThat(decisionEngine.shouldDegradeForListSize(600)).isTrue(); // >500阈值
        
        assertThat(decisionEngine.shouldDegradeForKPairs(5000)).isFalse();
        assertThat(decisionEngine.shouldDegradeForKPairs(15000)).isTrue(); // >10000阈值
    }
    
    @Test
    @DisplayName("系统指标应正确收集")
    void shouldCollectSystemMetricsCorrectly() {
        // Given
        when(resourceMonitor.getMemoryUsagePercent()).thenReturn(75.0);
        when(resourceMonitor.getCpuUsagePercent()).thenReturn(65.0);
        when(resourceMonitor.getActiveThreadCount()).thenReturn(120);
        when(resourceMonitor.getAvailableMemoryMB()).thenReturn(800L);
        
        performanceMonitor.recordOperation(Duration.ofMillis(150));
        performanceMonitor.recordOperation(Duration.ofMillis(250)); // 慢操作
        
        // When
        degradationManager.evaluateAndAdjust();
        SystemMetrics metrics = degradationManager.getLastMetrics();
        
        // Then
        assertThat(metrics).isNotNull();
        assertThat(metrics.memoryUsagePercent()).isEqualTo(75.0);
        assertThat(metrics.cpuUsagePercent()).isEqualTo(65.0);
        assertThat(metrics.threadCount()).isEqualTo(120);
        assertThat(metrics.availableMemoryMB()).isEqualTo(800L);
        assertThat(metrics.averageOperationTime()).isEqualTo(Duration.ofMillis(200)); // (150+250)/2
        assertThat(metrics.slowOperationCount()).isEqualTo(1); // 250ms > 200ms阈值
    }
}