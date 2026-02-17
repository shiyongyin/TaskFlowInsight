package com.syy.taskflowinsight.annotation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 注解性能监控器
 * 
 * 监控注解处理的性能开销，确保符合性能目标：
 * - 零采样路径 < 1μs
 * - AOP切面开销 P99 < 10μs  
 * - SpEL评估 P99 < 50μs
 * 
 * @since 3.0.0
 */
@Component
public class AnnotationPerformanceMonitor {
    
    private final MeterRegistry meterRegistry;
    
    // 性能计时器
    private final Timer zeroSamplingTimer;
    private final Timer aspectOverheadTimer;
    private final Timer spelEvaluationTimer;
    private final Timer samplingDecisionTimer;
    
    // 性能统计
    private final AtomicLong totalInvocations = new AtomicLong(0);
    private final AtomicLong sampledInvocations = new AtomicLong(0);
    private final AtomicLong zeroPathInvocations = new AtomicLong(0);
    
    // 方法级别性能缓存
    private final ConcurrentHashMap<String, MethodPerformanceStats> methodStats = new ConcurrentHashMap<>();
    
    public AnnotationPerformanceMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化性能计时器
        this.zeroSamplingTimer = Timer.builder("tfi.annotation.zero_sampling.duration.seconds")
            .description("Zero sampling path execution time")
            .register(meterRegistry);
            
        this.aspectOverheadTimer = Timer.builder("tfi.annotation.aspect_overhead.duration.seconds")
            .description("AOP aspect overhead (excluding business logic)")
            .register(meterRegistry);
            
        this.spelEvaluationTimer = Timer.builder("tfi.annotation.spel_evaluation.duration.seconds")
            .description("SpEL expression evaluation time")
            .register(meterRegistry);
            
