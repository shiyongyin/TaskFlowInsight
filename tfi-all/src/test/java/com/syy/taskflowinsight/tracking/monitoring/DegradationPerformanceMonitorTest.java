package com.syy.taskflowinsight.tracking.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DegradationPerformanceMonitor 单元测试
 * 
 * 验证降级性能监控器的基础功能
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 */
class DegradationPerformanceMonitorTest {
    
    private DegradationPerformanceMonitor performanceMonitor;
    
    @BeforeEach
    void setUp() {
        performanceMonitor = new DegradationPerformanceMonitor();
    }
    
    @Test
    @DisplayName("初始状态应该为空")
    void initialStateShouldBeEmpty() {
        assertThat(performanceMonitor.getAverageTime()).isEqualTo(Duration.ZERO);
        assertThat(performanceMonitor.getSlowOperationCount()).isEqualTo(0);
        assertThat(performanceMonitor.getTotalOperations()).isEqualTo(0);
        assertThat(performanceMonitor.getSlowOperationRate()).isEqualTo(0.0);
        assertThat(performanceMonitor.isPerformanceDegraded()).isFalse();
    }
    
    @Test
    @DisplayName("记录正常操作应该更新统计")
    void recordNormalOperationShouldUpdateStats() {
        // 记录一个正常操作（100ms）
        performanceMonitor.recordOperation(Duration.ofMillis(100));
        
        assertThat(performanceMonitor.getTotalOperations()).isEqualTo(1);
        assertThat(performanceMonitor.getAverageTime()).isEqualTo(Duration.ofMillis(100));
        assertThat(performanceMonitor.getSlowOperationCount()).isEqualTo(0);
        assertThat(performanceMonitor.getSlowOperationRate()).isEqualTo(0.0);
        assertThat(performanceMonitor.isPerformanceDegraded()).isFalse();
    }
    
    @Test
    @DisplayName("记录慢操作应该增加慢操作计数")
    void recordSlowOperationShouldIncrementSlowCount() {
        // 记录一个慢操作（300ms >= 200ms阈值）
        performanceMonitor.recordOperation(Duration.ofMillis(300));
        
        assertThat(performanceMonitor.getTotalOperations()).isEqualTo(1);
        assertThat(performanceMonitor.getAverageTime()).isEqualTo(Duration.ofMillis(300));
        assertThat(performanceMonitor.getSlowOperationCount()).isEqualTo(1);
        assertThat(performanceMonitor.getSlowOperationRate()).isEqualTo(1.0);
        assertThat(performanceMonitor.isPerformanceDegraded()).isTrue(); // 平均时间>150ms
    }
    
    @Test
    @DisplayName("记录多个操作应该正确计算平均值")
    void recordMultipleOperationsShouldCalculateAverageCorrectly() {
        // 记录多个操作：50ms, 100ms, 150ms
        performanceMonitor.recordOperation(Duration.ofMillis(50));
        performanceMonitor.recordOperation(Duration.ofMillis(100));
        performanceMonitor.recordOperation(Duration.ofMillis(150));
        
        assertThat(performanceMonitor.getTotalOperations()).isEqualTo(3);
        assertThat(performanceMonitor.getAverageTime()).isEqualTo(Duration.ofMillis(100)); // (50+100+150)/3 = 100
        assertThat(performanceMonitor.getSlowOperationCount()).isEqualTo(0); // 都小于200ms
        assertThat(performanceMonitor.getSlowOperationRate()).isEqualTo(0.0);
        assertThat(performanceMonitor.isPerformanceDegraded()).isFalse();
    }
    
    @Test
    @DisplayName("慢操作比率超过阈值应该触发性能降级")
    void highSlowOperationRateShouldTriggerDegradation() {
        // 记录10个操作，其中2个慢操作（20% > 5%阈值）
        for (int i = 0; i < 8; i++) {
            performanceMonitor.recordOperation(Duration.ofMillis(100)); // 正常操作
        }
        performanceMonitor.recordOperation(Duration.ofMillis(250)); // 慢操作
        performanceMonitor.recordOperation(Duration.ofMillis(300)); // 慢操作
        
        assertThat(performanceMonitor.getTotalOperations()).isEqualTo(10);
        assertThat(performanceMonitor.getSlowOperationCount()).isEqualTo(2);
        assertThat(performanceMonitor.getSlowOperationRate()).isEqualTo(0.2); // 20%
        assertThat(performanceMonitor.isPerformanceDegraded()).isTrue(); // 慢操作比率>5%
    }
    
    @Test
    @DisplayName("reset应该清除所有统计")
    void resetShouldClearAllStats() {
        // 先记录一些操作
        performanceMonitor.recordOperation(Duration.ofMillis(100));
        performanceMonitor.recordOperation(Duration.ofMillis(300));
        
        // 确认有数据
        assertThat(performanceMonitor.getTotalOperations()).isGreaterThan(0);
        
        // 重置
        performanceMonitor.reset();
        
        // 验证清零
        assertThat(performanceMonitor.getAverageTime()).isEqualTo(Duration.ZERO);
        assertThat(performanceMonitor.getSlowOperationCount()).isEqualTo(0);
        assertThat(performanceMonitor.getTotalOperations()).isEqualTo(0);
        assertThat(performanceMonitor.getSlowOperationRate()).isEqualTo(0.0);
        assertThat(performanceMonitor.isPerformanceDegraded()).isFalse();
    }
    
    @Test
    @DisplayName("recordOperation应该忽略null和负数duration")
    void recordOperationShouldIgnoreInvalidDuration() {
        // 记录null duration
        performanceMonitor.recordOperation(null);
        assertThat(performanceMonitor.getTotalOperations()).isEqualTo(0);
        
        // 记录负数duration
        performanceMonitor.recordOperation(Duration.ofMillis(-100));
        assertThat(performanceMonitor.getTotalOperations()).isEqualTo(0);
        
        // 记录有效duration
        performanceMonitor.recordOperation(Duration.ofMillis(100));
        assertThat(performanceMonitor.getTotalOperations()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("getStatsSummary应该返回完整统计信息")
    void getStatsSummaryShouldReturnCompleteStats() {
        performanceMonitor.recordOperation(Duration.ofMillis(100));
        performanceMonitor.recordOperation(Duration.ofMillis(300)); // 慢操作
        
        String summary = performanceMonitor.getStatsSummary();
        
        assertThat(summary)
            .contains("totalOps=2")
            .contains("avgTime=200ms")
            .contains("slowOps=1")
            .contains("slowRate=50.00%");
    }
    
    @Test
    @DisplayName("isDataFresh初始状态应该为true")
    void isDataFreshShouldBeTrueInitially() {
        // 新创建的监控器数据应该是新鲜的
        assertThat(performanceMonitor.isDataFresh()).isTrue();
    }
}