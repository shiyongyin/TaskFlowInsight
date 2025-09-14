package com.syy.taskflowinsight.metrics;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 指标摘要
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Data
@Builder(builderMethodName = "builder")
public class MetricsSummary {
    
    private long changeTrackingCount;
    private long snapshotCreationCount;
    private long pathMatchCount;
    private double pathMatchHitRate;
    private long collectionSummaryCount;
    private long errorCount;
    
    private Duration avgChangeTrackingTime;
    private Duration avgSnapshotCreationTime;
    private Duration avgPathMatchTime;
    private Duration avgCollectionSummaryTime;
    
    private double healthScore;
    
    /**
     * 转换为Map格式
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        
        // 计数指标
        Map<String, Long> counts = new HashMap<>();
        counts.put("change_tracking", changeTrackingCount);
        counts.put("snapshot_creation", snapshotCreationCount);
        counts.put("path_match", pathMatchCount);
        counts.put("collection_summary", collectionSummaryCount);
        counts.put("errors", errorCount);
        map.put("counts", counts);
        
        // 性能指标
        Map<String, String> performance = new HashMap<>();
        performance.put("avg_change_tracking", formatDuration(avgChangeTrackingTime));
        performance.put("avg_snapshot_creation", formatDuration(avgSnapshotCreationTime));
        performance.put("avg_path_match", formatDuration(avgPathMatchTime));
        performance.put("avg_collection_summary", formatDuration(avgCollectionSummaryTime));
        map.put("performance", performance);
        
        // 效率指标
        Map<String, Object> efficiency = new HashMap<>();
        efficiency.put("path_match_hit_rate", String.format("%.2f%%", pathMatchHitRate * 100));
        efficiency.put("error_rate", String.format("%.2f%%", getErrorRate() * 100));
        map.put("efficiency", efficiency);
        
        // 健康指标
        map.put("health_score", String.format("%.1f", healthScore));
        
        return map;
    }
    
    /**
     * 获取错误率
     */
    public double getErrorRate() {
        long total = changeTrackingCount + snapshotCreationCount + 
                    pathMatchCount + collectionSummaryCount;
        return total > 0 ? (double) errorCount / total : 0.0;
    }
    
    /**
     * 格式化持续时间
     */
    private String formatDuration(Duration duration) {
        if (duration == null) {
            return "0ms";
        }
        
        long millis = duration.toMillis();
        if (millis < 1) {
            long micros = duration.toNanos() / 1000;
            if (micros < 1) {
                return duration.toNanos() + "ns";
            }
            return micros + "μs";
        }
        return millis + "ms";
    }
    
    // Explicit getters for IDE compatibility (in case Lombok annotation processing fails)
    public long getChangeTrackingCount() { return changeTrackingCount; }
    public long getSnapshotCreationCount() { return snapshotCreationCount; }
    public long getPathMatchCount() { return pathMatchCount; }
    public double getPathMatchHitRate() { return pathMatchHitRate; }
    public long getCollectionSummaryCount() { return collectionSummaryCount; }
    public long getErrorCount() { return errorCount; }
    public Duration getAvgChangeTrackingTime() { return avgChangeTrackingTime; }
    public Duration getAvgSnapshotCreationTime() { return avgSnapshotCreationTime; }
    public Duration getAvgPathMatchTime() { return avgPathMatchTime; }
    public Duration getAvgCollectionSummaryTime() { return avgCollectionSummaryTime; }
    public double getHealthScore() { return healthScore; }
    
    /**
     * 生成文本报告
     */
    public String toTextReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("TFI Metrics Summary\n");
        report.append("===================\n\n");
        
        report.append("Operation Counts:\n");
        report.append(String.format("  Change Tracking: %d\n", changeTrackingCount));
        report.append(String.format("  Snapshot Creation: %d\n", snapshotCreationCount));
        report.append(String.format("  Path Matching: %d\n", pathMatchCount));
        report.append(String.format("  Collection Summary: %d\n", collectionSummaryCount));
        report.append(String.format("  Errors: %d\n", errorCount));
        report.append("\n");
        
        report.append("Average Processing Times:\n");
        report.append(String.format("  Change Tracking: %s\n", formatDuration(avgChangeTrackingTime)));
        report.append(String.format("  Snapshot Creation: %s\n", formatDuration(avgSnapshotCreationTime)));
        report.append(String.format("  Path Matching: %s\n", formatDuration(avgPathMatchTime)));
        report.append(String.format("  Collection Summary: %s\n", formatDuration(avgCollectionSummaryTime)));
        report.append("\n");
        
        report.append("Efficiency Metrics:\n");
        report.append(String.format("  Path Match Hit Rate: %.2f%%\n", pathMatchHitRate * 100));
        report.append(String.format("  Error Rate: %.2f%%\n", getErrorRate() * 100));
        report.append("\n");
        
        report.append(String.format("Health Score: %.1f/100\n", healthScore));
        
        return report.toString();
    }
}