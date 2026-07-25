package com.syy.tfi.kernel.model;

/**
 * Session 与 Stage 的生命周期终态；RUNNING 仅用于 owner 线程内的活动快照。
 */
public enum FlowStatus {
    /** 尚未冻结且仍处于 owner 线程生命周期内的活动快照状态。 */
    RUNNING,
    /** 已正常关闭且自身及后代均未被标记为 ERROR 的成功终态。 */
    OK,
    /** 错误事实、callback 失败或后代错误导致的终态；子 Stage 错误在关闭时向祖先传播。 */
    ERROR,
    /** 被显式清理或残留上下文替换、不会进入正常 Sink 发布流程的终态。 */
    ABANDONED
}
