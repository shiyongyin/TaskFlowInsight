package com.syy.taskflowinsight.context;

/**
 * Context 的终止意图。
 *
 * <p>正常作用域结束、业务失败和基础设施强制回收必须显式区分，避免再从异常或 reason 字符串
 * 反推 Session 终态。该类型保持包内可见，防止内部生命周期状态扩散为公共 API。
 */
enum ContextOutcome {
    SUCCESS,
    FAILURE,
    FORCED_CLEANUP
}
