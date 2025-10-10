package com.syy.taskflowinsight.tracking.monitoring;

import java.time.Duration;

/**
 * 系统指标数据载体
 * 
 * 包含降级决策所需的关键系统指标：
 * - 操作性能指标（平均时长、慢操作计数）
 * - 内存使用指标（使用率、可用内存）
 * - 资源状态指标（CPU、线程数）
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
public record SystemMetrics(
    // 性能指标
    Duration averageOperationTime,     // 平均操作时长
    long slowOperationCount,           // 慢操作计数（>=200ms）
    
    // 内存指标
    double memoryUsagePercent,         // 内存使用率（0-100）
    long availableMemoryMB,            // 可用内存（MB）
    
    // 资源指标
    double cpuUsagePercent,            // CPU使用率（0-100）
    int threadCount,                   // 活跃线程数
    
    // 元数据
    long timestamp                     // 采集时间戳
) {
    
    /**
     * 创建系统指标的构建器
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 系统指标构建器
     */
    public static class Builder {
        private Duration averageOperationTime = Duration.ZERO;
        private long slowOperationCount = 0;
        private double memoryUsagePercent = 0.0;
        private long availableMemoryMB = 0;
        private double cpuUsagePercent = 0.0;
        private int threadCount = 0;
        private long timestamp = System.currentTimeMillis();
        
        public Builder averageOperationTime(Duration averageOperationTime) {
            this.averageOperationTime = averageOperationTime;
            return this;
        }
        
        public Builder slowOperationCount(long slowOperationCount) {
            this.slowOperationCount = slowOperationCount;
            return this;
        }
        
        public Builder memoryUsagePercent(double memoryUsagePercent) {
            this.memoryUsagePercent = memoryUsagePercent;
            return this;
        }
        
        public Builder availableMemoryMB(long availableMemoryMB) {
            this.availableMemoryMB = availableMemoryMB;
            return this;
        }
        
        public Builder cpuUsagePercent(double cpuUsagePercent) {
            this.cpuUsagePercent = cpuUsagePercent;
            return this;
        }
        
        public Builder threadCount(int threadCount) {
            this.threadCount = threadCount;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public SystemMetrics build() {
            return new SystemMetrics(
                averageOperationTime,
                slowOperationCount,
                memoryUsagePercent,
                availableMemoryMB,
                cpuUsagePercent,
                threadCount,
                timestamp
            );
        }
    }
    
    /**
     * 检查是否存在内存压力
     */
    public boolean hasMemoryPressure(double threshold) {
        return memoryUsagePercent >= threshold;
    }
    
    /**
     * 检查是否存在性能压力
     */
    public boolean hasPerformancePressure(Duration threshold) {
        return averageOperationTime.compareTo(threshold) >= 0;
    }
    
    /**
     * 检查慢操作比率是否过高
     */
    public boolean hasHighSlowOperationRate(double maxRate, long totalOperations) {
        if (totalOperations == 0) {
            return false;
        }
        double rate = (double) slowOperationCount / totalOperations;
        return rate >= maxRate;
    }
    
    /**
     * 获取内存压力等级描述
     */
    public String getMemoryPressureLevel() {
        if (memoryUsagePercent >= 90) {
            return "critical";
        } else if (memoryUsagePercent >= 80) {
            return "high";
        } else if (memoryUsagePercent >= 70) {
            return "moderate";
        } else if (memoryUsagePercent >= 60) {
            return "low";
        } else {
            return "normal";
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "SystemMetrics{avgTime=%dms, slowOps=%d, memory=%.1f%%, cpu=%.1f%%, threads=%d}",
            averageOperationTime.toMillis(),
            slowOperationCount,
            memoryUsagePercent,
            cpuUsagePercent,
            threadCount
        );
    }
}