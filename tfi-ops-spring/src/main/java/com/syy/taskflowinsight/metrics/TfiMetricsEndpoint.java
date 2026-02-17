package com.syy.taskflowinsight.metrics;

import com.syy.taskflowinsight.actuator.support.TfiErrorResponse;
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
 * TFI 指标 REST 端点。
 *
 * <p>提供 HTTP API 访问 TFI 运行时指标（基于 Micrometer）。
 * 需要 {@code io.micrometer.core.instrument.MeterRegistry} 在 classpath 上。</p>
 *
 * <p>主要端点：</p>
 * <ul>
 *   <li>GET  /tfi/metrics/summary — 指标摘要</li>
 *   <li>GET  /tfi/metrics/report — 文本报告</li>
 *   <li>GET  /tfi/metrics/metric/{name} — 特定指标查询</li>
 *   <li>POST /tfi/metrics/custom — 记录自定义指标</li>
 *   <li>POST /tfi/metrics/counter/{name}/increment — 自增计数器</li>
 *   <li>POST /tfi/metrics/log — 触发日志记录</li>
 *   <li>DELETE /tfi/metrics/custom — 重置自定义指标</li>
 *   <li>GET  /tfi/metrics/config — 配置信息</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
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
     * 获取指标摘要。
     *
     * @return 指标摘要 Map，Metrics 不可用时返回 503
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getMetricsSummary() {
        if (metrics.isEmpty()) {
            return ResponseEntity.status(503).body(
                    TfiErrorResponse.unavailable("Metrics", "check tfi.metrics.enabled"));
        }

        MetricsSummary summary = metrics.get().getSummary();
        return ResponseEntity.ok(summary.toMap());
    }

    /**
     * 获取文本格式的指标报告。
     *
     * @return 纯文本报告，Metrics 不可用时返回 503
     */
    @GetMapping(value = "/report", produces = "text/plain")
    public ResponseEntity<?> getMetricsReport() {
        if (metrics.isEmpty()) {
            return ResponseEntity.status(503).body(
                    TfiErrorResponse.unavailable("Metrics", "check tfi.metrics.enabled"));
        }

        MetricsSummary summary = metrics.get().getSummary();
        return ResponseEntity.ok(summary.toTextReport());
    }

    /**
     * 获取特定指标。
     *
     * @param name 指标名：change_tracking / snapshot / path_match / collection_summary / errors / health
     * @return 指标数据 Map，未知名称返回 400，Metrics 不可用返回 503
     */
    @GetMapping("/metric/{name}")
    public ResponseEntity<?> getSpecificMetric(@PathVariable String name) {
        if (metrics.isEmpty()) {
            return ResponseEntity.status(503).body(
                    TfiErrorResponse.unavailable("Metrics", "check tfi.metrics.enabled"));
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
                return ResponseEntity.badRequest().body(
                        TfiErrorResponse.badRequest("Unknown metric: " + name, "valid names: change_tracking, snapshot, path_match, collection_summary, errors, health"));
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 记录自定义指标。
     *
     * @param name  指标名称
     * @param value 指标值
     * @return 操作确认，Metrics 不可用返回 503
     */
    @PostMapping("/custom")
    public ResponseEntity<?> recordCustomMetric(
            @RequestParam String name,
            @RequestParam double value) {

        if (metrics.isEmpty()) {
            return ResponseEntity.status(503).body(
                    TfiErrorResponse.unavailable("Metrics", "check tfi.metrics.enabled"));
        }

        metrics.get().recordCustomMetric(name, value);

        return ResponseEntity.ok(Map.of(
            "status", "recorded",
            "metric", name,
            "value", value
        ));
    }

    /**
     * 自增自定义计数器。
     *
     * @param name 计数器名称
     * @return 操作确认，Metrics 不可用返回 503
     */
    @PostMapping("/counter/{name}/increment")
    public ResponseEntity<?> incrementCounter(@PathVariable String name) {
        if (metrics.isEmpty()) {
            return ResponseEntity.status(503).body(
                    TfiErrorResponse.unavailable("Metrics", "check tfi.metrics.enabled"));
        }

        metrics.get().incrementCustomCounter(name);

        return ResponseEntity.ok(Map.of(
            "status", "incremented",
            "counter", name
        ));
    }

    /**
     * 触发立即记录指标日志。
     *
     * @return 操作确认，MetricsLogger 不可用返回 503
     */
    @PostMapping("/log")
    public ResponseEntity<?> triggerLogging() {
        if (metricsLogger.isEmpty()) {
            return ResponseEntity.status(503).body(
                    TfiErrorResponse.unavailable("MetricsLogger", "check tfi.metrics.logging.enabled"));
        }

        metricsLogger.get().logMetricsNow();

        return ResponseEntity.ok(Map.of(
            "status", "logged",
            "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 重置自定义指标。
     *
     * @return 操作确认，Metrics 不可用返回 503
     */
    @DeleteMapping("/custom")
    public ResponseEntity<?> resetCustomMetrics() {
        if (metrics.isEmpty()) {
            return ResponseEntity.status(503).body(
                    TfiErrorResponse.unavailable("Metrics", "check tfi.metrics.enabled"));
        }

        metrics.get().reset();

        return ResponseEntity.ok(Map.of(
            "status", "reset",
            "message", "Custom metrics cleared"
        ));
    }

    /**
     * 获取指标子系统的配置信息。
     *
     * @return 包含 metrics_available、logger_available、health_score 的配置 Map
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