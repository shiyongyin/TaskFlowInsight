package com.syy.taskflowinsight.performance.dashboard;

import com.syy.taskflowinsight.performance.BenchmarkRunner;
import com.syy.taskflowinsight.performance.BenchmarkReport;
import com.syy.taskflowinsight.performance.monitor.PerformanceMonitor;
import com.syy.taskflowinsight.performance.monitor.PerformanceReport;
import com.syy.taskflowinsight.performance.monitor.SLAConfig;
import com.syy.taskflowinsight.performance.monitor.AlertListener;
import com.syy.taskflowinsight.performance.monitor.Alert;
import com.syy.taskflowinsight.performance.monitor.AlertLevel;
import com.syy.taskflowinsight.performance.monitor.MetricSnapshot;

import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 性能监控 Dashboard。
 * <p>
 * 提供性能监控的 Web 端点和可视化数据，需配置 {@code tfi.performance.dashboard.enabled=true}。
 * <p>
 * 访问路径：
 * <ul>
 *   <li>GET  /api/performance - 获取性能概览</li>
 *   <li>GET  /api/performance/report/{type} - 获取详细报告（full/benchmark/realtime）</li>
 *   <li>GET  /api/performance/history/{metric} - 获取历史数据</li>
 *   <li>GET  /api/performance/alerts - 获取告警信息</li>
 *   <li>POST /api/performance/benchmark/{type} - 运行基准测试</li>
 *   <li>POST /api/performance/sla/{operation} - 配置 SLA</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 3.0.0
 */
@RestController
@RequestMapping("/api/performance")
@ConditionalOnProperty(name = "tfi.performance.dashboard.enabled", havingValue = "true", matchIfMissing = true)
public class PerformanceDashboard implements AlertListener {
    
    private final Optional<PerformanceMonitor> monitor;
    private final Optional<BenchmarkRunner> benchmarkRunner;
    
    public PerformanceDashboard(Optional<PerformanceMonitor> monitor, 
                               Optional<BenchmarkRunner> benchmarkRunner) {
        this.monitor = monitor;
        this.benchmarkRunner = benchmarkRunner;
    }
    
    // Dashboard状态
    private final Map<String, Object> dashboardData = new ConcurrentHashMap<>();
    private final List<Alert> recentAlerts = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong totalAlerts = new AtomicLong(0);
    private volatile BenchmarkReport lastBenchmarkReport;
    
    @PostConstruct
    public void init() {
        if (monitor.isPresent()) {
            monitor.get().registerAlertListener(this);
        }
        updateDashboard();
    }
    
    /**
     * 获取性能概览。
     *
     * @return 包含 status、timestamp、metrics_summary、alerts_summary、system_health 的 Map
     */
    @GetMapping
    public Map<String, Object> overview() {
        updateDashboard();
        
        Map<String, Object> overview = new HashMap<>();
        overview.put("status", getSystemStatus());
        overview.put("timestamp", System.currentTimeMillis());
        overview.put("metrics_summary", getMetricsSummary());
        overview.put("alerts_summary", getAlertsSummary());
        overview.put("system_health", getSystemHealth());
        
        return overview;
    }
    
    /**
     * 获取详细性能报告。
     *
     * @param type 报告类型：full、benchmark、realtime
     * @return 报告 Map，未知类型时返回 error
     */
    @GetMapping("/report/{type}")
    public Map<String, Object> report(@PathVariable String type) {
        if ("full".equals(type)) {
            return getFullReport();
        } else if ("benchmark".equals(type)) {
            return getBenchmarkReport();
        } else if ("realtime".equals(type)) {
            return getRealtimeReport();
        }
        
        return Collections.singletonMap("error", "Unknown report type: " + type);
    }
    
    /**
     * 获取历史数据。
     *
     * @param metric 指标名，或 "all" 获取全部
     * @return 历史快照 Map
     */
    @GetMapping("/history/{metric}")
    public Map<String, Object> history(@PathVariable String metric) {
        if (monitor.isEmpty()) {
            return Collections.singletonMap("error", "Monitor not available");
        }
        
        Map<String, List<MetricSnapshot>> history = monitor.get().getHistory();
        
        if ("all".equals(metric)) {
            return new HashMap<>(history);
        }
        
        List<MetricSnapshot> metricHistory = history.get(metric);
        if (metricHistory == null) {
            return Collections.singletonMap("error", "Metric not found: " + metric);
        }
        
        return Collections.singletonMap(metric, metricHistory);
    }
    
