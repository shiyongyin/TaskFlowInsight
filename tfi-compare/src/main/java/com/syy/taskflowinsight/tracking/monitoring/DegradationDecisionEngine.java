package com.syy.taskflowinsight.tracking.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 降级决策引擎
 * 
 * 根据系统指标计算最优降级级别，实现多维度决策：
 * 1. 内存压力优先 (主要因子)
 * 2. 性能压力 (平均时长、慢操作比率)
 * 3. 资源压力 (CPU、线程数)
 * 4. 列表大小 (与CT-003协同)
 * 
 * 决策原则：安全优先，选择更严格的级别
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@Component
@ConditionalOnProperty(prefix = "tfi.change-tracking.degradation", name = "enabled", havingValue = "true")
public class DegradationDecisionEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(DegradationDecisionEngine.class);
    
    private final DegradationConfig config;
    
    public DegradationDecisionEngine(DegradationConfig config) {
        this.config = config;
    }
    
    /**
     * 计算最优降级级别
     * 
     * @param metrics 当前系统指标
     * @param performanceMonitor 性能监控器（用于获取详细性能数据）
     * @return 推荐的降级级别
     */
    public DegradationLevel calculateOptimalLevel(SystemMetrics metrics, DegradationPerformanceMonitor performanceMonitor) {
        // 收集各维度的降级建议
        List<DegradationDecision> decisions = Arrays.asList(
            evaluateMemoryPressure(metrics),
            evaluatePerformancePressure(metrics, performanceMonitor),
            evaluateResourcePressure(metrics),
            evaluateSystemLoad(metrics)
        );
        
        // 选择最严格的降级级别（安全优先）
        DegradationLevel finalLevel = decisions.stream()
            .map(DegradationDecision::recommendedLevel)
            .reduce(DegradationLevel.FULL_TRACKING, (level1, level2) -> 
                level1.isMoreRestrictiveThan(level2) ? level1 : level2
            );
        
        // 记录决策过程（Debug模式）
        if (logger.isDebugEnabled()) {
            logDecisionProcess(metrics, decisions, finalLevel);
        }
        
        return finalLevel;
    }
    
    /**
     * 评估内存压力 (主要因子)
     */
    private DegradationDecision evaluateMemoryPressure(SystemMetrics metrics) {
        double memoryUsage = metrics.memoryUsagePercent();
        DegradationLevel level = config.memoryThresholds().getDegradationLevelForMemory(memoryUsage);
        
        String reason = String.format("memory_usage=%.1f%%", memoryUsage);
        return new DegradationDecision("memory_pressure", level, reason, 1.0); // 最高权重
    }
    
    /**
     * 评估性能压力
     */
    private DegradationDecision evaluatePerformancePressure(SystemMetrics metrics, DegradationPerformanceMonitor performanceMonitor) {
        long avgTimeMs = metrics.averageOperationTime().toMillis();
        double slowOpRate = performanceMonitor != null ? performanceMonitor.getSlowOperationRate() : 0.0;
        
        DegradationLevel level;
        String reason;
        
        // 严重性能问题：平均时间>1000ms或慢操作率>30%
        if (avgTimeMs > 1000 || slowOpRate > 0.30) {
            level = DegradationLevel.DISABLED;
            reason = String.format("severe_perf(avgTime=%dms,slowRate=%.1f%%)", avgTimeMs, slowOpRate * 100);
        }
        // 高性能压力：平均时间>150ms或慢操作率>25%
        else if (avgTimeMs > 150 || slowOpRate > 0.25) {
            level = DegradationLevel.SUMMARY_ONLY;
            reason = String.format("high_perf(avgTime=%dms,slowRate=%.1f%%)", avgTimeMs, slowOpRate * 100);
        }
        // 中等性能压力：平均时间>300ms或慢操作率>15%
        else if (avgTimeMs > 300 || slowOpRate > 0.15) {
            level = DegradationLevel.SIMPLE_COMPARISON;
            reason = String.format("medium_perf(avgTime=%dms,slowRate=%.1f%%)", avgTimeMs, slowOpRate * 100);
        }
        // 轻微性能压力：平均时间>200ms
        else if (avgTimeMs > config.performanceThresholds().averageOperationTimeMs()) {
            level = DegradationLevel.SKIP_DEEP_ANALYSIS;
            reason = String.format("light_perf(avgTime=%dms)", avgTimeMs);
        }
        else {
            level = DegradationLevel.FULL_TRACKING;
            reason = String.format("good_perf(avgTime=%dms,slowRate=%.1f%%)", avgTimeMs, slowOpRate * 100);
        }
        
        return new DegradationDecision("performance_pressure", level, reason, 0.8); // 高权重
    }
    
    /**
     * 评估资源压力 (CPU、线程)
     */
    private DegradationDecision evaluateResourcePressure(SystemMetrics metrics) {
        double cpuUsage = metrics.cpuUsagePercent();
        int threadCount = metrics.threadCount();
        
        DegradationLevel level;
        String reason;
        
        // CPU使用率过高
        if (cpuUsage >= 95) {
            level = DegradationLevel.DISABLED;
            reason = String.format("critical_cpu=%.1f%%", cpuUsage);
        }
        else if (cpuUsage >= 85) {
            level = DegradationLevel.SUMMARY_ONLY;
            reason = String.format("high_cpu=%.1f%%", cpuUsage);
        }
        else if (cpuUsage >= 75) {
            level = DegradationLevel.SIMPLE_COMPARISON;
            reason = String.format("medium_cpu=%.1f%%", cpuUsage);
        }
        // 线程数过多 (简单估算：>1000个线程可能有问题)
        else if (threadCount > 1000) {
            level = DegradationLevel.SKIP_DEEP_ANALYSIS;
            reason = String.format("high_threads=%d", threadCount);
        }
        else {
            level = DegradationLevel.FULL_TRACKING;
            reason = String.format("good_resources(cpu=%.1f%%,threads=%d)", cpuUsage, threadCount);
        }
        
        return new DegradationDecision("resource_pressure", level, reason, 0.6); // 中等权重
    }
    
    /**
     * 评估系统整体负载
     */
    private DegradationDecision evaluateSystemLoad(SystemMetrics metrics) {
        long availableMemMB = metrics.availableMemoryMB();
        
        DegradationLevel level;
        String reason;
        
        // 可用内存过低 (<100MB)
        if (availableMemMB < 100) {
            level = DegradationLevel.SUMMARY_ONLY;
            reason = String.format("low_available_mem=%dMB", availableMemMB);
        }
        // 可用内存较低 (<500MB)
        else if (availableMemMB < 500) {
            level = DegradationLevel.SKIP_DEEP_ANALYSIS;
            reason = String.format("medium_available_mem=%dMB", availableMemMB);
        }
        else {
            level = DegradationLevel.FULL_TRACKING;
            reason = String.format("sufficient_mem=%dMB", availableMemMB);
        }
        
        return new DegradationDecision("system_load", level, reason, 0.4); // 较低权重
    }
    
    /**
     * 检查是否应该基于列表大小进行降级 (与CT-003协同)
     */
    public boolean shouldDegradeForListSize(int listSize) {
        return config.shouldDegradeForListSize(listSize);
    }
    
    /**
     * 检查是否应该基于K对数进行降级 (与CT-003协同)
     */
    public boolean shouldDegradeForKPairs(int kPairs) {
        return config.shouldDegradeForKPairs(kPairs);
    }
    
    /**
     * 记录决策过程
     */
    private void logDecisionProcess(SystemMetrics metrics, List<DegradationDecision> decisions, DegradationLevel finalLevel) {
        StringBuilder sb = new StringBuilder("Degradation decision process:\n");
        sb.append(String.format("  Metrics: %s\n", metrics));
        sb.append("  Factor decisions:\n");
        
        for (DegradationDecision decision : decisions) {
            sb.append(String.format("    %s: %s (weight=%.1f) -> %s\n", 
                decision.factor(), decision.reason(), decision.weight(), decision.recommendedLevel()));
        }
        
        sb.append(String.format("  Final level: %s", finalLevel));
        logger.debug(sb.toString());
    }
    
    /**
     * 降级决策记录
     */
    private record DegradationDecision(
        String factor,                  // 决策因子名称
        DegradationLevel recommendedLevel, // 推荐级别
        String reason,                  // 决策原因
        double weight                   // 权重 (0.0-1.0)
    ) {}
}