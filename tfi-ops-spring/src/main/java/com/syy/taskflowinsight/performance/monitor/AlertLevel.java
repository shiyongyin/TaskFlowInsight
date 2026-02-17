package com.syy.taskflowinsight.performance.monitor;

/**
 * 告警级别枚举。
 *
 * <p>按严重程度从低到高排列：{@link #INFO} → {@link #WARNING} → {@link #ERROR} → {@link #CRITICAL}。
 * 用于 {@link Alert} 的级别分类和 {@link PerformanceMonitor} 的日志级别映射。</p>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
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