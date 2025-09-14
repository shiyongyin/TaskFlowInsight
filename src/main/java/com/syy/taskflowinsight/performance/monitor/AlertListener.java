package com.syy.taskflowinsight.performance.monitor;

/**
 * 告警监听器接口
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.1
 * @since 2025-01-13
 */
@FunctionalInterface
public interface AlertListener {
    /**
     * 处理告警
     * 
     * @param alert 告警信息
     */
    void onAlert(Alert alert);
}