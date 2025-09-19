package com.syy.taskflowinsight.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TfiMetrics单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("TfiMetrics单元测试")
public class TfiMetricsTest {
    
    private TfiMetrics metrics;
    private MeterRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new TfiMetrics(Optional.of(registry));
    }
    
    @Test
    @DisplayName("记录变更追踪操作")
    void testRecordChangeTracking() {
        // 记录几次操作
        metrics.recordChangeTracking(1000000); // 1ms
        metrics.recordChangeTracking(2000000); // 2ms
        metrics.recordChangeTracking(1500000); // 1.5ms
        
        MetricsSummary summary = metrics.getSummary();
        
        assertThat(summary.getChangeTrackingCount()).isEqualTo(3);
        assertThat(summary.getAvgChangeTrackingTime()).isNotNull();
        assertThat(summary.getAvgChangeTrackingTime().toNanos()).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("记录快照创建")
    void testRecordSnapshotCreation() {
        metrics.recordSnapshotCreation(5000000); // 5ms
        metrics.recordSnapshotCreation(3000000); // 3ms
        
        MetricsSummary summary = metrics.getSummary();
        
        assertThat(summary.getSnapshotCreationCount()).isEqualTo(2);
        assertThat(summary.getAvgSnapshotCreationTime()).isNotNull();
    }
    
    @Test
    @DisplayName("记录路径匹配与缓存命中")
    void testRecordPathMatch() {
        // 记录几次匹配，其中一些是缓存命中
        metrics.recordPathMatch(100000, false); // 0.1ms, miss
        metrics.recordPathMatch(50000, true);   // 0.05ms, hit
        metrics.recordPathMatch(30000, true);   // 0.03ms, hit
        metrics.recordPathMatch(200000, false); // 0.2ms, miss
        metrics.recordPathMatch(20000, true);   // 0.02ms, hit
        
        MetricsSummary summary = metrics.getSummary();
        
        assertThat(summary.getPathMatchCount()).isEqualTo(5);
        assertThat(metrics.getPathMatchHitRate()).isEqualTo(0.6); // 3/5 = 60%
    }
    
    @Test
    @DisplayName("记录集合摘要生成")
    void testRecordCollectionSummary() {
        metrics.recordCollectionSummary(1000000, 100);  // 1ms, 100 items
        metrics.recordCollectionSummary(2000000, 500);  // 2ms, 500 items
        metrics.recordCollectionSummary(1500000, 1000); // 1.5ms, 1000 items
        
        MetricsSummary summary = metrics.getSummary();
        
        assertThat(summary.getCollectionSummaryCount()).isEqualTo(3);
        assertThat(summary.getAvgCollectionSummaryTime()).isNotNull();
    }
    
    @Test
    @DisplayName("记录错误")
    void testRecordError() {
        metrics.recordError("NullPointerException");
        metrics.recordError("IllegalArgumentException");
        metrics.recordError("NullPointerException");
        
        MetricsSummary summary = metrics.getSummary();
        
        assertThat(summary.getErrorCount()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("记录自定义指标")
    void testRecordCustomMetric() {
        metrics.recordCustomMetric("test.latency", 100.5);
        metrics.recordCustomMetric("test.latency", 200.3);
        metrics.recordCustomMetric("test.size", 1024);
        
        // 验证指标被记录（通过registry）
        assertThat(registry.find("tfi.custom.test.latency.total").summary()).isNotNull();
        assertThat(registry.find("tfi.custom.test.size.total").summary()).isNotNull();
    }
    
    @Test
    @DisplayName("增加自定义计数器")
    void testIncrementCustomCounter() {
        metrics.incrementCustomCounter("requests");
        metrics.incrementCustomCounter("requests");
        metrics.incrementCustomCounter("requests");
        metrics.incrementCustomCounter("failures");
        
        // 验证计数器值
        assertThat(registry.find("tfi.custom.counter.requests.total").gauge()).isNotNull();
        assertThat(registry.find("tfi.custom.counter.requests.total").gauge().value()).isEqualTo(3.0);
        assertThat(registry.find("tfi.custom.counter.failures.total").gauge().value()).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("计时执行")
    void testTimeExecution() {
        // 测试有返回值的执行
        String result = metrics.timeExecution("test.operation", () -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "completed";
        });
        
        assertThat(result).isEqualTo("completed");
        assertThat(registry.find("tfi.execution.test.operation.seconds").timer()).isNotNull();
        
        // 测试无返回值的执行
        metrics.timeExecution("test.void.operation", () -> {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        assertThat(registry.find("tfi.execution.test.void.operation.seconds").timer()).isNotNull();
    }
    
    @Test
    @DisplayName("计算健康分数")
    void testHealthScore() {
        // 初始健康分数应该是100
        MetricsSummary summary = metrics.getSummary();
        assertThat(summary.getHealthScore()).isEqualTo(100.0);
        
        // 添加一些成功操作
        metrics.recordChangeTracking(1000000);
        metrics.recordChangeTracking(2000000);
        metrics.recordPathMatch(100000, true);
        metrics.recordPathMatch(200000, true);
        
        // 健康分数应该仍然很高
        summary = metrics.getSummary();
        assertThat(summary.getHealthScore()).isGreaterThan(90.0);
        
        // 添加错误
        metrics.recordError("TestError");
        metrics.recordError("AnotherError");
        
        // 健康分数应该下降
        summary = metrics.getSummary();
        assertThat(summary.getHealthScore()).isLessThan(90.0);
    }
    
    @Test
    @DisplayName("获取平均处理时间")
    void testGetAverageProcessingTime() {
        // 记录一些操作
        metrics.recordChangeTracking(1000000); // 1ms
        metrics.recordChangeTracking(2000000); // 2ms
        metrics.recordChangeTracking(3000000); // 3ms
        
        Duration avgTime = metrics.getAverageProcessingTime("change.tracking");
        
        assertThat(avgTime).isNotNull();
        assertThat(avgTime.toMillis()).isGreaterThanOrEqualTo(1);
        assertThat(avgTime.toMillis()).isLessThanOrEqualTo(3);
    }
    
    @Test
    @DisplayName("获取指标摘要")
    void testGetSummary() {
        // 记录各种操作
        metrics.recordChangeTracking(1000000);
        metrics.recordSnapshotCreation(2000000);
        metrics.recordPathMatch(100000, true);
        metrics.recordCollectionSummary(500000, 100);
        metrics.recordError("TestError");
        
        MetricsSummary summary = metrics.getSummary();
        
        assertThat(summary).isNotNull();
        assertThat(summary.getChangeTrackingCount()).isEqualTo(1);
        assertThat(summary.getSnapshotCreationCount()).isEqualTo(1);
        assertThat(summary.getPathMatchCount()).isEqualTo(1);
        assertThat(summary.getCollectionSummaryCount()).isEqualTo(1);
        assertThat(summary.getErrorCount()).isEqualTo(1);
        assertThat(summary.getHealthScore()).isLessThan(100.0);
    }
    
    @Test
    @DisplayName("重置自定义指标")
    void testReset() {
        // 添加自定义计数器
        metrics.incrementCustomCounter("test");
        metrics.incrementCustomCounter("test");
        
        // 重置
        metrics.reset();
        
        // 自定义指标应该被清理
        // 注意：标准指标（计数器）不会被重置
        MetricsSummary summary = metrics.getSummary();
        assertThat(summary).isNotNull();
    }
}