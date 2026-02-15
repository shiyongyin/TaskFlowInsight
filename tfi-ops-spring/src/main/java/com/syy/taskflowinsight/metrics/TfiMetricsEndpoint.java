package com.syy.taskflowinsight.metrics;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * TFI指标端点
 * 提供REST API访问TFI指标
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@RestController
@RequestMapping("/tfi/metrics")
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
public class TfiMetricsEndpoint {
    
    private final Optional<TfiMetrics> metrics;
    private final Optional<MetricsLogger> metricsLogger;
    
    public TfiMetricsEndpoint(Optional<TfiMetrics> metrics, Optional<MetricsLogger> metricsLogger) {
        this.metrics = metrics;
        this.metricsLogger = metricsLogger;
    }
    
    /**
     * 获取指标摘要
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getMetricsSummary() {
        if (metrics.isEmpty()) {
            return ResponseEntity.status(503)
                .body(Map.of("error", "Metrics not available"));
        }
        
        MetricsSummary summary = metrics.get().getSummary();
        return ResponseEntity.ok(summary.toMap());
    }
    
    /**
     * 获取文本格式的指标报告
     */
    @GetMapping(value = "/report", produces = "text/plain")
    public ResponseEntity<String> getMetricsReport() {
        if (metrics.isEmpty()) {
            return ResponseEntity.status(503)
                .body("Metrics not available");
        }
        
        MetricsSummary summary = metrics.get().getSummary();
        return ResponseEntity.ok(summary.toTextReport());
    }
    
    /**
     * 获取特定指标
     */
    @GetMapping("/metric/{name}")
    public ResponseEntity<Map<String, Object>> getSpecificMetric(@PathVariable String name) {
        if (metrics.isEmpty()) {
            return ResponseEntity.status(503)
                .body(Map.of("error", "Metrics not available"));
        }
        
        MetricsSummary summary = metrics.get().getSummary();
        Map<String, Object> result = new HashMap<>();
        
        switch (name.toLowerCase()) {
            case "change_tracking":
                result.put("count", summary.getChangeTrackingCount());
                result.put("avg_time", summary.getAvgChangeTrackingTime());
                break;
            case "snapshot":
                result.put("count", summary.getSnapshotCreationCount());
                result.put("avg_time", summary.getAvgSnapshotCreationTime());
                break;
            case "path_match":
                result.put("count", summary.getPathMatchCount());
                result.put("hit_rate", summary.getPathMatchHitRate());
                result.put("avg_time", summary.getAvgPathMatchTime());
                break;
            case "collection_summary":
                result.put("count", summary.getCollectionSummaryCount());
                result.put("avg_time", summary.getAvgCollectionSummaryTime());
                break;
            case "errors":
                result.put("count", summary.getErrorCount());
                result.put("error_rate", summary.getErrorRate());
                break;
            case "health":
                result.put("score", summary.getHealthScore());
                result.put("error_rate", summary.getErrorRate());
                result.put("path_match_hit_rate", summary.getPathMatchHitRate());
                break;
            default:
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unknown metric: " + name));
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 记录自定义指标
     */
    @PostMapping("/custom")
    public ResponseEntity<Map<String, Object>> recordCustomMetric(
            @RequestParam String name,
            @RequestParam double value) {
        
        if (metrics.isEmpty()) {
            return ResponseEntity.status(503)
                .body(Map.of("error", "Metrics not available"));
        }
        
        metrics.get().recordCustomMetric(name, value);
        
        return ResponseEntity.ok(Map.of(
            "status", "recorded",
            "metric", name,
            "value", value
        ));
    }
    
    /**
     * 增加自定义计数器
     */
    @PostMapping("/counter/{name}/increment")
    public ResponseEntity<Map<String, Object>> incrementCounter(@PathVariable String name) {
        if (metrics.isEmpty()) {
            return ResponseEntity.status(503)
                .body(Map.of("error", "Metrics not available"));
        }
        
        metrics.get().incrementCustomCounter(name);
        
        return ResponseEntity.ok(Map.of(
            "status", "incremented",
            "counter", name
        ));
    }
    
    /**
     * 触发立即记录日志
     */
    @PostMapping("/log")
    public ResponseEntity<Map<String, Object>> triggerLogging() {
        if (metricsLogger.isEmpty()) {
            return ResponseEntity.status(503)
                .body(Map.of("error", "Metrics logger not available"));
        }
        
        metricsLogger.get().logMetricsNow();
        
        return ResponseEntity.ok(Map.of(
            "status", "logged",
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * 重置自定义指标
     */
    @DeleteMapping("/custom")
    public ResponseEntity<Map<String, Object>> resetCustomMetrics() {
        if (metrics.isEmpty()) {
            return ResponseEntity.status(503)
                .body(Map.of("error", "Metrics not available"));
        }
        
        metrics.get().reset();
        
        return ResponseEntity.ok(Map.of(
            "status", "reset",
            "message", "Custom metrics cleared"
        ));
    }
    
    /**
     * 获取配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        
        config.put("metrics_available", metrics.isPresent());
        config.put("logger_available", metricsLogger.isPresent());
        
        if (metrics.isPresent()) {
            config.put("health_score", metrics.get().getSummary().getHealthScore());
        }
        
        return ResponseEntity.ok(config);
    }
}

/**
 * Spring Boot Actuator端点（可选）
 */
@Endpoint(id = "tfi-metrics")
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
class TfiMetricsActuatorEndpoint {
    
    private final Optional<TfiMetrics> metrics;
    
    public TfiMetricsActuatorEndpoint(Optional<TfiMetrics> metrics) {
        this.metrics = metrics;
    }
    
    @ReadOperation
    public Map<String, Object> metrics() {
        if (metrics.isEmpty()) {
            return Map.of("status", "unavailable");
        }
        
        return metrics.get().getSummary().toMap();
    }
    
    @WriteOperation
    public Map<String, Object> reset() {
        if (metrics.isEmpty()) {
            return Map.of("status", "unavailable");
        }
        
        metrics.get().reset();
        return Map.of("status", "reset");
    }
}