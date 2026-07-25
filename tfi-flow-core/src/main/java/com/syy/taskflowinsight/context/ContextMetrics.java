package com.syy.taskflowinsight.context;

import java.time.Instant;

/**
 * Context 运行态指标的一次不可变观测。
 *
 * <p>快照用于阻止调用方通过多个 getter 拼接不同时间点的数据。其中 active/created/closed
 * 必须来自同一次 registry 读取；其余累计计数与 executor 状态在该读取之后捕获，
 * {@code capturedAt} 标记本次观测完成的时间边界。
 *
 * @param activeContexts 当前活跃 Context 数
 * @param createdContexts 累计进入 registry 的 Context 数
 * @param closedContexts 累计离开 registry 的 Context 数
 * @param detectedLeaks 累计检测到的泄漏数
 * @param asyncTasks 累计提交的异步任务数
 * @param executorPoolSize 异步执行器当前线程数
 * @param executorQueueSize 异步执行器当前排队任务数
 * @param propagations 累计成功恢复的传播快照数
 * @param capturedAt 观测完成时间
 * @since 4.0.0
 * @see SafeContextManager#metrics()
 */
public record ContextMetrics(
        int activeContexts,
        long createdContexts,
        long closedContexts,
        long detectedLeaks,
        long asyncTasks,
        int executorPoolSize,
        int executorQueueSize,
        long propagations,
        Instant capturedAt) {
}
