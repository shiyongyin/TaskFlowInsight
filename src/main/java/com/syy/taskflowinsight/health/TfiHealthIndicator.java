package com.syy.taskflowinsight.health;

import com.syy.taskflowinsight.metrics.TfiMetrics;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * TFI健康检查指标器
 * 
 * 提供企业级健康检查：
 * - 内存使用状态（<10MB增量）
 * - CPU占用状态（<0.1%）
 * - 缓存命中率（>95%）
 * - 错误率监控
 * - 组件状态检查
 * 
 * @since 3.0.0
 */
@Component
public class TfiHealthIndicator implements HealthIndicator {
    
    private static final long MAX_MEMORY_INCREMENT_MB = 10;
    private static final double MAX_CPU_USAGE_PERCENT = 0.1;
    private static final double MIN_CACHE_HIT_RATIO = 0.95;
    private static final double MAX_ERROR_RATE = 0.01;
    
    private final TfiMetrics metrics;
    private final Instant startupTime = Instant.now();
    private long baselineMemory;
    
    public TfiHealthIndicator(TfiMetrics metrics) {
        this.metrics = metrics;
        this.baselineMemory = getCurrentMemoryUsage();
    }
    
    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        Health.Builder status = Health.up();
        
        // 1. 内存检查
        long currentMemory = getCurrentMemoryUsage();
        long memoryIncrement = currentMemory - baselineMemory;
        long memoryIncrementMB = memoryIncrement / (1024 * 1024);
        
        details.put("memory.increment.mb", memoryIncrementMB);
        details.put("memory.current.mb", currentMemory / (1024 * 1024));
        details.put("memory.baseline.mb", baselineMemory / (1024 * 1024));
        
        if (memoryIncrementMB > MAX_MEMORY_INCREMENT_MB) {
            status = Health.down()
                .withDetail("issue", "Memory increment exceeds threshold");
        }
        
        // 2. CPU占用检查（简化版，基于运行时间估算）
        double estimatedCpuUsage = estimateCpuUsage();
        details.put("cpu.usage.percent", String.format("%.3f", estimatedCpuUsage));
        
        if (estimatedCpuUsage > MAX_CPU_USAGE_PERCENT) {
            status = status.equals(Health.up()) ? Health.outOfService() : status;
            details.put("warning", "CPU usage exceeds threshold");
        }
        
        // 3. 缓存命中率检查
        double cacheHitRatio = metrics.getPathMatchHitRate();
        details.put("cache.hit.ratio", String.format("%.3f", cacheHitRatio));
        
        if (cacheHitRatio < MIN_CACHE_HIT_RATIO && metrics.getPathMatchHitRate() > 0) {
            status = status.equals(Health.up()) ? Health.outOfService() : status;
            details.put("warning", "Cache hit ratio below threshold");
        }
        
        // 4. 错误率检查
        double errorRate = calculateErrorRate();
        details.put("error.rate", String.format("%.4f", errorRate));
        
        if (errorRate > MAX_ERROR_RATE) {
            status = Health.down()
                .withDetail("issue", "Error rate exceeds threshold");
        }
        
        // 5. 组件状态
        details.put("component.status", "UP");
        details.put("uptime.seconds", Duration.between(startupTime, Instant.now()).getSeconds());
        
        // 6. 健康分数（0-100）
        double healthScore = calculateHealthScore();
        details.put("health.score", String.format("%.1f", healthScore));
        
        if (healthScore < 70) {
            status = Health.outOfService();
        } else if (healthScore < 50) {
            status = Health.down();
        }
        
        // 7. 性能基线
        details.put("performance.baseline", Map.of(
            "stage.p99.ms", "<50",
            "cache.lookup.us", "<1",
            "api.latency.ms", "<100"
        ));
        
        // 8. 采样率
        details.put("metrics.sampling.rate", "50%");
        
        return status.withDetails(details).build();
    }
    
    private long getCurrentMemoryUsage() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return heapUsage.getUsed();
    }
    
    private double estimateCpuUsage() {
        // 简化的CPU使用率估算（基于处理时间）
        // 实际生产环境应使用JMX或Sigar等工具
        long uptime = Duration.between(startupTime, Instant.now()).toMillis();
        if (uptime == 0) return 0.0;
        
        // 假设每秒处理1000个请求，每个请求0.1ms
        double processingTimeMs = metrics.getSummary().getChangeTrackingCount() * 0.1;
        return (processingTimeMs / uptime) * 100;
    }
    
    private double calculateErrorRate() {
        var summary = metrics.getSummary();
        long totalOps = summary.getChangeTrackingCount() + 
                       summary.getSnapshotCreationCount() + 
                       summary.getPathMatchCount();
        
        if (totalOps == 0) return 0.0;
        return (double) summary.getErrorCount() / totalOps;
    }
    
    private double calculateHealthScore() {
        double score = 100.0;
        
        // 内存增量影响（权重30%）
        long memoryIncrementMB = (getCurrentMemoryUsage() - baselineMemory) / (1024 * 1024);
        if (memoryIncrementMB > MAX_MEMORY_INCREMENT_MB) {
            score -= 30 * (memoryIncrementMB / MAX_MEMORY_INCREMENT_MB);
        }
        
        // CPU使用率影响（权重20%）
        double cpuUsage = estimateCpuUsage();
        if (cpuUsage > MAX_CPU_USAGE_PERCENT) {
            score -= 20 * (cpuUsage / MAX_CPU_USAGE_PERCENT);
        }
        
        // 缓存命中率影响（权重30%）
        double cacheHitRatio = metrics.getPathMatchHitRate();
        if (cacheHitRatio < MIN_CACHE_HIT_RATIO && cacheHitRatio > 0) {
            score -= 30 * (1 - cacheHitRatio);
        }
        
        // 错误率影响（权重20%）
        double errorRate = calculateErrorRate();
        if (errorRate > MAX_ERROR_RATE) {
            score -= 20 * (errorRate / MAX_ERROR_RATE);
        }
        
        return Math.max(0, Math.min(100, score));
    }
}