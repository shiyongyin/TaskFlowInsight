package com.syy.taskflowinsight.performance;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 基准测试结果 DTO。
 *
 * <p>包含单项 benchmark 的统计数据：样本数、最小/最大/均值/中位数延迟、
 * P50/P95/P99 分位数、标准差和吞吐量。</p>
 *
 * <p>结果状态：</p>
 * <ul>
 *   <li>{@code SUCCESS} — 测试正常完成</li>
 *   <li>{@code SKIPPED} — 测试被跳过（依赖不可用）</li>
 *   <li>{@code ERROR} — 测试出错</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@Data
@Builder
public class BenchmarkResult {

    /** 测试名称 */
    private String name;

    /** 样本数 */
    private long count;

    /** 最小延迟（纳秒） */
    private double minNanos;

    /** 最大延迟（纳秒） */
    private double maxNanos;

    /** 平均延迟（纳秒） */
    private double meanNanos;

    /** 中位数延迟（纳秒） */
    private double medianNanos;

    /** P50 延迟（纳秒） */
    private double p50Nanos;

    /** P95 延迟（纳秒） */
    private double p95Nanos;

    /** P99 延迟（纳秒） */
    private double p99Nanos;

    /** 标准差（纳秒） */
    private double stdDevNanos;

    /** 吞吐量（ops/sec） */
    private double throughput;

    /** 结果状态：SUCCESS / SKIPPED / ERROR */
    private String status;

    /** 附加消息（状态非 SUCCESS 时的说明） */
    private String message;

    /**
     * 从原始延迟样本列表计算统计结果。
     *
     * @param name      测试名称
     * @param durations 延迟样本（纳秒），{@code null} 或空列表返回 ERROR
     * @return 计算后的测试结果
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
     * 创建状态为 SKIPPED 的结果。
     *
     * @param message 跳过原因
     * @return 跳过状态的结果
     */
    public static BenchmarkResult skipped(String message) {
        return BenchmarkResult.builder()
            .status("SKIPPED")
            .message(message)
            .build();
    }
    
    /**
     * 创建状态为 ERROR 的结果。
     *
     * @param name    测试名称
     * @param message 错误描述
     * @return 错误状态的结果
     */
    public static BenchmarkResult error(String name, String message) {
        return BenchmarkResult.builder()
            .name(name)
            .status("ERROR")
            .message(message)
            .build();
    }
    
    /**
     * 格式化为人可读的文本。
     *
     * @return 包含样本数、延迟分位数和吞吐量的格式化字符串
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
     * 转换为微秒单位的结果。
     *
     * @return 所有延迟字段从纳秒转换为微秒的 DTO
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
     * 微秒单位的基准测试结果 DTO（JSON 序列化友好）。
     *
     * @since 3.0.0
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