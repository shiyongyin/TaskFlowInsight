package com.syy.taskflowinsight.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * TFI指标收集器
 * 使用Micrometer收集和暴露TFI相关指标
 * 
 * 核心指标：
 * - 变更追踪次数
 * - 快照生成次数
 * - 路径匹配次数和命中率
 * - 集合摘要生成次数
 * - 性能指标（P50, P95, P99）
 * - 内存使用情况
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Component
@ConditionalOnClass(MeterRegistry.class)
public class TfiMetrics {
    
    private static final Logger logger = LoggerFactory.getLogger(TfiMetrics.class);
    
    private final MeterRegistry registry;
    
    // 计数器
    private final Counter changeTrackingCounter;
    private final Counter snapshotCreationCounter;
    private final Counter pathMatchCounter;
    private final Counter pathMatchHitCounter;
    private final Counter collectionSummaryCounter;
    private final Counter errorCounter;
    
    // 计时器
    private final Timer changeTrackingTimer;
    private final Timer snapshotCreationTimer;
    private final Timer pathMatchTimer;
    private final Timer collectionSummaryTimer;
    
    // 自定义指标存储
    private final ConcurrentHashMap<String, AtomicLong> customMetrics = new ConcurrentHashMap<>();
    
    @Autowired
    public TfiMetrics(@Autowired(required = false) MeterRegistry registry) {
        this.registry = registry != null ? registry : new SimpleMeterRegistry();
        
        // 初始化计数器
        this.changeTrackingCounter = Counter.builder("tfi.change.tracking.count")
            .description("Number of change tracking operations")
            .register(this.registry);
            
        this.snapshotCreationCounter = Counter.builder("tfi.snapshot.creation.count")
            .description("Number of snapshot creations")
            .register(this.registry);
            
        this.pathMatchCounter = Counter.builder("tfi.path.match.count")
            .description("Number of path matching operations")
            .register(this.registry);
            
        this.pathMatchHitCounter = Counter.builder("tfi.path.match.hit.count")
            .description("Number of path matching cache hits")
            .register(this.registry);
            
        this.collectionSummaryCounter = Counter.builder("tfi.collection.summary.count")
            .description("Number of collection summaries created")
            .register(this.registry);
            
        this.errorCounter = Counter.builder("tfi.error.count")
            .description("Number of errors")
            .register(this.registry);
        
        // 初始化计时器
        this.changeTrackingTimer = Timer.builder("tfi.change.tracking.duration")
            .description("Time spent in change tracking")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(this.registry);
            
        this.snapshotCreationTimer = Timer.builder("tfi.snapshot.creation.duration")
            .description("Time spent creating snapshots")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(this.registry);
            
        this.pathMatchTimer = Timer.builder("tfi.path.match.duration")
            .description("Time spent in path matching")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(this.registry);
            
        this.collectionSummaryTimer = Timer.builder("tfi.collection.summary.duration")
            .description("Time spent creating collection summaries")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(this.registry);
        
        // 注册JVM和系统指标
        registerSystemMetrics();
        
        logger.info("TFI Metrics initialized with registry: {}", this.registry.getClass().getSimpleName());
    }
    
    /**
     * 记录变更追踪操作
     */
    public void recordChangeTracking(long durationNanos) {
        changeTrackingCounter.increment();
        changeTrackingTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        
        if (logger.isDebugEnabled()) {
            logger.debug("Change tracking recorded: {} ns", durationNanos);
        }
    }
    
    /**
     * 记录快照创建
     */
    public void recordSnapshotCreation(long durationNanos) {
        snapshotCreationCounter.increment();
        snapshotCreationTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }
    
    /**
     * 记录路径匹配
     */
    public void recordPathMatch(long durationNanos, boolean cacheHit) {
        pathMatchCounter.increment();
        pathMatchTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        
        if (cacheHit) {
            pathMatchHitCounter.increment();
        }
    }
    
    /**
     * 记录集合摘要生成
     */
    public void recordCollectionSummary(long durationNanos, int collectionSize) {
        collectionSummaryCounter.increment();
        collectionSummaryTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        
        // 记录集合大小分布
        registry.summary("tfi.collection.size")
            .record(collectionSize);
    }
    
