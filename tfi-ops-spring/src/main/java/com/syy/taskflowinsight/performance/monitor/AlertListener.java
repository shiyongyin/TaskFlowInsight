package com.syy.taskflowinsight.performance.monitor;

/**
 * 告警监听器函数式接口。
 *
 * <p>通过 {@link PerformanceMonitor#registerAlertListener(AlertListener)} 注册后，
 * 每次触发告警时回调 {@link #onAlert(Alert)}。</p>
 *
 * <p>实现方注意：回调在告警触发线程中同步执行，不应阻塞。</p>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
@FunctionalInterface
public interface AlertListener {

    /**
     * 处理告警事件。
     *
     * @param alert 告警信息，不为 {@code null}
     */
    void onAlert(Alert alert);
}