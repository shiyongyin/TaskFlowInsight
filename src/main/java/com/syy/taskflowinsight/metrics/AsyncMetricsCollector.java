package com.syy.taskflowinsight.metrics;

import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步指标收集器
 * 
 * 批量收集和提交指标，避免影响主业务性能
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0  
 * @since 2025-01-24
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tfi.metrics.async.enabled", havingValue = "true", matchIfMissing = false)
public class AsyncMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    // 指标缓冲区
    private final ConcurrentLinkedQueue<MetricEvent> eventBuffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger bufferSize = new AtomicInteger(0);
    private final int maxBufferSize = ConfigDefaults.METRICS_BUFFER_SIZE;
    
    // 指标统计
    private final AtomicLong totalEvents = new AtomicLong(0);
    private final AtomicLong droppedEvents = new AtomicLong(0);
    private final AtomicLong processedEvents = new AtomicLong(0);
    private final AtomicLong lastFlushTime = new AtomicLong(System.currentTimeMillis());
    
    // 注册的指标
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gaugeValues = new ConcurrentHashMap<>();
    
    // 状态监控
    private volatile boolean enabled = true;
    
    public AsyncMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @PostConstruct
    public void init() {
        // 注册内部监控指标
        registerInternalMetrics();
        log.info("AsyncMetricsCollector initialized with buffer size: {}", maxBufferSize);
    }
    
    @PreDestroy
    public void destroy() {
        enabled = false;
        // 最后一次刷新
        flushMetrics();
        log.info("AsyncMetricsCollector destroyed");
    }
    
    /**
     * 记录计数器事件
     */
    public void recordCounter(String name, String... tags) {
        recordCounter(name, 1.0, tags);
    }
    
    /**
     * 记录计数器事件（指定增量）
     */
    public void recordCounter(String name, double increment, String... tags) {
        if (!enabled) return;
        
        MetricEvent event = new MetricEvent(MetricType.COUNTER, name, increment, tags);
        offerEvent(event);
    }
    
    /**
     * 记录定时器事件
     */
    public void recordTimer(String name, long durationNanos, String... tags) {
        if (!enabled) return;
        
        MetricEvent event = new MetricEvent(MetricType.TIMER, name, durationNanos, tags);
        offerEvent(event);
    }
    
    /**
     * 记录仪表盘值
     */
    public void recordGauge(String name, double value, String... tags) {
        if (!enabled) return;
        
        MetricEvent event = new MetricEvent(MetricType.GAUGE, name, value, tags);
        offerEvent(event);
    }
    
    /**
     * 提交事件到缓冲区
     */
    private void offerEvent(MetricEvent event) {
        totalEvents.incrementAndGet();
        
        // 检查缓冲区容量
        if (bufferSize.get() >= maxBufferSize) {
            droppedEvents.incrementAndGet();
            
            if (log.isWarnEnabled() && droppedEvents.get() % 1000 == 0) {
                log.warn("Metrics buffer overflow, dropped {} events total", droppedEvents.get());
            }
            return;
        }
        
        // 添加到缓冲区
        eventBuffer.offer(event);
        bufferSize.incrementAndGet();
    }
    
    /**
     * 定期刷新指标（每10秒）
     */
    @Scheduled(fixedDelay = 10000)
    @Async("taskExecutor")
    public void flushMetrics() {
        if (!enabled) return;
        
        long startTime = System.currentTimeMillis();
        int processedCount = 0;
        
        try {
            processedCount = ConcurrentRetryUtil.executeWithRetry(this::processBatchEvents);
            
        } catch (Exception e) {
            log.error("Error during metrics flush", e);
            
        } finally {
            lastFlushTime.set(System.currentTimeMillis());
            
            if (processedCount > 0) {
                long duration = System.currentTimeMillis() - startTime;
                log.debug("Flushed {} metrics events in {}ms", processedCount, duration);
            }
        }
    }
    
    /**
     * 批量处理事件
     */
    private int processBatchEvents() {
        int processed = 0;
        int batchSize = Math.min(bufferSize.get(), 500); // 每批最多500个
        
        for (int i = 0; i < batchSize; i++) {
            MetricEvent event = eventBuffer.poll();
            if (event == null) break;
            
            processEvent(event);
            processed++;
            bufferSize.decrementAndGet();
        }
        
        processedEvents.addAndGet(processed);
        return processed;
    }
    
    /**
     * 处理单个指标事件
     */
    private void processEvent(MetricEvent event) {
        try {
            switch (event.type) {
                case COUNTER:
                    processCounterEvent(event);
                    break;
                case TIMER:
                    processTimerEvent(event);
                    break;
                case GAUGE:
                    processGaugeEvent(event);
                    break;
            }
        } catch (Exception e) {
            log.warn("Error processing metric event: {}", event, e);
        }
    }
    
    /**
     * 处理计数器事件
     */
    private void processCounterEvent(MetricEvent event) {
        Counter counter = counters.computeIfAbsent(event.name, name -> {
            Counter.Builder builder = Counter.builder(name);
            if (event.tags.length > 0) {
                builder.tags(event.tags);
            }
            return builder.register(meterRegistry);
        });
        
        counter.increment(event.value);
    }
    
    /**
     * 处理定时器事件
     */
    private void processTimerEvent(MetricEvent event) {
        Timer timer = timers.computeIfAbsent(event.name, name -> {
            Timer.Builder builder = Timer.builder(name);
            if (event.tags.length > 0) {
                builder.tags(event.tags);
            }
            return builder.register(meterRegistry);
        });
        
        timer.record((long) event.value, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
    
    /**
     * 处理仪表盘事件
     */
    private void processGaugeEvent(MetricEvent event) {
        AtomicLong gaugeValue = gaugeValues.computeIfAbsent(event.name, name -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder(name, value, val -> val.get())
                .tags(event.tags)
                .register(meterRegistry);
            return value;
        });
        
        gaugeValue.set((long) event.value);
    }
    
    /**
     * 注册内部监控指标
     */
    private void registerInternalMetrics() {
        // 缓冲区大小
        Gauge.builder("tfi.metrics.buffer.size", bufferSize::get)
            .description("Metrics buffer current size")
            .register(meterRegistry);
        
        // 处理统计
        Gauge.builder("tfi.metrics.events.total", () -> totalEvents.get())
            .description("Total metric events received")
            .register(meterRegistry);
            
        Gauge.builder("tfi.metrics.events.processed", () -> processedEvents.get())
            .description("Total metric events processed")
            .register(meterRegistry);
            
        Gauge.builder("tfi.metrics.events.dropped", () -> droppedEvents.get())
            .description("Total metric events dropped due to buffer overflow")
            .register(meterRegistry);
        
        // 最后刷新时间
        Gauge.builder("tfi.metrics.last.flush.timestamp", () -> lastFlushTime.get())
            .description("Last metrics flush timestamp")
            .register(meterRegistry);
    }
    
    /**
     * 获取收集器状态
     */
    public CollectorStats getStats() {
        return new CollectorStats(
            totalEvents.get(),
            processedEvents.get(),
            droppedEvents.get(),
            bufferSize.get(),
            maxBufferSize,
            enabled
        );
    }
    
    /**
     * 指标事件
     */
    private static class MetricEvent {
        final MetricType type;
        final String name;
        final double value;
        final String[] tags;
        final Instant timestamp;
        
        MetricEvent(MetricType type, String name, double value, String... tags) {
            this.type = type;
            this.name = name;
            this.value = value;
            this.tags = tags != null ? tags : new String[0];
            this.timestamp = Instant.now();
        }
        
        @Override
        public String toString() {
            return String.format("MetricEvent{type=%s, name='%s', value=%f, tags=%d}", 
                type, name, value, tags.length);
        }
    }
    
    /**
     * 指标类型
     */
    private enum MetricType {
        COUNTER, TIMER, GAUGE
    }
    
    /**
     * 收集器统计信息
     */
    public static class CollectorStats {
        private final long totalEvents;
        private final long processedEvents;
        private final long droppedEvents;
        private final int bufferSize;
        private final int maxBufferSize;
        private final boolean enabled;
        
        public CollectorStats(long totalEvents, long processedEvents, long droppedEvents,
                            int bufferSize, int maxBufferSize, boolean enabled) {
            this.totalEvents = totalEvents;
            this.processedEvents = processedEvents;
            this.droppedEvents = droppedEvents;
            this.bufferSize = bufferSize;
            this.maxBufferSize = maxBufferSize;
            this.enabled = enabled;
        }
        
        public double getDropRate() {
            return totalEvents == 0 ? 0.0 : (double) droppedEvents / totalEvents;
        }
        
        public double getBufferUtilization() {
            return maxBufferSize == 0 ? 0.0 : (double) bufferSize / maxBufferSize;
        }
        
        // Getter methods for test access
        public long getTotalEvents() { return totalEvents; }
        public long getProcessedEvents() { return processedEvents; }
        public long getDroppedEvents() { return droppedEvents; }
        public int getBufferSize() { return bufferSize; }
        public int getMaxBufferSize() { return maxBufferSize; }
        public boolean isEnabled() { return enabled; }
        
        @Override
        public String toString() {
            return String.format("CollectorStats{total=%d, processed=%d, dropped=%d(%.2f%%), buffer=%d/%d(%.1f%%), enabled=%s}",
                totalEvents, processedEvents, droppedEvents, getDropRate() * 100,
                bufferSize, maxBufferSize, getBufferUtilization() * 100, enabled);
        }
    }
}