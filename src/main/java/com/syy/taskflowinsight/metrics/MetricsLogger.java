package com.syy.taskflowinsight.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指标日志记录器
 * 定期记录TFI指标到日志中
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Component
@ConditionalOnProperty(name = "tfi.metrics.logging.enabled", havingValue = "true", matchIfMissing = false)
public class MetricsLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsLogger.class);
    private static final Logger metricsLogger = LoggerFactory.getLogger("TFI_METRICS");
    
    @Autowired(required = false)
    private TfiMetrics metrics;
    
    @Value("${tfi.metrics.logging.format:json}")
    private String loggingFormat = "json";
    
    @Value("${tfi.metrics.logging.include-zero:false}")
    private boolean includeZeroMetrics = false;
    
    private final AtomicLong logCount = new AtomicLong(0);
    private long startTime;
    
    @PostConstruct
    public void init() {
        startTime = System.currentTimeMillis();
        logger.info("Metrics logger initialized with format: {}", loggingFormat);
    }
    
    /**
     * 定期记录指标（默认每分钟）
     */
    @Scheduled(fixedDelayString = "${tfi.metrics.logging.interval:60000}")
    public void logMetrics() {
        if (metrics == null) {
            logger.debug("TfiMetrics not available, skipping metrics logging");
            return;
        }
        
        try {
            MetricsSummary summary = metrics.getSummary();
            
            // 如果没有任何操作且不包含零指标，则跳过
            if (!includeZeroMetrics && isEmptySummary(summary)) {
                logger.debug("No metrics to log (all zeros)");
                return;
            }
            
            long count = logCount.incrementAndGet();
            
            switch (loggingFormat.toLowerCase()) {
                case "json":
                    logJsonFormat(summary, count);
                    break;
                case "text":
                    logTextFormat(summary, count);
                    break;
                case "compact":
                    logCompactFormat(summary, count);
                    break;
                default:
                    logger.warn("Unknown logging format: {}, using json", loggingFormat);
                    logJsonFormat(summary, count);
            }
            
        } catch (Exception e) {
            logger.error("Error logging metrics", e);
        }
    }
    
    /**
     * 手动触发指标记录
     */
    public void logMetricsNow() {
        logMetrics();
    }
    
    /**
     * JSON格式日志
     */
    private void logJsonFormat(MetricsSummary summary, long count) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
        json.append("\"sequence\":").append(count).append(",");
        json.append("\"uptime_ms\":").append(System.currentTimeMillis() - startTime).append(",");
        
        // 操作计数
        json.append("\"operations\":{");
        json.append("\"change_tracking\":").append(summary.getChangeTrackingCount()).append(",");
        json.append("\"snapshot_creation\":").append(summary.getSnapshotCreationCount()).append(",");
        json.append("\"path_match\":").append(summary.getPathMatchCount()).append(",");
        json.append("\"collection_summary\":").append(summary.getCollectionSummaryCount()).append(",");
        json.append("\"errors\":").append(summary.getErrorCount());
        json.append("},");
        
        // 性能指标
        json.append("\"performance\":{");
        json.append("\"avg_change_tracking_ns\":").append(toNanos(summary.getAvgChangeTrackingTime())).append(",");
        json.append("\"avg_snapshot_creation_ns\":").append(toNanos(summary.getAvgSnapshotCreationTime())).append(",");
        json.append("\"avg_path_match_ns\":").append(toNanos(summary.getAvgPathMatchTime())).append(",");
        json.append("\"avg_collection_summary_ns\":").append(toNanos(summary.getAvgCollectionSummaryTime()));
        json.append("},");
        
        // 效率指标
        json.append("\"efficiency\":{");
        json.append("\"path_match_hit_rate\":").append(summary.getPathMatchHitRate()).append(",");
        json.append("\"error_rate\":").append(summary.getErrorRate());
        json.append("},");
        
        // 健康分数
        json.append("\"health_score\":").append(summary.getHealthScore());
        json.append("}");
        
        metricsLogger.info(json.toString());
    }
    
    /**
     * 文本格式日志
     */
    private void logTextFormat(MetricsSummary summary, long count) {
        metricsLogger.info("\n" + summary.toTextReport());
    }
    
    /**
     * 紧凑格式日志
     */
    private void logCompactFormat(MetricsSummary summary, long count) {
        metricsLogger.info("SEQ:{} CT:{} SC:{} PM:{} CS:{} ERR:{} HIT:{:.2f}% HEALTH:{:.1f}",
            count,
            summary.getChangeTrackingCount(),
            summary.getSnapshotCreationCount(),
            summary.getPathMatchCount(),
            summary.getCollectionSummaryCount(),
            summary.getErrorCount(),
            summary.getPathMatchHitRate() * 100,
            summary.getHealthScore()
        );
    }
    
    /**
     * 检查是否为空摘要
     */
    private boolean isEmptySummary(MetricsSummary summary) {
        return summary.getChangeTrackingCount() == 0 &&
               summary.getSnapshotCreationCount() == 0 &&
               summary.getPathMatchCount() == 0 &&
               summary.getCollectionSummaryCount() == 0 &&
               summary.getErrorCount() == 0;
    }
    
    /**
     * 转换为纳秒
     */
    private long toNanos(java.time.Duration duration) {
        return duration != null ? duration.toNanos() : 0L;
    }
    
    @PreDestroy
    public void shutdown() {
        // 记录最终指标
        logger.info("Shutting down metrics logger, recording final metrics");
        logMetrics();
        
        long totalLogs = logCount.get();
        long uptimeMs = System.currentTimeMillis() - startTime;
        logger.info("Metrics logger shutdown. Total logs: {}, Uptime: {} ms", totalLogs, uptimeMs);
    }
}