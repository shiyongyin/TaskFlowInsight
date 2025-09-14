package com.syy.taskflowinsight.performance;

import lombok.Data;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 基准测试报告
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Data
public class BenchmarkReport {
    
    private long startTime;
    private long endTime;
    private Map<String, BenchmarkResult> results = new LinkedHashMap<>();
    private Map<String, Object> summary = new HashMap<>();
    private String environment;
    
    public BenchmarkReport() {
        captureEnvironment();
    }
    
    /**
     * 添加测试结果
     */
    public void addResult(String name, BenchmarkResult result) {
        results.put(name, result);
    }
    
    /**
     * 计算统计信息
     */
    public void calculateStatistics() {
        long totalDuration = endTime - startTime;
        summary.put("total_duration_ms", totalDuration);
        summary.put("test_count", results.size());
        
        int successCount = 0;
        int skippedCount = 0;
        int errorCount = 0;
        
        for (BenchmarkResult result : results.values()) {
            if ("SUCCESS".equals(result.getStatus())) {
                successCount++;
            } else if ("SKIPPED".equals(result.getStatus())) {
                skippedCount++;
            } else if ("ERROR".equals(result.getStatus())) {
                errorCount++;
            }
        }
        
        summary.put("success_count", successCount);
        summary.put("skipped_count", skippedCount);
        summary.put("error_count", errorCount);
        
        // 计算平均吞吐量
        double totalThroughput = results.values().stream()
            .filter(r -> "SUCCESS".equals(r.getStatus()))
            .mapToDouble(BenchmarkResult::getThroughput)
            .sum();
        
        if (successCount > 0) {
            summary.put("avg_throughput", totalThroughput / successCount);
        }
    }
    
    /**
     * 捕获环境信息
     */
    private void captureEnvironment() {
        StringBuilder env = new StringBuilder();
        env.append("Java: ").append(System.getProperty("java.version"));
        env.append(", JVM: ").append(System.getProperty("java.vm.name"));
        env.append(" ").append(System.getProperty("java.vm.version"));
        env.append(", OS: ").append(System.getProperty("os.name"));
        env.append(" ").append(System.getProperty("os.version"));
        env.append(", Cores: ").append(Runtime.getRuntime().availableProcessors());
        
        this.environment = env.toString();
    }
    
    /**
     * 生成文本报告
     */
    public String generateTextReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=".repeat(80)).append("\n");
        report.append("TFI Performance Benchmark Report\n");
        report.append("=".repeat(80)).append("\n\n");
        
        // 环境信息
        report.append("Environment:\n");
        report.append("  ").append(environment).append("\n\n");
        
        // 时间信息
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        report.append("Execution:\n");
        report.append("  Start: ").append(sdf.format(new Date(startTime))).append("\n");
        report.append("  End:   ").append(sdf.format(new Date(endTime))).append("\n");
        report.append("  Duration: ").append(summary.get("total_duration_ms")).append(" ms\n\n");
        
        // 汇总统计
        report.append("Summary:\n");
        report.append("  Total tests: ").append(summary.get("test_count")).append("\n");
        report.append("  Successful:  ").append(summary.get("success_count")).append("\n");
        report.append("  Skipped:     ").append(summary.get("skipped_count")).append("\n");
        report.append("  Failed:      ").append(summary.get("error_count")).append("\n");
        
        if (summary.containsKey("avg_throughput")) {
            report.append("  Avg throughput: ")
                .append(String.format("%.0f", summary.get("avg_throughput")))
                .append(" ops/sec\n");
        }
        report.append("\n");
        
        // 详细结果
        report.append("Results:\n");
        report.append("-".repeat(80)).append("\n");
        
        for (Map.Entry<String, BenchmarkResult> entry : results.entrySet()) {
            report.append(entry.getValue().format()).append("\n\n");
        }
        
        report.append("=".repeat(80)).append("\n");
        