        this.samplingDecisionTimer = Timer.builder("tfi.annotation.sampling_decision.duration.seconds")
            .description("Sampling decision time")
            .register(meterRegistry);
    }
    
    /**
     * 记录零采样路径执行时间。
     *
     * @param operation 要执行的逻辑
     */
    public void recordZeroSamplingPath(Runnable operation) {
        try {
            zeroSamplingTimer.recordCallable(() -> {
                zeroPathInvocations.incrementAndGet();
                operation.run();
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException("Zero sampling recording failed", e);
        }
    }
    
    /**
     * 记录切面开销时间。
     *
     * @param operation 要执行的逻辑
     * @param <T> 返回类型
     * @return 操作返回值
     * @throws RuntimeException 操作执行失败时
     */
    public <T> T recordAspectOverhead(java.util.concurrent.Callable<T> operation) {
        try {
            return aspectOverheadTimer.recordCallable(() -> {
                totalInvocations.incrementAndGet();
                return operation.call();
            });
        } catch (Exception e) {
            throw new RuntimeException("Aspect overhead recording failed", e);
        }
    }
    
    /**
     * 记录 SpEL 评估时间。
     *
     * @param operation 要执行的 SpEL 评估
     * @param <T> 返回类型
     * @return 评估结果
     * @throws RuntimeException 评估失败时
     */
    public <T> T recordSpELEvaluation(java.util.concurrent.Callable<T> operation) {
        try {
            return spelEvaluationTimer.recordCallable(operation);
        } catch (Exception e) {
            throw new RuntimeException("SpEL evaluation recording failed", e);
        }
    }
    
    /**
     * 记录采样决策时间。
     *
     * @param decisionSupplier 采样决策逻辑
     * @return 是否采样
     * @throws RuntimeException 决策执行失败时
     */
    public boolean recordSamplingDecision(java.util.function.Supplier<Boolean> decisionSupplier) {
        try {
            return samplingDecisionTimer.recordCallable(() -> {
                boolean result = decisionSupplier.get();
                if (result) {
                    sampledInvocations.incrementAndGet();
                }
                return result;
            });
        } catch (Exception e) {
            throw new RuntimeException("Sampling decision recording failed", e);
        }
    }
    
    /**
     * 记录方法级别性能统计。
     *
     * @param methodSignature 方法签名
     * @param durationNanos 耗时（纳秒）
     * @param sampled 是否被采样
     */
    public void recordMethodPerformance(String methodSignature, long durationNanos, boolean sampled) {
        methodStats.computeIfAbsent(methodSignature, k -> new MethodPerformanceStats())
            .record(durationNanos, sampled);
    }
    
    /**
     * 获取性能统计摘要。
     *
     * @return 包含调用数、采样率、各计时器 P99 的摘要
     */
    public PerformanceSummary getPerformanceSummary() {
        long total = totalInvocations.get();
        long sampled = sampledInvocations.get();
        long zeroPath = zeroPathInvocations.get();
        
        return new PerformanceSummary(
            total, sampled, zeroPath,
            calculateSamplingRate(total, sampled),
            calculateZeroPathRate(total, zeroPath),
            getTimerSummary("zero_sampling", zeroSamplingTimer),
            getTimerSummary("aspect_overhead", aspectOverheadTimer),
            getTimerSummary("spel_evaluation", spelEvaluationTimer),
            getTimerSummary("sampling_decision", samplingDecisionTimer)
        );
    }
    
    /**
     * 获取方法级别性能统计。
     *
     * @return 方法签名到统计的映射（副本）
     */
    public java.util.Map<String, MethodPerformanceStats> getMethodStats() {
        return new java.util.HashMap<>(methodStats);
    }
    
    /**
     * 检查性能目标是否达成。
     *
     * @return 各目标达成情况及实际 P99 值
     */
    public PerformanceAssessment assessPerformance() {
        PerformanceSummary summary = getPerformanceSummary();
        
        boolean zeroPathTarget = summary.zeroSamplingP99 < 1_000; // < 1μs
        boolean aspectTarget = summary.aspectOverheadP99 < 10_000; // < 10μs
        boolean spelTarget = summary.spelEvaluationP99 < 50_000; // < 50μs
        boolean samplingTarget = summary.samplingDecisionP99 < 500; // < 500ns
        
        return new PerformanceAssessment(
            zeroPathTarget, aspectTarget, spelTarget, samplingTarget,
            summary.zeroSamplingP99, summary.aspectOverheadP99, 
            summary.spelEvaluationP99, summary.samplingDecisionP99
        );
    }
    
    /**
     * 清理性能统计
     */
    public void clearStats() {
        totalInvocations.set(0);
        sampledInvocations.set(0);
        zeroPathInvocations.set(0);
        methodStats.clear();
    }
    
    // 辅助方法
    
    private double calculateSamplingRate(long total, long sampled) {
        return total > 0 ? (double) sampled / total : 0.0;
    }
    
    private double calculateZeroPathRate(long total, long zeroPath) {
        return total > 0 ? (double) zeroPath / total : 0.0;
    }
    
    private TimerSummary getTimerSummary(String name, Timer timer) {
        if (timer.count() == 0) {
            return new TimerSummary(name, 0, 0.0, 0.0, 0.0);
        }
        
        return new TimerSummary(
            name,
            timer.count(),
            timer.mean(java.util.concurrent.TimeUnit.NANOSECONDS),
            timer.max(java.util.concurrent.TimeUnit.NANOSECONDS),
            timer.percentile(0.99, java.util.concurrent.TimeUnit.NANOSECONDS)
        );
    }
    
    // 内部类

    /**
     * 方法级别性能统计。
     * <p>
     * 记录调用次数、采样次数、最小/最大/平均耗时。
     *
     * @since 3.0.0
     */
    public static class MethodPerformanceStats {
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicLong sampledCalls = new AtomicLong(0);
        private volatile long minDuration = Long.MAX_VALUE;
        private volatile long maxDuration = 0;
        private volatile double avgDuration = 0.0;
        
        /**
         * 记录一次调用。
         *
         * @param durationNanos 耗时（纳秒）
         * @param sampled 是否被采样
         */
        public void record(long durationNanos, boolean sampled) {
            totalCalls.incrementAndGet();
            if (sampled) {
                sampledCalls.incrementAndGet();
            }
            
            minDuration = Math.min(minDuration, durationNanos);
            maxDuration = Math.max(maxDuration, durationNanos);
            
            // 简单移动平均
            avgDuration = (avgDuration + durationNanos) / 2.0;
        }
        
        public long getTotalCalls() { return totalCalls.get(); }
        public long getSampledCalls() { return sampledCalls.get(); }
        public double getSamplingRate() { 
            long total = totalCalls.get();
            return total > 0 ? (double) sampledCalls.get() / total : 0.0;
        }
        public long getMinDuration() { return minDuration == Long.MAX_VALUE ? 0 : minDuration; }
        public long getMaxDuration() { return maxDuration; }
        public double getAvgDuration() { return avgDuration; }
    }
    
    /**
     * 性能统计摘要。
     * <p>
     * 包含总调用数、采样数、零采样路径数、各计时器的 P99 等。
     *
     * @since 3.0.0
     */
    public static class PerformanceSummary {
        public final long totalInvocations;
        public final long sampledInvocations;
        public final long zeroPathInvocations;
        public final double samplingRate;
        public final double zeroPathRate;
        public final double zeroSamplingP99;
        public final double aspectOverheadP99;
        public final double spelEvaluationP99;
        public final double samplingDecisionP99;
        
        public PerformanceSummary(long totalInvocations, long sampledInvocations, long zeroPathInvocations,
                                double samplingRate, double zeroPathRate,
                                TimerSummary zeroSampling, TimerSummary aspectOverhead,
                                TimerSummary spelEvaluation, TimerSummary samplingDecision) {
            this.totalInvocations = totalInvocations;
            this.sampledInvocations = sampledInvocations;
            this.zeroPathInvocations = zeroPathInvocations;
            this.samplingRate = samplingRate;
            this.zeroPathRate = zeroPathRate;
            this.zeroSamplingP99 = zeroSampling.p99;
            this.aspectOverheadP99 = aspectOverhead.p99;
            this.spelEvaluationP99 = spelEvaluation.p99;
            this.samplingDecisionP99 = samplingDecision.p99;
        }
    }
    
    /**
     * 计时器摘要。
     * <p>
     * 包含 count、mean、max、p99。
     *
     * @since 3.0.0
     */
    public static class TimerSummary {
        public final String name;
        public final long count;
        public final double mean;
        public final double max;
        public final double p99;
        
        public TimerSummary(String name, long count, double mean, double max, double p99) {
            this.name = name;
            this.count = count;
            this.mean = mean;
            this.max = max;
            this.p99 = p99;
        }
    }
    
    /**
     * 性能评估结果。
     * <p>
     * 包含各目标是否达成及实际 P99 值。
     *
     * @since 3.0.0
     */
    public static class PerformanceAssessment {
        public final boolean zeroPathTarget;
        public final boolean aspectTarget;
        public final boolean spelTarget;
        public final boolean samplingTarget;
        public final double zeroPathActual;
        public final double aspectActual;
        public final double spelActual;
        public final double samplingActual;
        
        public PerformanceAssessment(boolean zeroPathTarget, boolean aspectTarget, boolean spelTarget, 
                                   boolean samplingTarget, double zeroPathActual, double aspectActual,
                                   double spelActual, double samplingActual) {
            this.zeroPathTarget = zeroPathTarget;
            this.aspectTarget = aspectTarget;
            this.spelTarget = spelTarget;
            this.samplingTarget = samplingTarget;
            this.zeroPathActual = zeroPathActual;
            this.aspectActual = aspectActual;
            this.spelActual = spelActual;
            this.samplingActual = samplingActual;
        }
        
        public boolean allTargetsMet() {
            return zeroPathTarget && aspectTarget && spelTarget && samplingTarget;
        }
    }
}