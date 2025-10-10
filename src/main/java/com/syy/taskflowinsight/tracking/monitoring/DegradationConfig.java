package com.syy.taskflowinsight.tracking.monitoring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.Map;

/**
 * 降级机制配置类
 * 
 * 配置降级相关的阈值、评估周期、滞后参数等
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
@ConfigurationProperties(prefix = "tfi.change-tracking.degradation")
@Validated
public record DegradationConfig(
    
    /** 是否启用降级机制 */
    @NotNull Boolean enabled,
    
    /** 评估周期 */
    @NotNull Duration evaluationInterval,
    
    /** 级别变更最小间隔（滞后机制） */
    @NotNull Duration minLevelChangeDuration,
    
    /** 指标缓存时间 */
    @NotNull Duration metricsCacheTime,
    
    /** 慢操作阈值（毫秒） */
    @Min(50) @Max(5000) Long slowOperationThresholdMs,
    
    /** 临界内存阈值（百分比） */
    @Min(50) @Max(100) Double criticalMemoryThreshold,
    
    /** 临界操作时间（毫秒） */
    @Min(100) @Max(10000) Long criticalOperationTimeMs,
    
    /** 内存阈值配置 */
    @NotNull MemoryThresholds memoryThresholds,
    
    /** 性能阈值配置 */
    @NotNull PerformanceThresholds performanceThresholds,
    
    /** 列表大小阈值 */
    @Min(100) @Max(10000) Integer listSizeThreshold,
    
    /** 最大候选项数量 */
    @Min(1) @Max(50) Integer maxCandidates,
    
    /** K对数阈值 */
    @Min(1000) @Max(100000) Integer kPairsThreshold
    
) {
    
    // 默认值构造函数
    public DegradationConfig {
        enabled = enabled != null ? enabled : false;
        evaluationInterval = evaluationInterval != null ? evaluationInterval : Duration.ofSeconds(5);
        minLevelChangeDuration = minLevelChangeDuration != null ? minLevelChangeDuration : Duration.ofSeconds(30);
        metricsCacheTime = metricsCacheTime != null ? metricsCacheTime : Duration.ofSeconds(10);
        slowOperationThresholdMs = slowOperationThresholdMs != null ? slowOperationThresholdMs : 200L;
        criticalMemoryThreshold = criticalMemoryThreshold != null ? criticalMemoryThreshold : 90.0;
        criticalOperationTimeMs = criticalOperationTimeMs != null ? criticalOperationTimeMs : 1000L;
        memoryThresholds = memoryThresholds != null ? memoryThresholds : new MemoryThresholds(null, null, null, null);
        performanceThresholds = performanceThresholds != null ? performanceThresholds : new PerformanceThresholds(null, null, null);
        listSizeThreshold = listSizeThreshold != null ? listSizeThreshold : 500;
        maxCandidates = maxCandidates != null ? maxCandidates : 5;
        kPairsThreshold = kPairsThreshold != null ? kPairsThreshold : 10000;
    }
    
    /**
     * 内存阈值配置
     */
    public record MemoryThresholds(
        /** 跳过深度分析的内存阈值 */
        Double skipDeepAnalysis,
        
        /** 简单比较的内存阈值 */
        Double simpleComparison,
        
        /** 仅摘要的内存阈值 */
        Double summaryOnly,
        
        /** 禁用的内存阈值 */
        Double disabled
    ) {
        public MemoryThresholds {
            skipDeepAnalysis = skipDeepAnalysis != null ? skipDeepAnalysis : 60.0;
            simpleComparison = simpleComparison != null ? simpleComparison : 70.0;
            summaryOnly = summaryOnly != null ? summaryOnly : 80.0;
            disabled = disabled != null ? disabled : 90.0;
        }
        
        /**
         * 根据内存使用率确定降级级别
         */
        public DegradationLevel getDegradationLevelForMemory(double memoryUsagePercent) {
            if (memoryUsagePercent >= disabled) {
                return DegradationLevel.DISABLED;
            } else if (memoryUsagePercent >= summaryOnly) {
                return DegradationLevel.SUMMARY_ONLY;
            } else if (memoryUsagePercent >= simpleComparison) {
                return DegradationLevel.SIMPLE_COMPARISON;
            } else if (memoryUsagePercent >= skipDeepAnalysis) {
                return DegradationLevel.SKIP_DEEP_ANALYSIS;
            } else {
                return DegradationLevel.FULL_TRACKING;
            }
        }
    }
    
    /**
     * 性能阈值配置
     */
    public record PerformanceThresholds(
        /** 平均操作时间阈值（毫秒） */
        Long averageOperationTimeMs,
        
        /** 慢操作比率阈值 */
        Double slowOperationRate,
        
        /** CPU使用率阈值 */
        Double cpuUsagePercent
    ) {
        public PerformanceThresholds {
            averageOperationTimeMs = averageOperationTimeMs != null ? averageOperationTimeMs : 200L;
            slowOperationRate = slowOperationRate != null ? slowOperationRate : 0.05;
            cpuUsagePercent = cpuUsagePercent != null ? cpuUsagePercent : 80.0;
        }
    }
    
    /**
     * 检查是否应该基于列表大小进行降级
     */
    public boolean shouldDegradeForListSize(int listSize) {
        return listSize > listSizeThreshold;
    }
    
    /**
     * 检查是否应该基于K对数进行降级
     */
    public boolean shouldDegradeForKPairs(int kPairs) {
        return kPairs > kPairsThreshold;
    }
    
    /**
     * 获取慢操作阈值Duration
     */
    public Duration getSlowOperationThreshold() {
        return Duration.ofMillis(slowOperationThresholdMs);
    }
    
    /**
     * 获取临界操作时间Duration
     */
    public Duration getCriticalOperationTime() {
        return Duration.ofMillis(criticalOperationTimeMs);
    }
    
    /**
     * 检查操作是否为慢操作
     */
    public boolean isSlowOperation(Duration operationTime) {
        return operationTime.toMillis() >= slowOperationThresholdMs;
    }
    
    /**
     * 检查内存使用率是否临界
     */
    public boolean isCriticalMemoryUsage(double memoryUsagePercent) {
        return memoryUsagePercent >= criticalMemoryThreshold;
    }
    
    @Override
    public String toString() {
        return String.format(
            "DegradationConfig{enabled=%s, evaluationInterval=%s, listThreshold=%d, memoryThresholds=%s}",
            enabled, evaluationInterval, listSizeThreshold, memoryThresholds
        );
    }
}