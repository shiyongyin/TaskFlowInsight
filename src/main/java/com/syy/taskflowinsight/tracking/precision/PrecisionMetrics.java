package com.syy.taskflowinsight.tracking.precision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

/**
 * 精度处理监控指标
 * 与CT-004保持命名一致，轻量化埋点设计
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
public class PrecisionMetrics {
    
    private static final Logger logger = LoggerFactory.getLogger(PrecisionMetrics.class);
    
    // 指标前缀，与CT-004保持一致
    public static final String METRIC_PREFIX = "tfi.precision";
    
    // 计数器（使用原子类，避免依赖Micrometer）
    private final AtomicLong numericComparisonCount = new AtomicLong(0);
    private final AtomicLong dateTimeComparisonCount = new AtomicLong(0);
    private final AtomicLong toleranceHitCount = new AtomicLong(0);
    private final AtomicLong bigDecimalComparisonCount = new AtomicLong(0);
    private final AtomicLong precisionCacheHitCount = new AtomicLong(0);
    private final AtomicLong precisionCacheMissCount = new AtomicLong(0);
    
    // 计时器（纳秒级，轻量实现）
    private final AtomicLong totalCalculationTimeNanos = new AtomicLong(0);
    private final AtomicLong calculationCount = new AtomicLong(0);
    
    // 容差命中详细统计
    private final AtomicLong absoluteToleranceHits = new AtomicLong(0);
    private final AtomicLong relativeToleranceHits = new AtomicLong(0);
    private final AtomicLong dateToleranceHits = new AtomicLong(0);
    
    // 可选的Micrometer支持（避免强依赖）
    private Optional<Object> meterRegistry = Optional.empty();
    private boolean micrometerEnabled = false;
    
    /**
     * 启用Micrometer支持（可选，运行时检测）
     */
    public void enableMicrometerIfAvailable() {
        try {
            // 运行时检测Micrometer是否可用
            Class.forName("io.micrometer.core.instrument.MeterRegistry");
            Class.forName("io.micrometer.core.instrument.Metrics");
            
            // 获取全局注册器
            Object globalRegistry = Class.forName("io.micrometer.core.instrument.Metrics")
                .getMethod("globalRegistry").invoke(null);
            
            if (globalRegistry != null) {
                meterRegistry = Optional.of(globalRegistry);
                micrometerEnabled = true;
                logger.info("Micrometer metrics enabled for precision tracking");
                initializeMicrometerMetrics();
            }
        } catch (Exception e) {
            logger.debug("Micrometer not available, using lightweight metrics only", e);
            micrometerEnabled = false;
        }
    }
    
    /**
     * 初始化Micrometer指标（如果启用）
     */
    private void initializeMicrometerMetrics() {
        if (!micrometerEnabled || !meterRegistry.isPresent()) return;
        
        try {
            Object registry = meterRegistry.get();
            
            // 使用反射注册指标，避免编译时依赖
            // Counter.builder("tfi.precision.numeric.comparisons").register(registry)
            registerCounter(registry, METRIC_PREFIX + ".numeric.comparisons", 
                "Total number of numeric precision comparisons");
            registerCounter(registry, METRIC_PREFIX + ".datetime.comparisons", 
                "Total number of datetime precision comparisons");
            registerCounter(registry, METRIC_PREFIX + ".tolerance.hits", 
                "Total number of tolerance hits");
            registerCounter(registry, METRIC_PREFIX + ".cache.hits", 
                "Total number of cache hits");
            registerCounter(registry, METRIC_PREFIX + ".cache.misses", 
                "Total number of cache misses");
                
            logger.info("Micrometer precision metrics initialized");
        } catch (Exception e) {
            logger.warn("Failed to initialize Micrometer metrics", e);
            micrometerEnabled = false;
        }
    }
    
    /**
     * 使用反射注册Counter指标
     */
    private void registerCounter(Object registry, String name, String description) {
        try {
            Class<?> counterClass = Class.forName("io.micrometer.core.instrument.Counter");
            Class<?> builderClass = counterClass.getDeclaredClasses()[0]; // Counter.Builder
            
            // Counter.builder(name).description(description).register(registry)
            Object builder = counterClass.getMethod("builder", String.class).invoke(null, name);
            builder = builderClass.getMethod("description", String.class).invoke(builder, description);
            builderClass.getMethod("register", Class.forName("io.micrometer.core.instrument.MeterRegistry"))
                .invoke(builder, registry);
        } catch (Exception e) {
            logger.debug("Failed to register counter {}: {}", name, e.getMessage());
        }
    }
    
    /**
     * 记录数值比较
     */
    public void recordNumericComparison() {
        numericComparisonCount.incrementAndGet();
        logger.trace("Numeric comparison recorded");
    }
    
    /**
     * 记录数值比较（带类型）
     */
    public void recordNumericComparison(String numberType) {
        numericComparisonCount.incrementAndGet();
        logger.trace("Numeric comparison recorded: type={}", numberType);
    }
    
    /**
     * 记录日期时间比较
     */
    public void recordDateTimeComparison() {
        dateTimeComparisonCount.incrementAndGet();
        logger.trace("DateTime comparison recorded");
    }
    
    /**
     * 记录日期时间比较（带类型）
     */
    public void recordDateTimeComparison(String temporalType) {
        dateTimeComparisonCount.incrementAndGet();
        logger.trace("DateTime comparison recorded: type={}", temporalType);
    }
    
    /**
     * 记录容差命中
     */
    public void recordToleranceHit(String type, double actualDiff) {
        toleranceHitCount.incrementAndGet();
        
        switch (type.toLowerCase()) {
            case "absolute":
                absoluteToleranceHits.incrementAndGet();
                break;
            case "relative":
                relativeToleranceHits.incrementAndGet();
                break;
            case "date":
                dateToleranceHits.incrementAndGet();
                break;
        }
        
        logger.trace("Tolerance hit recorded: type={}, diff={}", type, actualDiff);
    }
    
    /**
     * 记录BigDecimal比较方法使用
     */
    public void recordBigDecimalComparison(String method) {
        bigDecimalComparisonCount.incrementAndGet();
        logger.trace("BigDecimal comparison recorded: method={}", method);
    }
    
    /**
     * 记录精度计算时间
     */
    public void recordCalculationTime(long nanos) {
        totalCalculationTimeNanos.addAndGet(nanos);
        calculationCount.incrementAndGet();
        
        // 记录慢操作（>1ms）
        if (nanos > 1_000_000) {
            logger.debug("Slow precision calculation: {}ms", nanos / 1_000_000);
        }
    }
    
    /**
     * 记录精度缓存命中
     */
    public void recordCacheHit() {
        precisionCacheHitCount.incrementAndGet();
    }
    
    /**
     * 记录精度缓存未命中
     */
    public void recordCacheMiss() {
        precisionCacheMissCount.incrementAndGet();
    }
    
    /**
     * 获取指标快照
     */
    public MetricsSnapshot getSnapshot() {
        return new MetricsSnapshot(
            numericComparisonCount.get(),
            dateTimeComparisonCount.get(),
            toleranceHitCount.get(),
            bigDecimalComparisonCount.get(),
            precisionCacheHitCount.get(),
            precisionCacheMissCount.get(),
            totalCalculationTimeNanos.get(),
            calculationCount.get(),
            absoluteToleranceHits.get(),
            relativeToleranceHits.get(),
            dateToleranceHits.get()
        );
    }
    
    /**
     * 重置所有指标
     */
    public void reset() {
        numericComparisonCount.set(0);
        dateTimeComparisonCount.set(0);
        toleranceHitCount.set(0);
        bigDecimalComparisonCount.set(0);
        precisionCacheHitCount.set(0);
        precisionCacheMissCount.set(0);
        totalCalculationTimeNanos.set(0);
        calculationCount.set(0);
        absoluteToleranceHits.set(0);
        relativeToleranceHits.set(0);
        dateToleranceHits.set(0);
        
        logger.info("Precision metrics reset");
    }
    
    /**
     * 打印指标摘要
     */
    public void logSummary() {
        MetricsSnapshot snapshot = getSnapshot();
        
        logger.info("=== Precision Metrics Summary ===");
        logger.info("Numeric comparisons: {}", snapshot.numericComparisonCount);
        logger.info("DateTime comparisons: {}", snapshot.dateTimeComparisonCount);
        logger.info("Tolerance hits: {} (abs:{}, rel:{}, date:{})", 
            snapshot.toleranceHitCount,
            snapshot.absoluteToleranceHits, 
            snapshot.relativeToleranceHits,
            snapshot.dateToleranceHits);
        logger.info("BigDecimal comparisons: {}", snapshot.bigDecimalComparisonCount);
        logger.info("Cache hit rate: {:.2f}% ({}/{} total)", 
            snapshot.getCacheHitRate() * 100,
            snapshot.precisionCacheHitCount,
            snapshot.getTotalCacheRequests());
        logger.info("Average calculation time: {:.2f}μs", 
            snapshot.getAverageCalculationTimeMicros());
    }
    
    /**
     * 指标快照类
     */
    public static class MetricsSnapshot {
        public final long numericComparisonCount;
        public final long dateTimeComparisonCount;
        public final long toleranceHitCount;
        public final long bigDecimalComparisonCount;
        public final long precisionCacheHitCount;
        public final long precisionCacheMissCount;
        public final long totalCalculationTimeNanos;
        public final long calculationCount;
        public final long absoluteToleranceHits;
        public final long relativeToleranceHits;
        public final long dateToleranceHits;
        
        private MetricsSnapshot(long numericComparisonCount,
                               long dateTimeComparisonCount,
                               long toleranceHitCount,
                               long bigDecimalComparisonCount,
                               long precisionCacheHitCount,
                               long precisionCacheMissCount,
                               long totalCalculationTimeNanos,
                               long calculationCount,
                               long absoluteToleranceHits,
                               long relativeToleranceHits,
                               long dateToleranceHits) {
            this.numericComparisonCount = numericComparisonCount;
            this.dateTimeComparisonCount = dateTimeComparisonCount;
            this.toleranceHitCount = toleranceHitCount;
            this.bigDecimalComparisonCount = bigDecimalComparisonCount;
            this.precisionCacheHitCount = precisionCacheHitCount;
            this.precisionCacheMissCount = precisionCacheMissCount;
            this.totalCalculationTimeNanos = totalCalculationTimeNanos;
            this.calculationCount = calculationCount;
            this.absoluteToleranceHits = absoluteToleranceHits;
            this.relativeToleranceHits = relativeToleranceHits;
            this.dateToleranceHits = dateToleranceHits;
        }
        
        /**
         * 计算缓存命中率
         */
        public double getCacheHitRate() {
            long total = getTotalCacheRequests();
            return total > 0 ? (double) precisionCacheHitCount / total : 0.0;
        }
        
        /**
         * 获取总缓存请求数
         */
        public long getTotalCacheRequests() {
            return precisionCacheHitCount + precisionCacheMissCount;
        }
        
        /**
         * 计算平均计算时间（微秒）
         */
        public double getAverageCalculationTimeMicros() {
            return calculationCount > 0 ? 
                (double) totalCalculationTimeNanos / calculationCount / 1000.0 : 0.0;
        }
        
        /**
         * 计算容差命中率
         */
        public double getToleranceHitRate() {
            long totalComparisons = numericComparisonCount + dateTimeComparisonCount;
            return totalComparisons > 0 ? (double) toleranceHitCount / totalComparisons : 0.0;
        }
    }
}