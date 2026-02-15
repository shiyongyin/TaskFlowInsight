package com.syy.taskflowinsight.performance.monitor;

/**
 * 告警级别
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.1
 * @since 2025-01-13
 */
public enum AlertLevel {
    /**
     * 信息级别
     */
    INFO,
    
    /**
     * 警告级别
     */
    WARNING,
    
    /**
     * 错误级别
     */
    ERROR,
    
    /**
     * 严重级别
     */
    CRITICAL
}