    /**
     * 获取告警信息。
     *
     * @return 包含 total_alerts、recent_alerts、active_alerts、alerts_by_level 的 Map
     */
    @GetMapping("/alerts")
    public Map<String, Object> alerts() {
        Map<String, Object> alertsInfo = new HashMap<>();
        alertsInfo.put("total_alerts", totalAlerts.get());
        alertsInfo.put("recent_alerts", recentAlerts.stream()
            .limit(20)
            .collect(Collectors.toList()));
        
        if (monitor.isPresent()) {
            PerformanceReport report = monitor.get().getReport();
            alertsInfo.put("active_alerts", report.getActiveAlerts());
            alertsInfo.put("alerts_by_level", report.getAlertsByLevel());
        }
        
        return alertsInfo;
    }
    
    /**
     * 运行基准测试。
     *
     * @param type 测试类型：all、snapshot、tracking、path、collection、concurrent
     * @return 包含 status、report/result 的 Map
     */
    @PostMapping("/benchmark/{type}")
    public Map<String, Object> benchmark(@PathVariable String type) {
        if (benchmarkRunner.isEmpty()) {
            return Collections.singletonMap("error", "Benchmark runner not available");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("started", System.currentTimeMillis());
        result.put("type", type);
        
        try {
            if ("all".equals(type)) {
                lastBenchmarkReport = benchmarkRunner.get().runAll();
                result.put("status", "completed");
                result.put("report", lastBenchmarkReport.toMap());
            } else {
                // 运行特定测试
                var benchmarkResult = switch (type) {
                    case "snapshot" -> benchmarkRunner.get().benchmarkObjectSnapshot();
                    case "tracking" -> benchmarkRunner.get().benchmarkChangeTracking();
                    case "path" -> benchmarkRunner.get().benchmarkPathMatching();
                    case "collection" -> benchmarkRunner.get().benchmarkCollectionSummary();
                    case "concurrent" -> benchmarkRunner.get().benchmarkConcurrentTracking();
                    default -> null;
                };
                
                if (benchmarkResult != null) {
                    result.put("status", "completed");
                    result.put("result", benchmarkResult);
                } else {
                    result.put("status", "error");
                    result.put("message", "Unknown benchmark type: " + type);
                }
            }
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        result.put("completed", System.currentTimeMillis());
        return result;
    }
    
    /**
     * 配置 SLA。
     *
     * @param operation 操作名称
     * @param maxLatencyMs 最大延迟（毫秒），可选
     * @param minThroughput 最小吞吐量（ops/s），可选
     * @param maxErrorRate 最大错误率（0-1），可选
     * @return 配置结果 Map
     */
    @PostMapping("/sla/{operation}")
    public Map<String, Object> configureSLA(
            @PathVariable String operation,
            @RequestParam(required = false) Double maxLatencyMs,
            @RequestParam(required = false) Double minThroughput,
            @RequestParam(required = false) Double maxErrorRate) {
        
        if (monitor.isEmpty()) {
            return Collections.singletonMap("error", "Monitor not available");
        }
        
        SLAConfig config = SLAConfig.builder()
            .metricName(operation)
            .maxLatencyMs(maxLatencyMs != null ? maxLatencyMs : 10)
            .minThroughput(minThroughput != null ? minThroughput : 1000)
            .maxErrorRate(maxErrorRate != null ? maxErrorRate : 0.01)
            .build();
        
        monitor.get().configureSLA(operation, config);
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "configured");
        result.put("operation", operation);
        result.put("sla", config);
        return result;
    }
    
    /**
     * 清理告警。
     *
     * @param key 告警键，或 "all" 清理全部
     * @return 操作结果 Map
     */
    @DeleteMapping("/alerts/{key}")
    public Map<String, Object> clearAlerts(@PathVariable String key) {
        if (monitor.isEmpty()) {
            return Collections.singletonMap("error", "Monitor not available");
        }
        
        if ("all".equals(key)) {
            monitor.get().clearAllAlerts();
            recentAlerts.clear();
            return Collections.singletonMap("status", "all alerts cleared");
        } else {
            monitor.get().clearAlert(key);
            return Collections.singletonMap("status", "alert cleared: " + key);
        }
    }
    
    // ========== 内部方法 ==========
    
    /**
     * 更新Dashboard数据
     */
    private void updateDashboard() {
        if (monitor.isPresent()) {
            PerformanceReport report = monitor.get().getReport();
            dashboardData.put("current_report", report.toMap());
            dashboardData.put("last_update", System.currentTimeMillis());
        }
    }
    
    /**
     * 获取系统状态
     */
    private String getSystemStatus() {
        if (monitor.isEmpty()) return "UNKNOWN";
        
        PerformanceReport report = monitor.get().getReport();
        if (report.hasCriticalAlerts()) {
            return "CRITICAL";
        } else if (report.getAlertCount() > 0) {
            return "WARNING";
        } else {
            return "HEALTHY";
        }
    }
    
    /**
     * 获取指标摘要
     */
    private Map<String, Object> getMetricsSummary() {
        if (monitor.isEmpty()) return Collections.emptyMap();
        
        PerformanceReport report = monitor.get().getReport();
        Map<String, Object> summary = new HashMap<>();
        
        report.getMetrics().forEach((name, snapshot) -> {
            Map<String, Object> metricSummary = new HashMap<>();
            metricSummary.put("throughput", String.format("%.0f ops/s", snapshot.getThroughput()));
            metricSummary.put("p95_latency", String.format("%.2f μs", snapshot.getP95Micros()));
            metricSummary.put("success_rate", String.format("%.2f%%", snapshot.getSuccessRate() * 100));
            summary.put(name, metricSummary);
        });
        
        return summary;
    }
    
    /**
     * 获取告警摘要
     */
    private Map<String, Object> getAlertsSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", totalAlerts.get());
        
        if (monitor.isPresent()) {
            PerformanceReport report = monitor.get().getReport();
            summary.put("active", report.getAlertCount());
            
            Map<String, Long> byLevel = new HashMap<>();
            report.getAlertsByLevel().forEach((level, alerts) -> {
                byLevel.put(level.toString(), (long) alerts.size());
            });
            summary.put("by_level", byLevel);
        }
        
        return summary;
    }
    
