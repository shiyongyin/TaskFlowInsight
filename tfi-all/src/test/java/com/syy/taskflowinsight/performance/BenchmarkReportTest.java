package com.syy.taskflowinsight.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BenchmarkReport单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("BenchmarkReport单元测试")
public class BenchmarkReportTest {
    
    private BenchmarkReport report;
    
    @BeforeEach
    void setUp() {
        report = new BenchmarkReport();
        report.setStartTime(System.currentTimeMillis());
    }
    
    @Test
    @DisplayName("添加测试结果")
    void testAddResult() {
        List<Long> durations = Arrays.asList(1000L, 2000L, 3000L, 4000L, 5000L);
        BenchmarkResult result = BenchmarkResult.fromDurations("test1", durations);
        
        report.addResult("test1", result);
        
        assertThat(report.getResults()).hasSize(1);
        assertThat(report.getResults()).containsKey("test1");
        assertThat(report.getResults().get("test1")).isEqualTo(result);
    }
    
    @Test
    @DisplayName("计算统计信息")
    void testCalculateStatistics() {
        // 添加成功的结果
        report.addResult("test1", BenchmarkResult.fromDurations("test1", 
            Arrays.asList(1000L, 2000L, 3000L)));
        report.addResult("test2", BenchmarkResult.fromDurations("test2", 
            Arrays.asList(2000L, 3000L, 4000L)));
        
        // 添加跳过的结果
        report.addResult("test3", BenchmarkResult.skipped("Not available"));
        
        // 添加错误结果
        report.addResult("test4", BenchmarkResult.error("test4", "Failed"));
        
        report.setEndTime(System.currentTimeMillis() + 1000);
        report.calculateStatistics();
        
        Map<String, Object> summary = report.getSummary();
        
        assertThat(summary).containsKey("total_duration_ms");
        assertThat(summary.get("test_count")).isEqualTo(4);
        assertThat(summary.get("success_count")).isEqualTo(2);
        assertThat(summary.get("skipped_count")).isEqualTo(1);
        assertThat(summary.get("error_count")).isEqualTo(1);
        assertThat(summary).containsKey("avg_throughput");
    }
    
    @Test
    @DisplayName("环境信息捕获")
    void testEnvironmentCapture() {
        assertThat(report.getEnvironment()).isNotNull();
        assertThat(report.getEnvironment()).contains("Java:");
        assertThat(report.getEnvironment()).contains("JVM:");
        assertThat(report.getEnvironment()).contains("OS:");
        assertThat(report.getEnvironment()).contains("Cores:");
    }
    
    @Test
    @DisplayName("生成文本报告")
    void testGenerateTextReport() {
        setupSampleReport();
        
        String textReport = report.generateTextReport();
        
        assertThat(textReport).contains("TFI Performance Benchmark Report");
        assertThat(textReport).contains("Environment:");
        assertThat(textReport).contains("Execution:");
        assertThat(textReport).contains("Summary:");
        assertThat(textReport).contains("Results:");
        assertThat(textReport).contains("test1");
        assertThat(textReport).contains("Samples:");
        assertThat(textReport).contains("Throughput:");
    }
    
    @Test
    @DisplayName("生成JSON报告")
    void testToJson() {
        setupSampleReport();
        
        Map<String, Object> json = report.toJson();
        
        assertThat(json).containsKey("environment");
        assertThat(json).containsKey("start_time");
        assertThat(json).containsKey("end_time");
        assertThat(json).containsKey("summary");
        assertThat(json).containsKey("results");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> results = (Map<String, Object>) json.get("results");
        assertThat(results).containsKey("test1");
    }
    
    @Test
    @DisplayName("生成Markdown报告")
    void testGenerateMarkdownReport() {
        setupSampleReport();
        
        String markdown = report.generateMarkdownReport();
        
        assertThat(markdown).contains("# TFI Performance Benchmark Report");
        assertThat(markdown).contains("## Environment");
        assertThat(markdown).contains("## Execution");
        assertThat(markdown).contains("## Summary");
        assertThat(markdown).contains("## Results");
        assertThat(markdown).contains("### test1");
        assertThat(markdown).contains("| Metric | Value |");
        assertThat(markdown).contains("| Samples |");
        assertThat(markdown).contains("| Throughput |");
    }
    
    @Test
    @DisplayName("比较两个报告")
    void testCompareReports() {
        // 创建基线报告
        BenchmarkReport baseline = new BenchmarkReport();
        baseline.addResult("test1", BenchmarkResult.fromDurations("test1", 
            Arrays.asList(1000L, 2000L, 3000L)));
        baseline.addResult("test2", BenchmarkResult.fromDurations("test2", 
            Arrays.asList(2000L, 3000L, 4000L)));
        
        // 创建当前报告（性能提升）
        BenchmarkReport current = new BenchmarkReport();
        current.addResult("test1", BenchmarkResult.fromDurations("test1", 
            Arrays.asList(900L, 1800L, 2700L))); // 10%提升
        current.addResult("test2", BenchmarkResult.fromDurations("test2", 
            Arrays.asList(2200L, 3300L, 4400L))); // 10%下降
        
        String comparison = BenchmarkReport.compare(baseline, current);
        
        assertThat(comparison).contains("Performance Comparison");
        assertThat(comparison).contains("test1");
        assertThat(comparison).contains("test2");
        assertThat(comparison).contains("Mean");
        assertThat(comparison).contains("P95");
        assertThat(comparison).contains("Throughput");
    }
    
    @Test
    @DisplayName("处理缺失的测试结果")
    void testCompareWithMissingResult() {
        BenchmarkReport baseline = new BenchmarkReport();
        baseline.addResult("test1", BenchmarkResult.fromDurations("test1", 
            Arrays.asList(1000L, 2000L, 3000L)));
        baseline.addResult("test2", BenchmarkResult.fromDurations("test2", 
            Arrays.asList(2000L, 3000L, 4000L)));
        
        BenchmarkReport current = new BenchmarkReport();
        current.addResult("test1", BenchmarkResult.fromDurations("test1", 
            Arrays.asList(900L, 1800L, 2700L)));
        // test2缺失
        
        String comparison = BenchmarkReport.compare(baseline, current);
        
        assertThat(comparison).contains("test2: No current result");
    }
    
    @Test
    @DisplayName("处理非成功的测试结果")
    void testCompareWithNonSuccessfulResults() {
        BenchmarkReport baseline = new BenchmarkReport();
        baseline.addResult("test1", BenchmarkResult.fromDurations("test1", 
            Arrays.asList(1000L, 2000L, 3000L)));
        baseline.addResult("test2", BenchmarkResult.skipped("Not available"));
        
        BenchmarkReport current = new BenchmarkReport();
        current.addResult("test1", BenchmarkResult.error("test1", "Failed"));
        current.addResult("test2", BenchmarkResult.fromDurations("test2", 
            Arrays.asList(2000L, 3000L, 4000L)));
        
        String comparison = BenchmarkReport.compare(baseline, current);
        
        assertThat(comparison).contains("test1: Skipped (not successful in both runs)");
        assertThat(comparison).contains("test2: Skipped (not successful in both runs)");
    }
    
    /**
     * 设置示例报告
     */
    private void setupSampleReport() {
        report.addResult("test1", BenchmarkResult.fromDurations("test1", 
            Arrays.asList(1000L, 2000L, 3000L, 4000L, 5000L)));
        report.addResult("test2", BenchmarkResult.skipped("Not available"));
        
        report.setEndTime(report.getStartTime() + 1000);
        report.calculateStatistics();
    }
}