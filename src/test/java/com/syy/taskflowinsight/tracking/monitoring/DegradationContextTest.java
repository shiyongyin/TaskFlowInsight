package com.syy.taskflowinsight.tracking.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DegradationContext 单元测试
 * 
 * 验证降级上下文的ThreadLocal行为
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 */
class DegradationContextTest {
    
    @BeforeEach
    void setUp() {
        DegradationContext.reset();
    }
    
    @AfterEach
    void tearDown() {
        DegradationContext.clear();
    }
    
    @Test
    @DisplayName("默认状态应为FULL_TRACKING")
    void shouldDefaultToFullTracking() {
        assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
        assertThat(DegradationContext.allowsDeepAnalysis()).isTrue();
        assertThat(DegradationContext.allowsMoveDetection()).isTrue();
        assertThat(DegradationContext.allowsPathOptimization()).isTrue();
        assertThat(DegradationContext.onlySummaryInfo()).isFalse();
        assertThat(DegradationContext.isDisabled()).isFalse();
    }
    
    @Test
    @DisplayName("设置降级级别应正确更新上下文")
    void shouldUpdateContextWhenLevelIsSet() {
        // When
        DegradationContext.setCurrentLevel(DegradationLevel.SUMMARY_ONLY);
        
        // Then
        assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.SUMMARY_ONLY);
        assertThat(DegradationContext.allowsDeepAnalysis()).isFalse();
        assertThat(DegradationContext.allowsMoveDetection()).isFalse();
        assertThat(DegradationContext.allowsPathOptimization()).isFalse();
        assertThat(DegradationContext.onlySummaryInfo()).isTrue();
        assertThat(DegradationContext.isDisabled()).isFalse();
    }
    
    @Test
    @DisplayName("设置DISABLED级别应正确反映状态")
    void shouldReflectDisabledState() {
        // When
        DegradationContext.setCurrentLevel(DegradationLevel.DISABLED);
        
        // Then
        assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.DISABLED);
        assertThat(DegradationContext.allowsDeepAnalysis()).isFalse();
        assertThat(DegradationContext.allowsMoveDetection()).isFalse();
        assertThat(DegradationContext.allowsPathOptimization()).isFalse();
        assertThat(DegradationContext.onlySummaryInfo()).isFalse();
        assertThat(DegradationContext.isDisabled()).isTrue();
    }
    
    @Test
    @DisplayName("应该正确返回当前级别的限制")
    void shouldReturnCurrentLevelLimits() {
        // Given
        DegradationContext.setCurrentLevel(DegradationLevel.SIMPLE_COMPARISON);
        
        // Then
        assertThat(DegradationContext.getMaxDepth()).isEqualTo(5);
        assertThat(DegradationContext.getMaxElements()).isEqualTo(5000);
    }
    
    @Test
    @DisplayName("应该正确检查元素数量限制")
    void shouldCheckElementLimitsCorrectly() {
        // Given
        DegradationContext.setCurrentLevel(DegradationLevel.SIMPLE_COMPARISON); // maxElements = 5000
        
        // Then
        assertThat(DegradationContext.exceedsElementLimit(3000)).isFalse();
        assertThat(DegradationContext.exceedsElementLimit(6000)).isTrue();
        
        assertThat(DegradationContext.getAdjustedSize(3000)).isEqualTo(3000);
        assertThat(DegradationContext.getAdjustedSize(6000)).isEqualTo(5000);
    }
    
    @Test
    @DisplayName("FULL_TRACKING应该不限制元素数量")
    void fullTrackingShouldNotLimitElements() {
        // Given
        DegradationContext.setCurrentLevel(DegradationLevel.FULL_TRACKING); // maxElements = Integer.MAX_VALUE
        
        // Then
        assertThat(DegradationContext.exceedsElementLimit(100000)).isFalse();
        assertThat(DegradationContext.getAdjustedSize(100000)).isEqualTo(100000);
    }
    
    @Test
    @DisplayName("性能监控器应该正确设置和使用")
    void shouldSetAndUsePerformanceMonitorCorrectly() {
        // Given
        DegradationPerformanceMonitor monitor = new DegradationPerformanceMonitor();
        DegradationContext.setPerformanceMonitor(monitor);
        
        // When
        long startTime = System.nanoTime();
        try {
            Thread.sleep(10); // 模拟操作耗时
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        DegradationContext.recordOperationIfEnabled("test", startTime);
        
        // Then
        assertThat(DegradationContext.getPerformanceMonitor()).isSameAs(monitor);
        assertThat(monitor.getTotalOperations()).isEqualTo(1);
        assertThat(monitor.getAverageTime().toMillis()).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("没有性能监控器时记录操作应该安全")
    void shouldSafelyRecordOperationWithoutMonitor() {
        // Given - 没有设置性能监控器
        assertThat(DegradationContext.getPerformanceMonitor()).isNull();
        
        // When & Then - 应该不抛异常
        long startTime = System.nanoTime();
        DegradationContext.recordOperationIfEnabled("test", startTime);
    }
    
    @Test
    @DisplayName("reset应该恢复默认状态")
    void shouldResetToDefaultState() {
        // Given - 修改状态
        DegradationContext.setCurrentLevel(DegradationLevel.DISABLED);
        DegradationContext.setPerformanceMonitor(new DegradationPerformanceMonitor());
        
        // When
        DegradationContext.reset();
        
        // Then
        assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
        assertThat(DegradationContext.getPerformanceMonitor()).isNull();
    }
    
    @Test
    @DisplayName("clear应该清理ThreadLocal变量")
    void shouldClearThreadLocalVariables() {
        // Given - 设置状态
        DegradationContext.setCurrentLevel(DegradationLevel.SUMMARY_ONLY);
        DegradationContext.setPerformanceMonitor(new DegradationPerformanceMonitor());
        
        // When
        DegradationContext.clear();
        
        // Then - clear后应该回到默认状态
        assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.FULL_TRACKING);
        assertThat(DegradationContext.getPerformanceMonitor()).isNull();
    }
    
    @Test
    @DisplayName("setCurrentLevel应该忽略null值")
    void shouldIgnoreNullLevel() {
        // Given
        DegradationLevel originalLevel = DegradationContext.getCurrentLevel();
        
        // When
        DegradationContext.setCurrentLevel(null);
        
        // Then - 级别不应改变
        assertThat(DegradationContext.getCurrentLevel()).isEqualTo(originalLevel);
    }
}