        return report.toString();
    }
    
    /**
     * 生成JSON报告
     */
    public Map<String, Object> toJson() {
        Map<String, Object> json = new HashMap<>();
        
        json.put("environment", environment);
        json.put("start_time", startTime);
        json.put("end_time", endTime);
        json.put("summary", summary);
        
        Map<String, Object> resultsJson = new LinkedHashMap<>();
        for (Map.Entry<String, BenchmarkResult> entry : results.entrySet()) {
            BenchmarkResult result = entry.getValue();
            if ("SUCCESS".equals(result.getStatus())) {
                resultsJson.put(entry.getKey(), result.toMicros());
            } else {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("status", result.getStatus());
                errorResult.put("message", result.getMessage());
                resultsJson.put(entry.getKey(), errorResult);
            }
        }
        json.put("results", resultsJson);
        
        return json;
    }
    
    /**
     * 转换为Map（兼容Dashboard）
     */
    public Map<String, Object> toMap() {
        return toJson();
    }
    
    /**
     * 生成Markdown报告
     */
    public String generateMarkdownReport() {
        StringBuilder md = new StringBuilder();
        
        md.append("# TFI Performance Benchmark Report\n\n");
        
        md.append("## Environment\n");
        md.append("```\n").append(environment).append("\n```\n\n");
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        md.append("## Execution\n");
        md.append("- **Start**: ").append(sdf.format(new Date(startTime))).append("\n");
        md.append("- **End**: ").append(sdf.format(new Date(endTime))).append("\n");
        md.append("- **Duration**: ").append(summary.get("total_duration_ms")).append(" ms\n\n");
        
        md.append("## Summary\n");
        md.append("| Metric | Value |\n");
        md.append("|--------|-------|\n");
        md.append("| Total tests | ").append(summary.get("test_count")).append(" |\n");
        md.append("| Successful | ").append(summary.get("success_count")).append(" |\n");
        md.append("| Skipped | ").append(summary.get("skipped_count")).append(" |\n");
        md.append("| Failed | ").append(summary.get("error_count")).append(" |\n");
        
        if (summary.containsKey("avg_throughput")) {
            md.append("| Avg throughput | ")
                .append(String.format("%.0f", summary.get("avg_throughput")))
                .append(" ops/sec |\n");
        }
        md.append("\n");
        
        md.append("## Results\n\n");
        
        for (Map.Entry<String, BenchmarkResult> entry : results.entrySet()) {
            BenchmarkResult result = entry.getValue();
            md.append("### ").append(entry.getKey()).append("\n");
            
            if ("SUCCESS".equals(result.getStatus())) {
                md.append("| Metric | Value |\n");
                md.append("|--------|-------|\n");
                md.append("| Samples | ").append(result.getCount()).append(" |\n");
                md.append("| Min | ").append(String.format("%.2f μs", result.getMinNanos() / 1000)).append(" |\n");
                md.append("| Max | ").append(String.format("%.2f μs", result.getMaxNanos() / 1000)).append(" |\n");
                md.append("| Mean | ").append(String.format("%.2f μs", result.getMeanNanos() / 1000)).append(" |\n");
                md.append("| Median | ").append(String.format("%.2f μs", result.getMedianNanos() / 1000)).append(" |\n");
                md.append("| P95 | ").append(String.format("%.2f μs", result.getP95Nanos() / 1000)).append(" |\n");
                md.append("| P99 | ").append(String.format("%.2f μs", result.getP99Nanos() / 1000)).append(" |\n");
                md.append("| Throughput | ").append(String.format("%.0f ops/sec", result.getThroughput())).append(" |\n");
            } else {
                md.append("**Status**: ").append(result.getStatus()).append("\n");
                if (result.getMessage() != null) {
                    md.append("**Message**: ").append(result.getMessage()).append("\n");
                }
            }
            md.append("\n");
        }
        
        return md.toString();
    }
    
    /**
     * 比较两个报告
     */
    public static String compare(BenchmarkReport baseline, BenchmarkReport current) {
        StringBuilder comparison = new StringBuilder();
        
        comparison.append("Performance Comparison\n");
        comparison.append("======================\n\n");
        
        for (String testName : baseline.getResults().keySet()) {
            BenchmarkResult baseResult = baseline.getResults().get(testName);
            BenchmarkResult currResult = current.getResults().get(testName);
            
            if (currResult == null) {
                comparison.append(testName).append(": No current result\n");
                continue;
            }
            
            if (!"SUCCESS".equals(baseResult.getStatus()) || !"SUCCESS".equals(currResult.getStatus())) {
                comparison.append(testName).append(": Skipped (not successful in both runs)\n");
                continue;
            }
            
            double meanDiff = ((currResult.getMeanNanos() - baseResult.getMeanNanos()) / baseResult.getMeanNanos()) * 100;
            double p95Diff = ((currResult.getP95Nanos() - baseResult.getP95Nanos()) / baseResult.getP95Nanos()) * 100;
            double throughputDiff = ((currResult.getThroughput() - baseResult.getThroughput()) / baseResult.getThroughput()) * 100;
            
            comparison.append(String.format("%-20s: Mean %+.1f%%, P95 %+.1f%%, Throughput %+.1f%%\n",
                testName, meanDiff, p95Diff, throughputDiff));
            
            if (Math.abs(meanDiff) > 10) {
                comparison.append("  ⚠️ Significant change detected\n");
            }
        }
        
        return comparison.toString();
    }
}