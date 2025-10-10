package com.syy.taskflowinsight.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
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
    
    // 降级相关指标（v3.0.0新增）
    private final Counter degradationEventsCounter;
    private final Counter slowOperationCounter;
    
    // 计时器
    private final Timer changeTrackingTimer;
    private final Timer snapshotCreationTimer;
    private final Timer pathMatchTimer;
    private final Timer collectionSummaryTimer;
    
    // 自定义指标存储
    private final ConcurrentHashMap<String, AtomicLong> customMetrics = new ConcurrentHashMap<>();
    
    public TfiMetrics(Optional<MeterRegistry> registry) {
        this.registry = registry.orElse(new SimpleMeterRegistry());
        
        // 初始化计数器
        this.changeTrackingCounter = Counter.builder("tfi.change.tracking.total")
            .description("Number of change tracking operations")
            .register(this.registry);
            
        this.snapshotCreationCounter = Counter.builder("tfi.snapshot.creation.total")
            .description("Number of snapshot creations")
            .register(this.registry);
            
        this.pathMatchCounter = Counter.builder("tfi.path.match.total")
            .description("Number of path matching operations")
            .register(this.registry);
            
        this.pathMatchHitCounter = Counter.builder("tfi.path.match.hit.total")
            .description("Number of path matching cache hits")
            .register(this.registry);
            
        this.collectionSummaryCounter = Counter.builder("tfi.collection.summary.total")
            .description("Number of collection summaries created")
            .register(this.registry);
            
        this.errorCounter = Counter.builder("tfi.error.total")
            .description("Number of errors")
            .register(this.registry);
        
        // 降级相关计数器（v3.0.0新增）
        this.degradationEventsCounter = Counter.builder("tfi.degradation.events.total")
            .description("Number of degradation level changes")
            .register(this.registry);
            
        this.slowOperationCounter = Counter.builder("tfi.operation.slow.total")
            .description("Number of slow operations (>=200ms)")
            .register(this.registry);
        
        // 初始化计时器
        this.changeTrackingTimer = Timer.builder("tfi.change.tracking.duration.seconds")
            .description("Time spent in change tracking")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(this.registry);
            
        this.snapshotCreationTimer = Timer.builder("tfi.snapshot.creation.duration.seconds")
            .description("Time spent creating snapshots")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(this.registry);
            
        this.pathMatchTimer = Timer.builder("tfi.path.match.duration.seconds")
            .description("Time spent in path matching")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(this.registry);
            
        this.collectionSummaryTimer = Timer.builder("tfi.collection.summary.duration.seconds")
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
        registry.summary("tfi.collection.size.total")
            .record(collectionSize);
    }
    
    /**
     * 记录错误
     */
    public void recordError(String errorType) {
        errorCounter.increment();
        
        Counter.builder("tfi.error.type.total")
            .tag("type", errorType)
            .register(registry)
            .increment();
            
        logger.warn("Error recorded: {}", errorType);
    }
    
    /**
     * 记录降级事件（v3.0.0新增）
     */
    public void recordDegradationEvent(String fromLevel, String toLevel, String reason) {
        degradationEventsCounter.increment();
        
        Counter.builder("tfi.degradation.transitions.total")
            .tag("from", fromLevel.toLowerCase())
            .tag("to", toLevel.toLowerCase())
            .tag("reason", reason)
            .register(registry)
            .increment();
            
        logger.info("Degradation event recorded: {} -> {} ({})", fromLevel, toLevel, reason);
    }
    
    /**
     * 记录慢操作（v3.0.0新增）
     */
    public void recordSlowOperation(String operationType, long durationMs) {
        slowOperationCounter.increment();
        
        Timer.builder("tfi.operation.slow.duration.milliseconds")
            .tag("operation", operationType)
            .register(registry)
            .record(durationMs, TimeUnit.MILLISECONDS);
            
        if (logger.isDebugEnabled()) {
            logger.debug("Slow operation recorded: {} took {}ms", operationType, durationMs);
        }
    }
    
    /**
     * 记录自定义指标
     */
    public void recordCustomMetric(String name, double value) {
        registry.summary("tfi.custom." + name + ".total").record(value);
    }
    
    /**
     * 增加自定义计数器
     */
    public void incrementCustomCounter(String name) {
        customMetrics.computeIfAbsent(name, k -> {
            AtomicLong counter = new AtomicLong(0);
            Gauge.builder("tfi.custom.counter." + name + ".total", counter, AtomicLong::get)
                .register(registry);
            return counter;
        }).incrementAndGet();
    }
    
    /**
     * 记录自定义计时（毫秒）
     */
    public void recordCustomTiming(String name, long durationMs) {
        Timer timer = Timer.builder("tfi.custom.timing." + name + ".milliseconds")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        timer.record(durationMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 计时执行
     */
    public <T> T timeExecution(String metricName, Supplier<T> supplier) {
        Timer timer = Timer.builder("tfi.execution." + metricName + ".seconds")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
            
        return timer.record(supplier);
    }
    
    /**
     * 计时执行（无返回值）
     */
    public void timeExecution(String metricName, Runnable runnable) {
        Timer timer = Timer.builder("tfi.execution." + metricName + ".seconds")
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
        Timer timer = registry.find("tfi." + timerName + ".duration.seconds").timer();
        if (timer != null) {
            return Duration.ofNanos((long) timer.mean(TimeUnit.NANOSECONDS));
        }
        return Duration.ZERO;
    }
    
    /**
     * 注册Gauge指标（v3.0.0新增）
     */
    public void registerGauge(String name, Supplier<Number> supplier) {
        Gauge.builder(name, supplier)
            .description("Custom gauge metric")
            .register(registry);
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
        Gauge.builder("tfi.threads.active.total", Thread::activeCount)
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
    
    // ==================== 企业级扩展指标 (M2-006) ====================
    
    // 标准指标名称常量（符合Prometheus规范）
    public static final String STAGE_DURATION_SECONDS = "tfi.stage.duration.seconds";
    public static final String STAGE_COUNT_TOTAL = "tfi.stage.count.total";
    public static final String STAGE_ERROR_TOTAL = "tfi.stage.error.total";
    
    public static final String CACHE_HIT_RATIO = "tfi.cache.hit.ratio";
    public static final String CACHE_EVICTION_TOTAL = "tfi.cache.eviction.total";
    public static final String CACHE_SIZE_CURRENT = "tfi.cache.size.current";
    public static final String CACHE_LOAD_DURATION_SECONDS = "tfi.cache.load.duration.seconds";
    
    public static final String SESSION_ACTIVE_CURRENT = "tfi.session.active.current";
    public static final String SESSION_CREATED_TOTAL = "tfi.session.created.total";
    public static final String SESSION_DURATION_SECONDS = "tfi.session.duration.seconds";
    
    public static final String TASK_ACTIVE_CURRENT = "tfi.task.active.current";
    public static final String TASK_CREATED_TOTAL = "tfi.task.created.total";
    public static final String TASK_DURATION_SECONDS = "tfi.task.duration.seconds";
    
    public static final String HTTP_REQUEST_DURATION_SECONDS = "tfi.http.request.duration.seconds";
    public static final String DB_QUERY_DURATION_SECONDS = "tfi.db.query.duration.seconds";
    public static final String API_LATENCY_SECONDS = "tfi.api.latency.seconds";
    
    public static final String ERROR_TOTAL = "tfi.error.total";
    public static final String ERROR_RATE = "tfi.error.rate";
    
    // CT-006: 并发与内存指标
    public static final String CME_RETRY_TOTAL = "tfi.cme.retry.total";
    public static final String CME_RETRY_SUCCESS_RATE = "tfi.cme.retry.success.rate";
    public static final String FIFO_EVICTION_TOTAL = "tfi.fifo.eviction.total";
    
    // 兼容性：旧指标名映射（自动迁移）
    private static final Map<String, String> LEGACY_METRIC_MAPPING = Map.of(
        "tfi.stage.duration", STAGE_DURATION_SECONDS,
        "tfi.cache.get.duration", CACHE_LOAD_DURATION_SECONDS,
        "tfi.http.request.duration", HTTP_REQUEST_DURATION_SECONDS,
        "tfi.db.query.duration", DB_QUERY_DURATION_SECONDS,
        "tfi.api.latency", API_LATENCY_SECONDS
    );
    
    // 企业级缓存
    private final Map<String, Timer> enterpriseTimerCache = new ConcurrentHashMap<>();
    private final Map<String, Counter> enterpriseCounterCache = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> enterpriseGaugeValues = new ConcurrentHashMap<>();
    
    // === 企业级业务指标收集 ===
    
    /**
     * 记录Stage执行（企业级版本）
     */
    public void recordStageExecution(String stageName, Duration duration, boolean success) {
        if (!isMetricEnabled("stage")) return;
        
        // 标准化计数器
        enterpriseCounterCache.computeIfAbsent(
            STAGE_COUNT_TOTAL + "." + stageName,
            k -> Counter.builder(STAGE_COUNT_TOTAL)
                .description("Total count of stage executions")
                .tag("stage", stageName)
                .tag("status", success ? "success" : "failure")
                .register(registry)
        ).increment();
        
        // 标准化持续时间（.seconds后缀）
        if (duration != null) {
            Timer timer = enterpriseTimerCache.computeIfAbsent(
                STAGE_DURATION_SECONDS + "." + stageName,
                k -> Timer.builder(STAGE_DURATION_SECONDS)
                    .description("Stage execution duration in seconds")
                    .tag("stage", stageName)
                    .tag("status", success ? "success" : "failure")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .publishPercentileHistogram(true)
                    .minimumExpectedValue(Duration.ofMillis(1))
                    .maximumExpectedValue(Duration.ofMinutes(5))
                    .register(registry)
            );
            
            timer.record(duration);
            
            // 兼容性：记录旧指标名（带警告）
            recordLegacyMetric("tfi.stage.duration", duration, 
                Tag.of("stage", stageName), Tag.of("status", success ? "success" : "failure"));
        }
    }
    
    /**
     * 记录Stage错误（企业级版本）
     */
    public void recordStageError(String stageName, String errorType, Throwable error) {
        if (!isMetricEnabled("stage")) return;
        
        enterpriseCounterCache.computeIfAbsent(
            STAGE_ERROR_TOTAL + "." + stageName + "." + errorType,
            k -> Counter.builder(STAGE_ERROR_TOTAL)
                .description("Total count of stage errors")
                .tag("stage", stageName)
                .tag("error.type", errorType)
                .tag("error.class", error != null ? error.getClass().getSimpleName() : "Unknown")
                .register(registry)
        ).increment();
    }
    
    /**
     * 记录缓存指标（企业级完整监控）
     */
    public void recordCacheMetrics(String cacheName, 
                                   double hitRatio, 
                                   long evictionCount, 
                                   long size,
                                   Duration avgLoadTime) {
        if (!isMetricEnabled("cache")) return;
        
        // 命中率 (关键SLI指标)
        Gauge.builder(CACHE_HIT_RATIO, () -> hitRatio)
            .description("Cache hit ratio (0-1)")
            .tag("cache", cacheName)
            .register(registry);
        
        // 驱逐总数
        AtomicLong evictionGauge = enterpriseGaugeValues.computeIfAbsent(
            CACHE_EVICTION_TOTAL + "." + cacheName, k -> new AtomicLong(0));
        evictionGauge.set(evictionCount);
        
        Gauge.builder(CACHE_EVICTION_TOTAL, evictionGauge, AtomicLong::get)
            .description("Total cache evictions")
            .tag("cache", cacheName)
            .register(registry);
        
        // 当前缓存大小
        Gauge.builder(CACHE_SIZE_CURRENT, () -> size)
            .description("Current cache size")
            .tag("cache", cacheName)
            .baseUnit("entries")
            .register(registry);
        
        // 加载时间（标准化.seconds后缀）
        if (avgLoadTime != null) {
            Timer.builder(CACHE_LOAD_DURATION_SECONDS)
                .description("Cache entry load duration in seconds")
                .tag("cache", cacheName)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(avgLoadTime);
        }
    }
    
    /**
     * 记录会话指标（企业级版本）
     */
    public void recordSessionMetrics(int activeCount, long createdTotal, Duration avgDuration) {
        if (!isMetricEnabled("session")) return;
        
        // 当前活跃会话数
        Gauge.builder(SESSION_ACTIVE_CURRENT, () -> activeCount)
            .description("Current active session count")
            .register(registry);
        
        // 创建总数
        Counter.builder(SESSION_CREATED_TOTAL)
            .description("Total sessions created")
            .register(registry)
            .increment(createdTotal);
        
        // 平均持续时间（标准化）
        if (avgDuration != null) {
            Timer.builder(SESSION_DURATION_SECONDS)
                .description("Session duration in seconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(avgDuration);
        }
    }
    
    /**
     * 记录任务指标（企业级版本）
     */
    public void recordTaskMetrics(int activeCount, long createdTotal, Duration avgDuration) {
        if (!isMetricEnabled("task")) return;
        
        Gauge.builder(TASK_ACTIVE_CURRENT, () -> activeCount)
            .description("Current active task count")
            .register(registry);
        
        Counter.builder(TASK_CREATED_TOTAL)
            .description("Total tasks created")
            .register(registry)
            .increment(createdTotal);
        
        if (avgDuration != null) {
            Timer.builder(TASK_DURATION_SECONDS)
                .description("Task execution duration in seconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(avgDuration);
        }
    }
    
    // === HTTP和API指标（企业级） ===
    
    /**
     * 记录HTTP请求（企业级版本）
     */
    public Timer.Sample startHttpRequest() {
        if (!isMetricEnabled("http")) return Timer.start();
        return Timer.start(registry);
    }
    
    public void recordHttpRequest(Timer.Sample sample, 
                                 String method, 
                                 String path, 
                                 int statusCode) {
        if (!isMetricEnabled("http") || sample == null) return;
        
        sample.stop(Timer.builder(HTTP_REQUEST_DURATION_SECONDS)
            .description("HTTP request duration in seconds")
            .tag("method", method)
            .tag("path", sanitizePath(path))
            .tag("status", String.valueOf(statusCode))
            .tag("status.class", statusCode / 100 + "xx")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry));
    }
    
    /**
     * 记录API延迟（企业级版本）
     */
    public void recordApiLatency(String endpoint, Duration latency) {
        if (!isMetricEnabled("api")) return;
        
        Timer.builder(API_LATENCY_SECONDS)
            .description("API endpoint latency in seconds")
            .tag("endpoint", endpoint)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
            .record(latency);
    }
    
    /**
     * 记录数据库查询（企业级版本）
     */
    public void recordDbQuery(String operation, String table, Duration duration, boolean success) {
        if (!isMetricEnabled("db")) return;
        
        Timer.builder(DB_QUERY_DURATION_SECONDS)
            .description("Database query duration in seconds")
            .tag("operation", operation)
            .tag("table", table)
            .tag("status", success ? "success" : "failure")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
            .record(duration);
    }
    
    /**
     * 记录错误（企业级版本）
     */
    public void recordError(String errorType, String component, Throwable error) {
        if (!isMetricEnabled("error")) return;
        
        enterpriseCounterCache.computeIfAbsent(
            ERROR_TOTAL + "." + errorType,
            k -> Counter.builder(ERROR_TOTAL)
                .description("Total error count")
                .tag("type", errorType)
                .tag("component", component)
                .tag("class", error != null ? error.getClass().getSimpleName() : "Unknown")
                .register(registry)
        ).increment();
    }
    
    /**
     * 记录CME重试统计（CT-006）
     */
    public void recordCmeRetryStats(int totalRetries, int successes, int exhausted) {
        if (!isMetricEnabled("cme")) return;
        
        // 总重试次数
        enterpriseCounterCache.computeIfAbsent(
            CME_RETRY_TOTAL,
            k -> Counter.builder(CME_RETRY_TOTAL)
                .description("Total CME retry attempts")
                .register(registry)
        ).increment(totalRetries);
        
        // 成功率
        if (totalRetries > 0) {
            double successRate = (double)(totalRetries - exhausted) / totalRetries;
            registerGauge(CME_RETRY_SUCCESS_RATE, () -> successRate);
        }
    }
    
    /**
     * 记录FIFO驱逐（CT-006）
     */
    public void recordFifoEviction(String cacheName, long evictedCount) {
        if (!isMetricEnabled("fifo")) return;
        
        enterpriseCounterCache.computeIfAbsent(
            FIFO_EVICTION_TOTAL + "." + cacheName,
            k -> Counter.builder(FIFO_EVICTION_TOTAL)
                .description("Total FIFO cache evictions")
                .tag("cache", cacheName)
                .tag("strategy", "FIFO")
                .register(registry)
        ).increment(evictedCount);
    }
    
    // === 企业级辅助方法 ===
    
    private boolean isMetricEnabled(String metricType) {
        // 采样率控制（企业级默认50%）
        return Math.random() < 0.5;
    }
    
    private String sanitizePath(String path) {
        // 移除高基数路径参数，避免标签爆炸
        return path.replaceAll("/\\d+", "/{id}")
                  .replaceAll("/[a-f0-9-]{36}", "/{uuid}")
                  .replaceAll("/[a-f0-9]{8,}", "/{hash}");
    }
    
    private String normalizeMetricName(String name) {
        // 自动添加.seconds后缀（时间类指标）
        if ((name.contains("duration") || name.contains("latency") || name.contains("time")) 
            && !name.endsWith(".seconds") && !name.endsWith(".millis")) {
            logger.warn("Metric '{}' should end with '.seconds' for time measurements", name);
            return name + ".seconds";
        }
        return name;
    }
    
    private void recordLegacyMetric(String legacyName, Duration duration, Tag... tags) {
        if (LEGACY_METRIC_MAPPING.containsKey(legacyName)) {
            logger.warn("Deprecated metric name '{}' detected. Please migrate to '{}' for Prometheus compatibility", 
                legacyName, LEGACY_METRIC_MAPPING.get(legacyName));
            
            Timer.builder(legacyName)
                .tags(Arrays.asList(tags))
                .tag("deprecated", "true")
                .register(registry)
                .record(duration);
        }
    }
    
    /**
     * 注册企业级系统指标
     */
    private void registerEnterpriseSystemMetrics() {
        // JVM内存使用（企业级）
        Gauge.builder("tfi.jvm.memory.used.bytes.total", Runtime.getRuntime(), 
            r -> r.totalMemory() - r.freeMemory())
            .description("JVM used memory in bytes")
            .register(registry);
        
        // JVM最大内存
        Gauge.builder("tfi.jvm.memory.max.bytes.total", Runtime.getRuntime(), Runtime::maxMemory)
            .description("JVM max memory in bytes")
            .register(registry);
        
        // 活跃线程数
        Gauge.builder("tfi.jvm.threads.total", Thread::activeCount)
            .description("Active thread count")
            .register(registry);
        
        // TFI组件状态
        Gauge.builder("tfi.component.status", () -> 1.0)
            .description("TFI component status (1=up, 0=down)")
            .tag("component", "core")
            .register(registry);
        
        // 企业级健康分数
        Gauge.builder("tfi.health.score", this::calculateHealthScore)
            .description("TFI health score (0-100)")
            .register(registry);
    }
}