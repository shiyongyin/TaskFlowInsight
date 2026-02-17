package com.syy.taskflowinsight.performance.monitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 按操作名聚合的指标收集器。
 *
 * <p>记录每次操作的调用次数（总数/成功/失败）和延迟样本，
 * 并支持生成 {@link MetricSnapshot} 快照。</p>
 *
 * <p>延迟样本使用 lock-free 的 {@link ConcurrentLinkedDeque} 作为有界环形缓冲区，
 * 保留最近 {@value #SAMPLE_SIZE} 条记录，避免 {@code synchronizedList} 锁竞争。</p>
 *
 * <p>线程安全：所有字段均为并发安全结构，可在多线程环境中安全共享。</p>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
public class MetricCollector {

    /** 延迟样本保留数量上限 */
    private static final int SAMPLE_SIZE = 1000;

    private final String name;
    private final LongAdder totalOps = new LongAdder();
    private final LongAdder successOps = new LongAdder();
    private final LongAdder errorOps = new LongAdder();
    private final Deque<Long> recentLatencies = new ConcurrentLinkedDeque<>();
    private final AtomicInteger latencyCount = new AtomicInteger(0);

    /**
     * 创建指标收集器。
     *
     * @param name 操作名称
     */
    public MetricCollector(String name) {
        this.name = name;
    }

    /**
     * 记录一次操作。
     *
     * @param durationNanos 操作耗时（纳秒）
     * @param success       是否成功
     */
    public void record(long durationNanos, boolean success) {
        totalOps.increment();

        if (success) {
            successOps.increment();
        } else {
            errorOps.increment();
        }

        recentLatencies.addLast(durationNanos);
        if (latencyCount.incrementAndGet() > SAMPLE_SIZE) {
            recentLatencies.pollFirst();
            latencyCount.decrementAndGet();
        }
    }

    /**
     * 生成带时间戳的快照。
     *
     * @param timestamp 时间戳（epoch millis）
     * @return 指标快照
     */
    public MetricSnapshot snapshot(long timestamp) {
        List<Long> latenciesCopy = new ArrayList<>(recentLatencies);
        Collections.sort(latenciesCopy);

        if (latenciesCopy.isEmpty()) {
            return MetricSnapshot.empty(name, timestamp);
        }

        return MetricSnapshot.builder()
                .name(name)
                .timestamp(timestamp)
                .totalOps(totalOps.sum())
                .successOps(successOps.sum())
                .errorOps(errorOps.sum())
                .minMicros(latenciesCopy.get(0) / 1000.0)
                .maxMicros(latenciesCopy.get(latenciesCopy.size() - 1) / 1000.0)
                .p50Micros(percentile(latenciesCopy, 0.50) / 1000.0)
                .p95Micros(percentile(latenciesCopy, 0.95) / 1000.0)
                .p99Micros(percentile(latenciesCopy, 0.99) / 1000.0)
                .build();
    }

    /**
     * 生成当前时刻的快照（等价于 {@code snapshot(System.currentTimeMillis())}）。
     *
     * @return 当前指标快照
     */
    public MetricSnapshot currentStats() {
        return snapshot(System.currentTimeMillis());
    }

    /**
     * 获取操作名称。
     *
     * @return 操作名称
     */
    public String getName() {
        return name;
    }

    private double percentile(List<Long> sorted, double p) {
        int index = (int) (sorted.size() * p);
        return sorted.get(Math.min(index, sorted.size() - 1));
    }
}
