package com.syy.taskflowinsight.tracking.monitoring;

import com.syy.taskflowinsight.metrics.TfiMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 降级性能监控器
 * 
 * 跟踪操作性能指标：
 * - 操作计数和总耗时
 * - 慢操作计数（>=200ms）
 * - 平均操作时长计算
 * 
 * 线程安全设计，使用无锁数据结构
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@Component("degradationPerformanceMonitor")
@ConditionalOnProperty(prefix = "tfi.change-tracking.degradation", name = "enabled", havingValue = "true")
public class DegradationPerformanceMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(DegradationPerformanceMonitor.class);
    
    /** 慢操作阈值（毫秒） */
    private static final long SLOW_OPERATION_THRESHOLD_MS = 200;
    
    // 性能计数器（线程安全）
    private final LongAdder totalOperations = new LongAdder();
    private final LongAdder totalDurationMs = new LongAdder();
    private final LongAdder slowOperationCount = new LongAdder();
    
    // 最后更新时间（用于计算平均值的有效性）
    private final AtomicLong lastUpdateTime = new AtomicLong(System.currentTimeMillis());
    
    // 可选的TfiMetrics注入（用于记录慢操作）
    @Autowired(required = false)
    private TfiMetrics tfiMetrics;
    
    /**
     * 记录一次操作的耗时
     * 
     * @param duration 操作耗时
     */
    public void recordOperation(Duration duration) {
        if (duration == null || duration.isNegative()) {
            logger.debug("Invalid operation duration: {}", duration);
            return;
        }
        
        long durationMs = duration.toMillis();
        
        // 更新计数器
        totalOperations.increment();
        totalDurationMs.add(durationMs);
        lastUpdateTime.set(System.currentTimeMillis());
        
        // 检查是否为慢操作
        if (durationMs >= SLOW_OPERATION_THRESHOLD_MS) {
            slowOperationCount.increment();
            logger.debug("Slow operation detected: {}ms", durationMs);
            
            // 直接记录单次慢操作到TfiMetrics
            if (tfiMetrics != null) {
                tfiMetrics.recordSlowOperation("change_tracking", durationMs);
            }
        }
    }
    
    /**
     * 获取平均操作时长
     * 
     * @return 平均时长，如果没有操作记录则返回Duration.ZERO
     */
    public Duration getAverageTime() {
        long totalOps = totalOperations.sum();
        if (totalOps == 0) {
            return Duration.ZERO;
        }
        
        long avgMs = totalDurationMs.sum() / totalOps;
        return Duration.ofMillis(avgMs);
    }
    
    /**
     * 获取慢操作计数
     * 
     * @return 慢操作数量
     */
    public long getSlowOperationCount() {
        return slowOperationCount.sum();
    }
    
    /**
     * 获取总操作数
     * 
     * @return 总操作计数
     */
    public long getTotalOperations() {
        return totalOperations.sum();
    }
    
    /**
     * 获取慢操作比率
     * 
     * @return 慢操作比率（0.0-1.0），如果没有操作则返回0.0
     */
    public double getSlowOperationRate() {
        long totalOps = totalOperations.sum();
        if (totalOps == 0) {
            return 0.0;
        }
        
        return (double) slowOperationCount.sum() / totalOps;
    }
    
    /**
     * 判断是否存在性能降级风险
     * 
     * @return true 如果平均操作时间超过阈值或慢操作比率过高
     */
    public boolean isPerformanceDegraded() {
        Duration avgTime = getAverageTime();
        double slowRate = getSlowOperationRate();
        
        // 平均时间超过150ms或慢操作比率超过5%
        return avgTime.toMillis() >= 150 || slowRate >= 0.05;
    }
    
    /**
     * 重置所有计数器
     * 注意：此操作会丢失历史数据，谨慎使用
     */
    public void reset() {
        totalOperations.reset();
        totalDurationMs.reset();
        slowOperationCount.reset();
        lastUpdateTime.set(System.currentTimeMillis());
        
        logger.info("Performance monitor reset");
    }
    
    /**
     * 获取监控统计摘要
     * 
     * @return 性能统计信息
     */
    public String getStatsSummary() {
        long totalOps = totalOperations.sum();
        Duration avgTime = getAverageTime();
        long slowOps = slowOperationCount.sum();
        double slowRate = getSlowOperationRate();
        
        return String.format(
            "PerformanceStats{totalOps=%d, avgTime=%dms, slowOps=%d, slowRate=%.2f%%}",
            totalOps, avgTime.toMillis(), slowOps, slowRate * 100
        );
    }
    
    /**
     * 检查数据是否新鲜（最近5分钟内有更新）
     * 
     * @return true 如果数据是新鲜的
     */
    public boolean isDataFresh() {
        long lastUpdate = lastUpdateTime.get();
        long now = System.currentTimeMillis();
        return (now - lastUpdate) < Duration.ofMinutes(5).toMillis();
    }
}