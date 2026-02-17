package com.syy.taskflowinsight.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.syy.taskflowinsight.actuator.support.TfiErrorResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 性能基准测试 REST 端点。
 *
 * <p>提供通过 HTTP API 触发、查询和比较基准测试的能力。
 * 需配置 {@code tfi.performance.endpoint.enabled=true} 启用。</p>
 *
 * <p>主要端点：</p>
 * <ul>
 *   <li>POST /tfi/benchmark/run — 运行所有基准测试（同步或异步）</li>
 *   <li>POST /tfi/benchmark/run/{testName} — 运行特定基准测试</li>
 *   <li>GET  /tfi/benchmark/status — 查询运行状态</li>
 *   <li>GET  /tfi/benchmark/report — 获取报告（JSON/text/markdown）</li>
 *   <li>GET  /tfi/benchmark/compare — 比较两个报告</li>
 *   <li>GET  /tfi/benchmark/list — 列出所有缓存报告</li>
 *   <li>DELETE /tfi/benchmark/clear — 清除缓存报告</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@RestController
@RequestMapping("/tfi/benchmark")
@ConditionalOnProperty(name = "tfi.performance.endpoint.enabled", havingValue = "true", matchIfMissing = false)
public class BenchmarkEndpoint {
    
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkEndpoint.class);
    
    private final Optional<BenchmarkRunner> runner;
    
    // 存储最近的报告
    private final Map<String, BenchmarkReport> reportCache = new ConcurrentHashMap<>();
    
    // 异步执行的任务
    private final Map<String, CompletableFuture<BenchmarkReport>> runningTasks = new ConcurrentHashMap<>();
    
    public BenchmarkEndpoint(Optional<BenchmarkRunner> runner) {
        this.runner = runner;
    }
    
    /**
     * 运行所有基准测试。
     *
     * @param async 是否异步执行，默认 false
     * @param tag   报告标签，默认 "default"
     * @return 同步时返回完整报告 JSON；异步时返回 202 Accepted
     */
    @PostMapping("/run")
    public ResponseEntity<?> runBenchmarks(
            @RequestParam(defaultValue = "false") boolean async,
            @RequestParam(defaultValue = "default") String tag) {
        
        if (runner.isEmpty()) {
            return ResponseEntity.status(503).body(
                    TfiErrorResponse.unavailable("BenchmarkRunner", "set tfi.performance.enabled=true"));
        }
        
        if (async) {
            // 异步执行
            if (runningTasks.containsKey(tag)) {
                return ResponseEntity.status(409)
                    .body(Map.of("error", "Benchmark already running with tag: " + tag));
            }
            
            CompletableFuture<BenchmarkReport> future = CompletableFuture.supplyAsync(() -> {
                BenchmarkReport report = runner.get().runAll();
                reportCache.put(tag, report);
                return report;
            });
            
            runningTasks.put(tag, future);
            
            // 清理完成的任务
            future.whenComplete((report, ex) -> runningTasks.remove(tag));
            
            return ResponseEntity.accepted()
                .body(Map.of(
                    "status", "started",
                    "tag", tag,
                    "message", "Benchmark started asynchronously"
                ));
        } else {
            // 同步执行
            BenchmarkReport report = runner.get().runAll();
            reportCache.put(tag, report);
            return ResponseEntity.ok(report.toJson());
        }
    }
    
    /**
     * 运行特定的基准测试。
     *
     * @param testName 测试名：change_tracking / object_snapshot / path_matching / collection_summary / concurrent_tracking
     * @return 测试结果或 400（未知测试名）
     */
    @PostMapping("/run/{testName}")
    public ResponseEntity<?> runSpecificBenchmark(
            @PathVariable String testName) {
        
        if (runner.isEmpty()) {
            return ResponseEntity.status(503).body(
                    TfiErrorResponse.unavailable("BenchmarkRunner", "set tfi.performance.enabled=true"));
        }
        
        BenchmarkResult result;
        
        switch (testName) {
            case "change_tracking":
                result = runner.get().benchmarkChangeTracking();
                break;
            case "object_snapshot":
                result = runner.get().benchmarkObjectSnapshot();
                break;
            case "path_matching":
                result = runner.get().benchmarkPathMatching();
                break;
            case "collection_summary":
                result = runner.get().benchmarkCollectionSummary();
                break;
            case "concurrent_tracking":
                result = runner.get().benchmarkConcurrentTracking();
                break;
            default:
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unknown test: " + testName));
        }
        
        if ("SUCCESS".equals(result.getStatus())) {
            return ResponseEntity.ok(Map.of(
                "test", testName,
                "result", result.toMicros()
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "test", testName,
                "status", result.getStatus(),
                "message", result.getMessage()
            ));
        }
    }
    
    /**
     * 获取指定标签的测试状态。
     *
     * @param tag 报告标签
     * @return 状态信息：running / completed / not_found / failed
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @RequestParam(defaultValue = "default") String tag) {
        
        CompletableFuture<BenchmarkReport> future = runningTasks.get(tag);
        
        if (future == null) {
            // 检查是否有缓存的报告
            if (reportCache.containsKey(tag)) {
                return ResponseEntity.ok(Map.of(
                    "status", "completed",
                    "tag", tag,
                    "has_report", true
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "status", "not_found",
                    "tag", tag,
                    "message", "No benchmark found with this tag"
                ));
            }
        }
        
        if (future.isDone()) {
            try {
                future.get(1, TimeUnit.SECONDS);
                return ResponseEntity.ok(Map.of(
                    "status", "completed",
                    "tag", tag,
                    "has_report", true
                ));
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of(
                    "status", "failed",
                    "tag", tag,
                    "error", e.getMessage()
                ));
            }
        } else {
            return ResponseEntity.ok(Map.of(
                "status", "running",
                "tag", tag
            ));
        }
    }
    
    /**
     * 获取已完成的报告。
     *
     * @param tag    报告标签
     * @param format 输出格式：json / text / markdown
     * @return 报告内容，未找到返回 404
     */
    @GetMapping("/report")
    public ResponseEntity<?> getReport(
            @RequestParam(defaultValue = "default") String tag,
            @RequestParam(defaultValue = "json") String format) {
        
        BenchmarkReport report = reportCache.get(tag);
        
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        
        switch (format.toLowerCase()) {
            case "json":
                return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(report.toJson());
                    
            case "text":
                return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(report.generateTextReport());
                    
            case "markdown":
                return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(report.generateMarkdownReport());
                    
            default:
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unsupported format: " + format));
        }
    }
    
    /**
     * 比较两个已缓存的报告（均值、P95、吞吐量变化百分比）。
     *
     * @param baseline 基线报告标签
     * @param current  当前报告标签
     * @return 比较结果文本
     */
    @GetMapping("/compare")
    public ResponseEntity<String> compareReports(
            @RequestParam String baseline,
            @RequestParam String current) {
        
        BenchmarkReport baselineReport = reportCache.get(baseline);
        BenchmarkReport currentReport = reportCache.get(current);
        
        if (baselineReport == null) {
            return ResponseEntity.badRequest()
                .body("Baseline report not found: " + baseline);
        }
        
        if (currentReport == null) {
            return ResponseEntity.badRequest()
                .body("Current report not found: " + current);
        }
        
        String comparison = BenchmarkReport.compare(baselineReport, currentReport);
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body(comparison);
    }
    
    /**
     * 列出所有已缓存的报告和正在运行的任务。
     *
     * @return 包含 reports 和 running 的 Map
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listReports() {
        Map<String, Object> list = new HashMap<>();
        
        for (Map.Entry<String, BenchmarkReport> entry : reportCache.entrySet()) {
            BenchmarkReport report = entry.getValue();
            Map<String, Object> info = new HashMap<>();
            info.put("start_time", report.getStartTime());
            info.put("end_time", report.getEndTime());
            info.put("test_count", report.getSummary().get("test_count"));
            info.put("success_count", report.getSummary().get("success_count"));
            list.put(entry.getKey(), info);
        }
        
        return ResponseEntity.ok(Map.of(
            "reports", list,
            "running", runningTasks.keySet()
        ));
    }
    
    /**
     * 清除缓存的报告。
     *
     * @param tag 指定标签（可选），为空时清除全部
     * @return 操作结果
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearReports(
            @RequestParam(required = false) String tag) {
        
        if (tag != null) {
            reportCache.remove(tag);
            return ResponseEntity.ok(Map.of(
                "message", "Report cleared",
                "tag", tag
            ));
        } else {
            int count = reportCache.size();
            reportCache.clear();
            return ResponseEntity.ok(Map.of(
                "message", "All reports cleared",
                "count", count
            ));
        }
    }
}