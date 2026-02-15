package com.syy.taskflowinsight.tracking.precision;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PrecisionMetrics单元测试
 * 验证监控指标统计、快照功能、性能监控
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
class PrecisionMetricsTest {
    
    private PrecisionMetrics metrics;
    
    @BeforeEach
    void setUp() {
        metrics = new PrecisionMetrics();
    }
    
    @Test
    @DisplayName("数值比较计数")
    void testNumericComparisonCount() {
        // 初始状态
        assertEquals(0, metrics.getSnapshot().numericComparisonCount);
        
        // 记录比较
        metrics.recordNumericComparison();
        metrics.recordNumericComparison("double");
        metrics.recordNumericComparison("bigdecimal");
        
        assertEquals(3, metrics.getSnapshot().numericComparisonCount);
    }
    
    @Test
    @DisplayName("日期时间比较计数")
    void testDateTimeComparisonCount() {
        // 初始状态
        assertEquals(0, metrics.getSnapshot().dateTimeComparisonCount);
        
        // 记录比较
        metrics.recordDateTimeComparison();
        metrics.recordDateTimeComparison("date");
        metrics.recordDateTimeComparison("instant");
        
        assertEquals(3, metrics.getSnapshot().dateTimeComparisonCount);
    }
    
    @Test
    @DisplayName("容差命中统计")
    void testToleranceHitStatistics() {
        // 初始状态
        PrecisionMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
        assertEquals(0, snapshot.toleranceHitCount);
        assertEquals(0, snapshot.absoluteToleranceHits);
        assertEquals(0, snapshot.relativeToleranceHits);
        assertEquals(0, snapshot.dateToleranceHits);
        
        // 记录各种容差命中
        metrics.recordToleranceHit("absolute", 1e-13);
        metrics.recordToleranceHit("absolute", 5e-13);
        metrics.recordToleranceHit("relative", 1e-10);
        metrics.recordToleranceHit("date", 50L);
        
        snapshot = metrics.getSnapshot();
        assertEquals(4, snapshot.toleranceHitCount);
        assertEquals(2, snapshot.absoluteToleranceHits);
        assertEquals(1, snapshot.relativeToleranceHits);
        assertEquals(1, snapshot.dateToleranceHits);
    }
    
    @Test
    @DisplayName("BigDecimal比较方法统计")
    void testBigDecimalComparisonStats() {
        // 初始状态
        assertEquals(0, metrics.getSnapshot().bigDecimalComparisonCount);
        
        // 记录不同比较方法
        metrics.recordBigDecimalComparison("compareTo");
        metrics.recordBigDecimalComparison("equals");
        metrics.recordBigDecimalComparison("tolerance");
        
        assertEquals(3, metrics.getSnapshot().bigDecimalComparisonCount);
    }
    
    @Test
    @DisplayName("计算时间统计")
    void testCalculationTimeStats() {
        // 初始状态
        PrecisionMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
        assertEquals(0, snapshot.totalCalculationTimeNanos);
        assertEquals(0, snapshot.calculationCount);
        assertEquals(0.0, snapshot.getAverageCalculationTimeMicros(), 0.001);
        
        // 记录计算时间
        metrics.recordCalculationTime(1000L); // 1μs
        metrics.recordCalculationTime(2000L); // 2μs
        metrics.recordCalculationTime(3000L); // 3μs
        
        snapshot = metrics.getSnapshot();
        assertEquals(6000L, snapshot.totalCalculationTimeNanos);
        assertEquals(3, snapshot.calculationCount);
        assertEquals(2.0, snapshot.getAverageCalculationTimeMicros(), 0.001); // 平均2μs
    }
    
    @Test
    @DisplayName("缓存命中率统计")
    void testCacheHitRateStats() {
        // 初始状态
        PrecisionMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
        assertEquals(0, snapshot.precisionCacheHitCount);
        assertEquals(0, snapshot.precisionCacheMissCount);
        assertEquals(0.0, snapshot.getCacheHitRate(), 0.001);
        assertEquals(0, snapshot.getTotalCacheRequests());
        
        // 记录缓存命中和未命中
        metrics.recordCacheHit();
        metrics.recordCacheHit();
        metrics.recordCacheMiss();
        
        snapshot = metrics.getSnapshot();
        assertEquals(2, snapshot.precisionCacheHitCount);
        assertEquals(1, snapshot.precisionCacheMissCount);
        assertEquals(3, snapshot.getTotalCacheRequests());
        assertEquals(2.0/3.0, snapshot.getCacheHitRate(), 0.001); // 66.67%
    }
    
    @Test
    @DisplayName("容差命中率计算")
    void testToleranceHitRateCalculation() {
        // 记录比较和容差命中
        metrics.recordNumericComparison();
        metrics.recordNumericComparison();
        metrics.recordDateTimeComparison();
        metrics.recordDateTimeComparison();
        // 总计4次比较
        
        metrics.recordToleranceHit("absolute", 1e-13);
        metrics.recordToleranceHit("relative", 1e-10);
        // 总计2次容差命中
        
        PrecisionMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
        assertEquals(0.5, snapshot.getToleranceHitRate(), 0.001); // 50%命中率
    }
    
