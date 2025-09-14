package com.syy.taskflowinsight.performance;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 基准测试结果
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Data
@Builder
public class BenchmarkResult {
    
    private String name;
    private long count;
    private double minNanos;
    private double maxNanos;
    private double meanNanos;
    private double medianNanos;
    private double p50Nanos;
    private double p95Nanos;
    private double p99Nanos;
    private double stdDevNanos;
    private double throughput; // ops/sec
    private String status; // SUCCESS, SKIPPED, ERROR
    private String message;
    
    /**
     * 从持续时间列表创建结果
     */
    public static BenchmarkResult fromDurations(String name, List<Long> durations) {
        if (durations == null || durations.isEmpty()) {
            return error(name, "No data");
        }
        
        Collections.sort(durations);
        
        long count = durations.size();
        double min = durations.get(0);
        double max = durations.get(durations.size() - 1);
        
        // 计算平均值
        double sum = 0;
        for (long d : durations) {
            sum += d;
        }
        double mean = sum / count;
        
        // 计算中位数
        double median = durations.get(durations.size() / 2);
        
        // 计算百分位数
        double p50 = durations.get((int) (count * 0.50));
        double p95 = durations.get((int) (count * 0.95));
        double p99 = durations.get((int) (count * 0.99));
        
        // 计算标准差
        double variance = 0;
        for (long d : durations) {
            variance += Math.pow(d - mean, 2);
        }
        double stdDev = Math.sqrt(variance / count);
        
        // 计算吞吐量
        double throughput = 1_000_000_000.0 / mean; // ops/sec
        
        return BenchmarkResult.builder()
            .name(name)
            .count(count)
            .minNanos(min)
            .maxNanos(max)
            .meanNanos(mean)
            .medianNanos(median)
            .p50Nanos(p50)
            .p95Nanos(p95)
            .p99Nanos(p99)
            .stdDevNanos(stdDev)
            .throughput(throughput)
            .status("SUCCESS")
            .build();
    }
    
    /**
     * 创建跳过的结果
     */
    public static BenchmarkResult skipped(String message) {
        return BenchmarkResult.builder()
            .status("SKIPPED")
            .message(message)
            .build();
    }
    
    /**
     * 创建错误结果
     */
    public static BenchmarkResult error(String name, String message) {
        return BenchmarkResult.builder()
            .name(name)
            .status("ERROR")
            .message(message)
            .build();
    }
    
    /**
     * 格式化输出
     */
    public String format() {
        if ("SKIPPED".equals(status)) {
            return String.format("%-20s: SKIPPED - %s", name, message);
        }
        
        if ("ERROR".equals(status)) {
            return String.format("%-20s: ERROR - %s", name, message);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s:\n", name));
        sb.append(String.format("  Samples: %d\n", count));
        sb.append(String.format("  Min:     %.2f μs\n", minNanos / 1000));
        sb.append(String.format("  Max:     %.2f μs\n", maxNanos / 1000));
        sb.append(String.format("  Mean:    %.2f μs (±%.2f)\n", meanNanos / 1000, stdDevNanos / 1000));
        sb.append(String.format("  Median:  %.2f μs\n", medianNanos / 1000));
        sb.append(String.format("  P95:     %.2f μs\n", p95Nanos / 1000));
        sb.append(String.format("  P99:     %.2f μs\n", p99Nanos / 1000));
        sb.append(String.format("  Throughput: %.0f ops/sec", throughput));
        
        return sb.toString();
    }
    
    /**
     * 转换为微秒
     */
    public BenchmarkResultMicros toMicros() {
        return BenchmarkResultMicros.builder()
            .name(name)
            .count(count)
            .min(minNanos / 1000)
            .max(maxNanos / 1000)
            .mean(meanNanos / 1000)
            .median(medianNanos / 1000)
            .p50(p50Nanos / 1000)
            .p95(p95Nanos / 1000)
            .p99(p99Nanos / 1000)
            .stdDev(stdDevNanos / 1000)
            .throughput(throughput)
            .build();
    }
    
    /**
     * 结果（微秒）
     */
    @Data
    @Builder
    public static class BenchmarkResultMicros {
        private String name;
        private long count;
        private double min;
        private double max;
        private double mean;
        private double median;
        private double p50;
        private double p95;
        private double p99;
        private double stdDev;
        private double throughput;
    }
}