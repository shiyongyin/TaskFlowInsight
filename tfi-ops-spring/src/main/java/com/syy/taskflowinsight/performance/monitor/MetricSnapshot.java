package com.syy.taskflowinsight.performance.monitor;

import lombok.Builder;
import lombok.Data;

/**
 * 性能指标快照 DTO。
 * <p>
 * 记录某一时刻的性能指标数据，使用 Lombok {@code @Builder} 构建。
 * 字段包括：操作数、成功率、延迟分位数（P50/P95/P99）、吞吐量等。
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 3.0.0
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
     * 获取错误率。
     *
     * @return 错误率（0-1），无操作时返回 0
     */
    public double getErrorRate() {
        if (totalOps == 0) return 0;
        return (double) errorOps / totalOps;
    }
    
    /**
     * 获取成功率。
     *
     * @return 成功率（0-1），无操作时返回 0
     */
    public double getSuccessRate() {
        if (totalOps == 0) return 0;
        return (double) successOps / totalOps;
    }
    
    /**
     * 获取吞吐量（基于 P50 计算）。
     *
     * @return 吞吐量（ops/s），P50 为 0 时返回 0
     */
    public double getThroughput() {
        if (p50Micros == 0) return 0;
        return 1_000_000.0 / p50Micros; // 基于P50计算
    }
    
    /**
     * 创建空快照。
     *
     * @param name 指标名称
     * @param timestamp 时间戳（毫秒）
     * @return 全零的空快照
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
     * 格式化输出。
     *
     * @return 包含 ops、success、p50、p95、p99、throughput 的字符串
     */
    public String format() {
        return String.format(
            "%s: ops=%d, success=%.2f%%, p50=%.2fμs, p95=%.2fμs, p99=%.2fμs, throughput=%.0f ops/s",
            name, totalOps, getSuccessRate() * 100, 
            p50Micros, p95Micros, p99Micros, getThroughput()
        );
    }
}