    /**
     * 获取系统健康度
     */
    private Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        
        if (monitor.isPresent()) {
            PerformanceReport report = monitor.get().getReport();
            health.put("heap_usage", String.format("%.1f%%", report.getHeapUsagePercent()));
            health.put("thread_count", report.getThreadCount());
            
            // 计算健康评分（0-100）
            double score = 100;
            if (report.getHeapUsagePercent() > 90) score -= 30;
            else if (report.getHeapUsagePercent() > 75) score -= 15;
            
            if (report.getThreadCount() > 1000) score -= 20;
            else if (report.getThreadCount() > 500) score -= 10;
            
            score -= report.getAlertCount() * 5;
            score = Math.max(0, score);
            
            health.put("score", score);
            health.put("grade", getHealthGrade(score));
        }
        
        return health;
    }
    
    /**
     * 获取健康等级
     */
    private String getHealthGrade(double score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }
    
    /**
     * 获取完整报告
     */
    private Map<String, Object> getFullReport() {
        Map<String, Object> report = new HashMap<>();
        report.put("overview", overview());
        report.put("realtime", getRealtimeReport());
        report.put("benchmark", getBenchmarkReport());
        report.put("alerts", alerts());
        return report;
    }
    
    /**
     * 获取实时报告
     */
    private Map<String, Object> getRealtimeReport() {
        if (monitor.isEmpty()) {
            return Collections.singletonMap("error", "Monitor not available");
        }
        
        return monitor.get().getReport().toMap();
    }
    
    /**
     * 获取基准测试报告
     */
    private Map<String, Object> getBenchmarkReport() {
        if (lastBenchmarkReport == null) {
            return Collections.singletonMap("message", "No benchmark report available");
        }
        
        return lastBenchmarkReport.toMap();
    }
    
    @Override
    public void onAlert(Alert alert) {
        totalAlerts.incrementAndGet();
        recentAlerts.add(0, alert);
        
        // 保留最近100条告警
        while (recentAlerts.size() > 100) {
            recentAlerts.remove(recentAlerts.size() - 1);
        }
    }
}