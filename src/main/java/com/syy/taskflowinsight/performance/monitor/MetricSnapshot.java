package com.syy.taskflowinsight.performance.monitor;

import lombok.Builder;
import lombok.Data;

/**
 * 性能指标快照
 * 记录某一时刻的性能指标数据
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.1
 * @since 2025-01-13
 */
@Data
@Builder
public class MetricSnapshot {
    
    private String name;
    private long timestamp;
    private long totalOps;
    private long successOps;
    private long errorOps;
    private double minMicros;
    private double maxMicros;
    private double p50Micros;
    private double p95Micros;
    private double p99Micros;
    
    /**
     * 获取错误率
     */
    public double getErrorRate() {
        if (totalOps == 0) return 0;
        return (double) errorOps / totalOps;
    }
    
    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        if (totalOps == 0) return 0;
        return (double) successOps / totalOps;
    }
    
    /**
     * 获取吞吐量（ops/s）
     */
    public double getThroughput() {
        if (p50Micros == 0) return 0;
        return 1_000_000.0 / p50Micros; // 基于P50计算
    }
    
    /**
     * 创建空快照
     */
    public static MetricSnapshot empty(String name, long timestamp) {
        return builder()
            .name(name)
            .timestamp(timestamp)
            .totalOps(0)
            .successOps(0)
            .errorOps(0)
            .minMicros(0)
            .maxMicros(0)
            .p50Micros(0)
            .p95Micros(0)
            .p99Micros(0)
            .build();
    }
    
    /**
     * 格式化输出
     */
    public String format() {
        return String.format(
            "%s: ops=%d, success=%.2f%%, p50=%.2fμs, p95=%.2fμs, p99=%.2fμs, throughput=%.0f ops/s",
            name, totalOps, getSuccessRate() * 100, 
            p50Micros, p95Micros, p99Micros, getThroughput()
        );
    }
}