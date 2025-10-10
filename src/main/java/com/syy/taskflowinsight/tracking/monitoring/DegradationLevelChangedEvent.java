package com.syy.taskflowinsight.tracking.monitoring;

import org.springframework.context.ApplicationEvent;

/**
 * 降级级别变更事件
 * 
 * 当降级级别发生变更时发布此事件，允许其他组件监听并响应
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-24
 */
public class DegradationLevelChangedEvent extends ApplicationEvent {
    
    private final DegradationLevel previousLevel;
    private final DegradationLevel currentLevel;
    private final String reason;
    private final SystemMetrics metrics;
    private final long timestamp;
    
    /**
     * 创建降级事件
     * 
     * @param source 事件源（通常是DegradationManager）
     * @param previousLevel 前一个级别
     * @param currentLevel 当前级别
     * @param reason 变更原因
     * @param metrics 触发变更的系统指标
     */
    public DegradationLevelChangedEvent(Object source, 
                                       DegradationLevel previousLevel,
                                       DegradationLevel currentLevel,
                                       String reason,
                                       SystemMetrics metrics) {
        super(source);
        this.previousLevel = previousLevel;
        this.currentLevel = currentLevel;
        this.reason = reason;
        this.metrics = metrics;
        this.timestamp = System.currentTimeMillis();
    }
    
    public DegradationLevel getPreviousLevel() {
        return previousLevel;
    }
    
    public DegradationLevel getCurrentLevel() {
        return currentLevel;
    }
    
    public String getReason() {
        return reason;
    }
    
    public SystemMetrics getMetrics() {
        return metrics;
    }
    
    public long getEventTimestamp() {
        return timestamp;
    }
    
    /**
     * 是否为降级（级别变严格）
     */
    public boolean isDegradation() {
        return currentLevel.isMoreRestrictiveThan(previousLevel);
    }
    
    /**
     * 是否为恢复（级别变宽松）
     */
    public boolean isRecovery() {
        return previousLevel.isMoreRestrictiveThan(currentLevel);
    }
    
    /**
     * 获取级别变化的严重程度（0-4）
     */
    public int getSeverityChange() {
        return Math.abs(currentLevel.ordinal() - previousLevel.ordinal());
    }
    
    @Override
    public String toString() {
        return String.format("DegradationLevelChangedEvent{%s->%s, reason='%s', %s, timestamp=%d}",
            previousLevel, currentLevel, reason, 
            isDegradation() ? "degradation" : "recovery",
            timestamp);
    }
}