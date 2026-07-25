/**
 * 线程本地 Context 生命周期与异步传播。
 *
 * <p>{@link com.syy.taskflowinsight.context.SafeContextManager} 是 ThreadLocal、identity registry、
 * 泄漏调度和运行态指标的唯一 owner；跨字段观测通过不可变
 * {@link com.syy.taskflowinsight.context.ContextMetrics} 发布，避免调用方拼接不同时间点的数据。
 * 嵌套任务的深度、LIFO 与终态同样只由 {@link com.syy.taskflowinsight.context.ManagedThreadContext}
 * 的真实 task stack 表达，避免按 threadId 维护无法关联任务身份的镜像状态。
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
package com.syy.taskflowinsight.context;
