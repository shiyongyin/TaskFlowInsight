package com.syy.taskflowinsight.performance.monitor;

import lombok.Data;

import java.util.*;

/**
 * 性能报告
 * 汇总当前系统的性能状态
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.1
 * @since 2025-01-13
 */
@Data
public class PerformanceReport {
    
    private long timestamp;
    private Map<String, MetricSnapshot> metrics = new HashMap<>();
    private long heapUsedMB;
    private long heapMaxMB;
    private int threadCount;
    private List<Alert> activeAlerts = new ArrayList<>();
    
    /**
     * 添加指标
     */
    public void addMetric(String name, MetricSnapshot snapshot) {
        metrics.put(name, snapshot);
    }
    
    /**
     * 获取堆内存使用率
     */
    public double getHeapUsagePercent() {
        if (heapMaxMB == 0) return 0;
        return (double) heapUsedMB / heapMaxMB * 100;
    }
    
    /**
     * 是否有严重告警
     */
    public boolean hasCriticalAlerts() {
        return activeAlerts.stream().anyMatch(Alert::isCritical);
    }
    
    /**
     * 获取告警数量
     */
    public int getAlertCount() {
        return activeAlerts.size();
    }
    
    /**
     * 获取按级别分组的告警
     */
    public Map<AlertLevel, List<Alert>> getAlertsByLevel() {
        Map<AlertLevel, List<Alert>> grouped = new EnumMap<>(AlertLevel.class);
        for (Alert alert : activeAlerts) {
            grouped.computeIfAbsent(alert.getLevel(), k -> new ArrayList<>()).add(alert);
        }
        return grouped;
    }
    
    /**
     * 生成摘要
     */
    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Performance Report ===\n");
        sb.append(String.format("Timestamp: %tc\n", new Date(timestamp)));
        sb.append(String.format("Heap: %dMB / %dMB (%.1f%%)\n", 
            heapUsedMB, heapMaxMB, getHeapUsagePercent()));
        sb.append(String.format("Threads: %d\n", threadCount));
        
        if (!metrics.isEmpty()) {
            sb.append("\nMetrics:\n");
            metrics.forEach((name, snapshot) -> {
                sb.append("  ").append(snapshot.format()).append("\n");
            });
        }
        
        if (!activeAlerts.isEmpty()) {
            sb.append("\nActive Alerts:\n");
            Map<AlertLevel, List<Alert>> byLevel = getAlertsByLevel();
            byLevel.forEach((level, alerts) -> {
                sb.append(String.format("  %s: %d\n", level, alerts.size()));
                alerts.forEach(alert -> {
                    sb.append("    - ").append(alert.getMessage()).append("\n");
                });
            });
        }
        
        return sb.toString();
    }
    
    /**
     * 转换为JSON友好的Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("timestamp", timestamp);
        map.put("heap_used_mb", heapUsedMB);
        map.put("heap_max_mb", heapMaxMB);
        map.put("heap_usage_percent", getHeapUsagePercent());
        map.put("thread_count", threadCount);
        
        Map<String, Map<String, Object>> metricsMap = new HashMap<>();
        metrics.forEach((name, snapshot) -> {
            Map<String, Object> metricMap = new HashMap<>();
            metricMap.put("total_ops", snapshot.getTotalOps());
            metricMap.put("success_rate", snapshot.getSuccessRate());
            metricMap.put("p50_micros", snapshot.getP50Micros());
            metricMap.put("p95_micros", snapshot.getP95Micros());
            metricMap.put("p99_micros", snapshot.getP99Micros());
            metricMap.put("throughput", snapshot.getThroughput());
            metricsMap.put(name, metricMap);
        });
        map.put("metrics", metricsMap);
        
        List<Map<String, Object>> alertsList = new ArrayList<>();
        activeAlerts.forEach(alert -> {
            Map<String, Object> alertMap = new HashMap<>();
            alertMap.put("key", alert.getKey());
            alertMap.put("level", alert.getLevel().toString());
            alertMap.put("message", alert.getMessage());
            alertMap.put("timestamp", alert.getTimestamp());
            alertsList.add(alertMap);
        });
        map.put("active_alerts", alertsList);
        
        return map;
    }
}