    @Test
    @DisplayName("指标重置功能")
    void testMetricsReset() {
        // 记录一些指标
        metrics.recordNumericComparison();
        metrics.recordDateTimeComparison();
        metrics.recordToleranceHit("absolute", 1e-13);
        metrics.recordBigDecimalComparison("compareTo");
        metrics.recordCacheHit();
        metrics.recordCalculationTime(1000L);
        
        // 验证指标已记录
        PrecisionMetrics.MetricsSnapshot beforeReset = metrics.getSnapshot();
        assertTrue(beforeReset.numericComparisonCount > 0);
        assertTrue(beforeReset.dateTimeComparisonCount > 0);
        assertTrue(beforeReset.toleranceHitCount > 0);
        
        // 重置指标
        metrics.reset();
        
        // 验证所有指标已重置为0
        PrecisionMetrics.MetricsSnapshot afterReset = metrics.getSnapshot();
        assertEquals(0, afterReset.numericComparisonCount);
        assertEquals(0, afterReset.dateTimeComparisonCount);
        assertEquals(0, afterReset.toleranceHitCount);
        assertEquals(0, afterReset.bigDecimalComparisonCount);
        assertEquals(0, afterReset.precisionCacheHitCount);
        assertEquals(0, afterReset.precisionCacheMissCount);
        assertEquals(0, afterReset.totalCalculationTimeNanos);
        assertEquals(0, afterReset.calculationCount);
    }
    
    @Test
    @DisplayName("指标摘要日志功能")
    void testLogSummary() {
        // 记录各种指标
        metrics.recordNumericComparison("float");
        metrics.recordNumericComparison("bigdecimal");
        metrics.recordDateTimeComparison("date");
        metrics.recordToleranceHit("absolute", 1e-13);
        metrics.recordToleranceHit("relative", 1e-10);
        metrics.recordToleranceHit("date", 100L);
        metrics.recordBigDecimalComparison("compareTo");
        metrics.recordCacheHit();
        metrics.recordCacheHit();
        metrics.recordCacheMiss();
        metrics.recordCalculationTime(1500L);
        metrics.recordCalculationTime(2500L);
        
        // 调用日志摘要（不会抛异常）
        assertDoesNotThrow(() -> metrics.logSummary());
        
        // 验证数据正确性
        PrecisionMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
        assertEquals(2, snapshot.numericComparisonCount);
        assertEquals(1, snapshot.dateTimeComparisonCount);
        assertEquals(3, snapshot.toleranceHitCount);
        assertEquals(1, snapshot.absoluteToleranceHits);
        assertEquals(1, snapshot.relativeToleranceHits);
        assertEquals(1, snapshot.dateToleranceHits);
        assertEquals(1, snapshot.bigDecimalComparisonCount);
        assertEquals(2.0/3.0, snapshot.getCacheHitRate(), 0.001);
        assertEquals(2.0, snapshot.getAverageCalculationTimeMicros(), 0.001);
    }
    
    @Test
    @DisplayName("边界情况处理")
    void testEdgeCases() {
        // 空快照的计算
        PrecisionMetrics.MetricsSnapshot emptySnapshot = metrics.getSnapshot();
        assertEquals(0.0, emptySnapshot.getCacheHitRate());
        assertEquals(0.0, emptySnapshot.getAverageCalculationTimeMicros());
        assertEquals(0.0, emptySnapshot.getToleranceHitRate());
        
        // 只有命中没有未命中
        metrics.recordCacheHit();
        metrics.recordCacheHit();
        PrecisionMetrics.MetricsSnapshot hitOnlySnapshot = metrics.getSnapshot();
        assertEquals(1.0, hitOnlySnapshot.getCacheHitRate(), 0.001);
        
        // 只有比较没有容差命中
        metrics.reset();
        metrics.recordNumericComparison();
        metrics.recordDateTimeComparison();
        PrecisionMetrics.MetricsSnapshot noHitSnapshot = metrics.getSnapshot();
        assertEquals(0.0, noHitSnapshot.getToleranceHitRate(), 0.001);
    }
    
    @Test
    @DisplayName("并发安全性测试")
    void testConcurrentAccess() throws InterruptedException {
        // 创建多个线程同时操作指标
        Thread[] threads = new Thread[10];
        
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    metrics.recordNumericComparison();
                    metrics.recordDateTimeComparison();
                    metrics.recordToleranceHit("absolute", 1e-13);
                    metrics.recordCacheHit();
                    metrics.recordCalculationTime(1000L);
                }
            });
        }
        
        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 验证结果
        PrecisionMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
        assertEquals(1000, snapshot.numericComparisonCount); // 10 threads * 100 iterations
        assertEquals(1000, snapshot.dateTimeComparisonCount);
        assertEquals(1000, snapshot.toleranceHitCount);
        assertEquals(1000, snapshot.precisionCacheHitCount);
        assertEquals(1000, snapshot.calculationCount);
    }
}