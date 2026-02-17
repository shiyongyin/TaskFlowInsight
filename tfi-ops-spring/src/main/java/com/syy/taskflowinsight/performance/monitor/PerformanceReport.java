package com.syy.taskflowinsight.performance.monitor;

import lombok.Data;

import java.util.*;

/**
 * 性能报告 DTO。
 *
 * <p>汇总当前系统的性能状态，包括各操作指标快照、JVM 堆/线程信息和活跃告警。
 * 由 {@link PerformanceMonitor#getReport()} 生成。</p>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@Data
public class PerformanceReport {

    /** 报告生成时间（epoch millis） */
    private long timestamp;

    /** 按操作名索引的指标快照 */
    private Map<String, MetricSnapshot> metrics = new HashMap<>();

    /** JVM 堆已用内存（MB） */
    private long heapUsedMB;

    /** JVM 堆最大内存（MB） */
    private long heapMaxMB;

    /** JVM 线程数 */
    private int threadCount;

    /** 当前活跃告警列表 */
    private List<Alert> activeAlerts = new ArrayList<>();

    /**
     * 添加操作指标快照。
     *
     * @param name     操作名称
     * @param snapshot 指标快照
     */
    public void addMetric(String name, MetricSnapshot snapshot) {
        metrics.put(name, snapshot);
    }

    /**
     * 获取堆内存使用百分比。
     *
     * @return 堆使用率百分比（0-100），{@code heapMaxMB == 0} 时返回 0
     */
    public double getHeapUsagePercent() {
        if (heapMaxMB == 0) return 0;
        return (double) heapUsedMB / heapMaxMB * 100;
    }

    /**
     * 判断是否存在 {@link AlertLevel#CRITICAL} 级别的告警。
     *
     * @return 存在严重告警时返回 {@code true}
     */
    public boolean hasCriticalAlerts() {
        return activeAlerts.stream().anyMatch(Alert::isCritical);
    }

    /**
     * 获取活跃告警数量。
     *
     * @return 告警数量
     */
    public int getAlertCount() {
        return activeAlerts.size();
    }

    /**
     * 按级别分组告警。
     *
     * @return 告警级别到告警列表的映射
     */
    public Map<AlertLevel, List<Alert>> getAlertsByLevel() {
        Map<AlertLevel, List<Alert>> grouped = new EnumMap<>(AlertLevel.class);
        for (Alert alert : activeAlerts) {
            grouped.computeIfAbsent(alert.getLevel(), k -> new ArrayList<>()).add(alert);
        }
        return grouped;
    }

    /**
     * 生成纯文本报告摘要。
     *
     * @return 包含时间、堆使用、线程数、指标和告警的文本
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
     * 转换为 JSON 友好的 Map。
     *
     * @return 包含 timestamp、heap、thread_count、metrics、active_alerts 的 Map
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