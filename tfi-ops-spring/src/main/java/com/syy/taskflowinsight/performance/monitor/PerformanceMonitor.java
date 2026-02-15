package com.syy.taskflowinsight.performance.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 实时性能监控器
 * 提供实时性能指标收集、监控和告警
 * 
 * 核心功能：
 * - 实时指标收集（TPS、延迟、内存、CPU）
 * - 性能SLA监控
 * - 告警触发机制
 * - 指标聚合和报告
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.1
 * @since 2025-01-13
 */
@Component
public class PerformanceMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);
    
    // 配置
    @Value("${tfi.performance.monitor.enabled:true}")
    private boolean enabled;
    
    @Value("${tfi.performance.monitor.interval-ms:5000}")
    private long monitorIntervalMs;
    
    @Value("${tfi.performance.monitor.history-size:100}")
    private int historySize;
    
    // JVM监控
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    
    // 指标收集器
    private final Map<String, MetricCollector> collectors = new ConcurrentHashMap<>();
    private final Map<String, List<MetricSnapshot>> history = new ConcurrentHashMap<>();
    
    // SLA配置
    private final Map<String, SLAConfig> slaConfigs = new ConcurrentHashMap<>();
    
    // 告警管理
    private final List<AlertListener> alertListeners = new ArrayList<>();
    private final Map<String, Alert> activeAlerts = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("Performance monitor disabled");
            return;
        }
        
        initializeDefaultSLAs();
        logger.info("Performance monitor initialized with interval: {}ms", monitorIntervalMs);
    }
    
    /**
     * 初始化默认SLA配置
     */
    private void initializeDefaultSLAs() {
        // 快照操作SLA
        slaConfigs.put("snapshot", SLAConfig.builder()
            .metricName("snapshot")
            .maxLatencyMs(10)
            .minThroughput(1000)
            .maxErrorRate(0.01)
            .build());
        
        // 变更追踪SLA
        slaConfigs.put("change_tracking", SLAConfig.builder()
            .metricName("change_tracking")
            .maxLatencyMs(5)
            .minThroughput(5000)
            .maxErrorRate(0.001)
            .build());
        
        // 路径匹配SLA
        slaConfigs.put("path_matching", SLAConfig.builder()
            .metricName("path_matching")
            .maxLatencyMs(1)
            .minThroughput(10000)
            .maxErrorRate(0.0001)
            .build());
    }
    
    /**
     * 记录操作开始
     */
    public Timer startTimer(String operation) {
        return new Timer(operation, this);
    }
    
    /**
     * 记录操作完成
     */
    void recordOperation(String operation, long durationNanos, boolean success) {
        if (!enabled) return;
        
        MetricCollector collector = collectors.computeIfAbsent(operation, 
            k -> new MetricCollector(k));
        collector.record(durationNanos, success);
        
        // 检查SLA
        checkSLA(operation, collector);
    }
    
    /**
     * 定期收集指标快照
     * 注：需要通过定时任务或外部调用触发
     */
    public void collectMetrics() {
        if (!enabled) return;
        
        long timestamp = System.currentTimeMillis();
        
        // 收集各操作指标
        collectors.forEach((name, collector) -> {
            MetricSnapshot snapshot = collector.snapshot(timestamp);
            
            List<MetricSnapshot> snapshots = history.computeIfAbsent(name, 
                k -> new ArrayList<>());
            snapshots.add(snapshot);
            
            // 限制历史大小
            if (snapshots.size() > historySize) {
                snapshots.remove(0);
            }
        });
        
        // 收集系统指标
        collectSystemMetrics(timestamp);
    }
    
    /**
     * 收集系统指标
     */
    private void collectSystemMetrics(long timestamp) {
        // 内存使用
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        double heapUsagePercent = (double) heapUsed / heapMax * 100;
        
        // 线程数
        int threadCount = threadBean.getThreadCount();
        
        // CPU使用率（简化版）
        double cpuUsage = getCpuUsage();
        
        logger.debug("System metrics - Heap: {:.2f}%, Threads: {}, CPU: {:.2f}%",
            heapUsagePercent, threadCount, cpuUsage);
        
        // 检查系统资源告警
        if (heapUsagePercent > 90) {
            raiseAlert("system.memory", AlertLevel.CRITICAL, 
                String.format("Heap usage critical: %.2f%%", heapUsagePercent));
        } else if (heapUsagePercent > 75) {
            raiseAlert("system.memory", AlertLevel.WARNING,
                String.format("Heap usage high: %.2f%%", heapUsagePercent));
        }
        
        if (threadCount > 1000) {
            raiseAlert("system.threads", AlertLevel.WARNING,
                String.format("High thread count: %d", threadCount));
        }
    }
    
    /**
     * 获取CPU使用率
     */
    private double getCpuUsage() {
        // 简化实现，实际应该跟踪历史数据计算差值
        return threadBean.getCurrentThreadCpuTime() / 1_000_000.0; // 转换为毫秒
    }
    
    /**
     * 检查SLA违规
     */
    private void checkSLA(String operation, MetricCollector collector) {
        SLAConfig sla = slaConfigs.get(operation);
        if (sla == null) return;
        
        MetricSnapshot current = collector.currentStats();
        
        // 检查延迟
        if (current.getP95Micros() > sla.getMaxLatencyMs() * 1000) {
            raiseAlert(operation + ".latency", AlertLevel.WARNING,
                String.format("%s P95 latency %.2fms exceeds SLA %.2fms",
                    operation, current.getP95Micros() / 1000.0, sla.getMaxLatencyMs()));
        }
        
        // 检查吞吐量
        if (current.getThroughput() < sla.getMinThroughput()) {
            raiseAlert(operation + ".throughput", AlertLevel.WARNING,
                String.format("%s throughput %.0f ops/s below SLA %.0f ops/s",
                    operation, current.getThroughput(), sla.getMinThroughput()));
        }
        
        // 检查错误率
        if (current.getErrorRate() > sla.getMaxErrorRate()) {
            raiseAlert(operation + ".errors", AlertLevel.ERROR,
                String.format("%s error rate %.2f%% exceeds SLA %.2f%%",
                    operation, current.getErrorRate() * 100, sla.getMaxErrorRate() * 100));
        }
    }
    
    /**
     * 触发告警
     */
    private void raiseAlert(String key, AlertLevel level, String message) {
        Alert alert = Alert.builder()
            .key(key)
            .level(level)
            .message(message)
            .timestamp(System.currentTimeMillis())
            .build();
        
        activeAlerts.put(key, alert);
        
        // 通知监听器
        for (AlertListener listener : alertListeners) {
            try {
                listener.onAlert(alert);
            } catch (Exception e) {
                logger.error("Alert listener error", e);
            }
        }
        
        // 记录日志
        switch (level) {
            case CRITICAL:
                logger.error("CRITICAL ALERT: {}", message);
                break;
            case ERROR:
                logger.error("ERROR ALERT: {}", message);
                break;
            case WARNING:
                logger.warn("WARNING ALERT: {}", message);
                break;
            default:
                logger.info("INFO ALERT: {}", message);
        }
    }
    
    /**
     * 获取当前性能报告
     */
    public PerformanceReport getReport() {
        PerformanceReport report = new PerformanceReport();
        report.setTimestamp(System.currentTimeMillis());
        
        // 添加操作指标
        collectors.forEach((name, collector) -> {
            report.addMetric(name, collector.currentStats());
        });
        
        // 添加系统指标
        report.setHeapUsedMB(memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024));
        report.setHeapMaxMB(memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024));
        report.setThreadCount(threadBean.getThreadCount());
        
        // 添加活跃告警
        report.setActiveAlerts(new ArrayList<>(activeAlerts.values()));
        
        return report;
    }
    
    /**
     * 获取历史指标
     */
    public Map<String, List<MetricSnapshot>> getHistory() {
        return new HashMap<>(history);
    }
    
    /**
     * 清理告警
     */
    public void clearAlert(String key) {
        activeAlerts.remove(key);
    }
    
    /**
     * 清理所有告警
     */
    public void clearAllAlerts() {
        activeAlerts.clear();
    }
    
    /**
     * 注册告警监听器
     */
    public void registerAlertListener(AlertListener listener) {
        alertListeners.add(listener);
    }
    
    /**
     * 配置SLA
     */
    public void configureSLA(String operation, SLAConfig config) {
        slaConfigs.put(operation, config);
    }
    
    /**
     * 重置指标
     */
    public void reset(String operation) {
        collectors.remove(operation);
        history.remove(operation);
    }
    
    /**
     * 重置所有指标
     */
    public void resetAll() {
        collectors.clear();
        history.clear();
        activeAlerts.clear();
    }
    
    /**
     * 计时器类
     */
    public static class Timer implements AutoCloseable {
        private final String operation;
        private final PerformanceMonitor monitor;
        private final long startNanos;
        private boolean success = true;
        
        Timer(String operation, PerformanceMonitor monitor) {
            this.operation = operation;
            this.monitor = monitor;
            this.startNanos = System.nanoTime();
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        @Override
        public void close() {
            long duration = System.nanoTime() - startNanos;
            monitor.recordOperation(operation, duration, success);
        }
    }
    
    /**
     * 指标收集器
     */
    static class MetricCollector {
        private final String name;
        private final LongAdder totalOps = new LongAdder();
        private final LongAdder successOps = new LongAdder();
        private final LongAdder errorOps = new LongAdder();
        private final List<Long> recentLatencies = Collections.synchronizedList(new ArrayList<>());
        private static final int SAMPLE_SIZE = 1000;
        
        MetricCollector(String name) {
            this.name = name;
        }
        
        void record(long durationNanos, boolean success) {
            totalOps.increment();
            
            if (success) {
                successOps.increment();
            } else {
                errorOps.increment();
            }
            
            // 保留最近的延迟样本
            recentLatencies.add(durationNanos);
            if (recentLatencies.size() > SAMPLE_SIZE) {
                recentLatencies.remove(0);
            }
        }
        
        MetricSnapshot snapshot(long timestamp) {
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
        
        MetricSnapshot currentStats() {
            return snapshot(System.currentTimeMillis());
        }
        
        private double percentile(List<Long> sorted, double p) {
            int index = (int) (sorted.size() * p);
            return sorted.get(Math.min(index, sorted.size() - 1));
        }
    }
}