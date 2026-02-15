package com.syy.taskflowinsight.performance.monitor;

import lombok.Builder;
import lombok.Data;

/**
 * SLA（服务级别协议）配置
 * 定义性能指标的预期标准
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.1
 * @since 2025-01-13
 */
@Data
@Builder
public class SLAConfig {
    
    /**
     * 指标名称
     */
    private String metricName;
    
    /**
     * 最大延迟（毫秒）
     */
    private double maxLatencyMs;
    
    /**
     * 最小吞吐量（ops/s）
     */
    private double minThroughput;
    
    /**
     * 最大错误率（0-1）
     */
    private double maxErrorRate;
    
    /**
     * 是否启用
     */
    @Builder.Default
    private boolean enabled = true;
    
    /**
     * 告警级别
     */
    @Builder.Default
    private AlertLevel alertLevel = AlertLevel.WARNING;
    
    /**
     * 检查是否违反SLA
     */
    public boolean isViolated(MetricSnapshot snapshot) {
        if (!enabled) return false;
        
        // 检查延迟
        if (snapshot.getP95Micros() > maxLatencyMs * 1000) {
            return true;
        }
        
        // 检查吞吐量
        if (snapshot.getThroughput() < minThroughput) {
            return true;
        }
        
        // 检查错误率
        if (snapshot.getErrorRate() > maxErrorRate) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取违规描述
     */
    public String getViolationDescription(MetricSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        
        if (snapshot.getP95Micros() > maxLatencyMs * 1000) {
            sb.append(String.format("P95 latency %.2fms > %.2fms; ", 
                snapshot.getP95Micros() / 1000, maxLatencyMs));
        }
        
        if (snapshot.getThroughput() < minThroughput) {
            sb.append(String.format("Throughput %.0f < %.0f ops/s; ",
                snapshot.getThroughput(), minThroughput));
        }
        
        if (snapshot.getErrorRate() > maxErrorRate) {
            sb.append(String.format("Error rate %.2f%% > %.2f%%; ",
                snapshot.getErrorRate() * 100, maxErrorRate * 100));
        }
        
        return sb.toString();
    }
}