    /**
     * 记录错误
     */
    public void recordError(String errorType) {
        errorCounter.increment();
        
        Counter.builder("tfi.error.type")
            .tag("type", errorType)
            .register(registry)
            .increment();
            
        logger.warn("Error recorded: {}", errorType);
    }
    
    /**
     * 记录自定义指标
     */
    public void recordCustomMetric(String name, double value) {
        registry.summary("tfi.custom." + name).record(value);
    }
    
    /**
     * 增加自定义计数器
     */
    public void incrementCustomCounter(String name) {
        customMetrics.computeIfAbsent(name, k -> {
            AtomicLong counter = new AtomicLong(0);
            Gauge.builder("tfi.custom.counter." + name, counter, AtomicLong::get)
                .register(registry);
            return counter;
        }).incrementAndGet();
    }
    
    /**
     * 计时执行
     */
    public <T> T timeExecution(String metricName, Supplier<T> supplier) {
        Timer timer = Timer.builder("tfi.execution." + metricName)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
            
        return timer.record(supplier);
    }
    
    /**
     * 计时执行（无返回值）
     */
    public void timeExecution(String metricName, Runnable runnable) {
        Timer timer = Timer.builder("tfi.execution." + metricName)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
            
        timer.record(runnable);
    }
    
    /**
     * 获取路径匹配缓存命中率
     */
    public double getPathMatchHitRate() {
        double total = pathMatchCounter.count();
        double hits = pathMatchHitCounter.count();
        
        return total > 0 ? hits / total : 0.0;
    }
    
    /**
     * 获取平均处理时间
     */
    public Duration getAverageProcessingTime(String timerName) {
        Timer timer = registry.find("tfi." + timerName + ".duration").timer();
        if (timer != null) {
            return Duration.ofNanos((long) timer.mean(TimeUnit.NANOSECONDS));
        }
        return Duration.ZERO;
    }
    
    /**
     * 注册系统指标
     */
    private void registerSystemMetrics() {
        // TFI特定的内存使用
        Gauge.builder("tfi.memory.used", Runtime.getRuntime(), 
            runtime -> runtime.totalMemory() - runtime.freeMemory())
            .description("TFI memory usage")
            .baseUnit("bytes")
            .register(registry);
        
        // 活跃线程数
        Gauge.builder("tfi.threads.active", Thread::activeCount)
            .description("Number of active threads")
            .register(registry);
        
        // 自定义健康指标
        Gauge.builder("tfi.health.score", this::calculateHealthScore)
            .description("TFI health score (0-100)")
            .register(registry);
    }
    
    /**
     * 计算健康分数
     */
    private double calculateHealthScore() {
        // 如果没有任何操作，健康分数为100
        double totalOps = changeTrackingCounter.count() + snapshotCreationCounter.count() + 
                         pathMatchCounter.count() + collectionSummaryCounter.count();
        
        if (totalOps == 0) {
            return 100.0;
        }
        
        double score = 100.0;
        
        // 基于错误率降低分数
        double errorRate = errorCounter.count() / totalOps;
        score -= errorRate * 100;
        
        // 基于缓存命中率调整分数（仅当有路径匹配操作时）
        if (pathMatchCounter.count() > 0) {
            double hitRate = getPathMatchHitRate();
            score *= (0.5 + hitRate * 0.5);
        }
        
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * 导出指标摘要
     */
    public MetricsSummary getSummary() {
        return MetricsSummary.builder()
            .changeTrackingCount((long) changeTrackingCounter.count())
            .snapshotCreationCount((long) snapshotCreationCounter.count())
            .pathMatchCount((long) pathMatchCounter.count())
            .pathMatchHitRate(getPathMatchHitRate())
            .collectionSummaryCount((long) collectionSummaryCounter.count())
            .errorCount((long) errorCounter.count())
            .avgChangeTrackingTime(getAverageProcessingTime("change.tracking"))
            .avgSnapshotCreationTime(getAverageProcessingTime("snapshot.creation"))
            .avgPathMatchTime(getAverageProcessingTime("path.match"))
            .avgCollectionSummaryTime(getAverageProcessingTime("collection.summary"))
            .healthScore(calculateHealthScore())
            .build();
    }
    
    /**
     * 重置所有指标
     */
    public void reset() {
        // 注意：Micrometer的计数器不支持重置
        // 这里只清理自定义指标
        customMetrics.clear();
        logger.info("Custom metrics cleared");
    }
}