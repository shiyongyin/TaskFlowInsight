package com.syy.taskflowinsight.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetricsSummary单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("MetricsSummary单元测试")
public class MetricsSummaryTest {
    
    private MetricsSummary summary;
    
    @BeforeEach
    void setUp() {
        summary = MetricsSummary.builder()
            .changeTrackingCount(100)
            .snapshotCreationCount(50)
            .pathMatchCount(200)
            .pathMatchHitRate(0.85)
            .collectionSummaryCount(30)
            .errorCount(5)
            .avgChangeTrackingTime(Duration.ofMillis(2))
            .avgSnapshotCreationTime(Duration.ofMillis(5))
            .avgPathMatchTime(Duration.ofNanos(500000))
            .avgCollectionSummaryTime(Duration.ofMillis(1))
            .healthScore(92.5)
            .build();
    }
    
    @Test
    @DisplayName("获取错误率")
    void testGetErrorRate() {
        double errorRate = summary.getErrorRate();
        
        // 5 errors / (100 + 50 + 200 + 30) = 5/380
        double expected = 5.0 / 380.0;
        assertThat(errorRate).isEqualTo(expected);
    }
    
    @Test
    @DisplayName("转换为Map格式")
    void testToMap() {
        Map<String, Object> map = summary.toMap();
        
        assertThat(map).containsKeys("counts", "performance", "efficiency", "health_score");
        
        @SuppressWarnings("unchecked")
        Map<String, Long> counts = (Map<String, Long>) map.get("counts");
        assertThat(counts).containsEntry("change_tracking", 100L);
        assertThat(counts).containsEntry("snapshot_creation", 50L);
        assertThat(counts).containsEntry("path_match", 200L);
        assertThat(counts).containsEntry("collection_summary", 30L);
        assertThat(counts).containsEntry("errors", 5L);
        
        @SuppressWarnings("unchecked")
        Map<String, String> performance = (Map<String, String>) map.get("performance");
        assertThat(performance).containsKey("avg_change_tracking");
        assertThat(performance).containsKey("avg_snapshot_creation");
        assertThat(performance).containsKey("avg_path_match");
        assertThat(performance).containsKey("avg_collection_summary");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> efficiency = (Map<String, Object>) map.get("efficiency");
        assertThat(efficiency).containsKey("path_match_hit_rate");
        assertThat(efficiency).containsKey("error_rate");
        
        assertThat(map.get("health_score")).isEqualTo("92.5");
    }
    
    @Test
    @DisplayName("生成文本报告")
    void testToTextReport() {
        String report = summary.toTextReport();
        
        assertThat(report).contains("TFI Metrics Summary");
        assertThat(report).contains("Operation Counts:");
        assertThat(report).contains("Change Tracking: 100");
        assertThat(report).contains("Snapshot Creation: 50");
        assertThat(report).contains("Path Matching: 200");
        assertThat(report).contains("Collection Summary: 30");
        assertThat(report).contains("Errors: 5");
        
        assertThat(report).contains("Average Processing Times:");
        assertThat(report).contains("Change Tracking: 2ms");
        assertThat(report).contains("Snapshot Creation: 5ms");
        assertThat(report).contains("Path Matching: 500μs");
        assertThat(report).contains("Collection Summary: 1ms");
        
        assertThat(report).contains("Efficiency Metrics:");
        assertThat(report).contains("Path Match Hit Rate: 85.00%");
        assertThat(report).contains("Error Rate:");
        
        assertThat(report).contains("Health Score: 92.5/100");
    }
    
    @Test
    @DisplayName("持续时间格式化")
    void testDurationFormatting() {
        // 测试纳秒格式
        MetricsSummary nanoSummary = MetricsSummary.builder()
            .avgChangeTrackingTime(Duration.ofNanos(500))
            .build();
        
        String report = nanoSummary.toTextReport();
        assertThat(report).contains("500ns");
        
        // 测试微秒格式
        MetricsSummary microSummary = MetricsSummary.builder()
            .avgSnapshotCreationTime(Duration.ofNanos(5000))
            .build();
        
        report = microSummary.toTextReport();
        assertThat(report).contains("5μs");
        
        // 测试毫秒格式
        MetricsSummary milliSummary = MetricsSummary.builder()
            .avgPathMatchTime(Duration.ofMillis(10))
            .build();
        
        report = milliSummary.toTextReport();
        assertThat(report).contains("10ms");
    }
    
    @Test
    @DisplayName("空摘要处理")
    void testEmptySummary() {
        MetricsSummary emptySummary = MetricsSummary.builder()
            .changeTrackingCount(0)
            .snapshotCreationCount(0)
            .pathMatchCount(0)
            .collectionSummaryCount(0)
            .errorCount(0)
            .pathMatchHitRate(0.0)
            .healthScore(100.0)
            .build();
        
        assertThat(emptySummary.getErrorRate()).isEqualTo(0.0);
        
        Map<String, Object> map = emptySummary.toMap();
        assertThat(map).isNotNull();
        
        String report = emptySummary.toTextReport();
        assertThat(report).contains("Health Score: 100.0/100");
    }
    
    @Test
    @DisplayName("高错误率场景")
    void testHighErrorRate() {
        MetricsSummary errorSummary = MetricsSummary.builder()
            .changeTrackingCount(10)
            .snapshotCreationCount(5)
            .pathMatchCount(10)
            .collectionSummaryCount(5)
            .errorCount(10)
            .pathMatchHitRate(0.5)
            .healthScore(25.0)
            .build();
        
        double errorRate = errorSummary.getErrorRate();
        assertThat(errorRate).isEqualTo(10.0 / 30.0); // 33.33%
        
        String report = errorSummary.toTextReport();
        assertThat(report).contains("Errors: 10");
        assertThat(report).contains("Health Score: 25.0/100");
    }
}