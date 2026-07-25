package com.syy.taskflowinsight.api;

/**
 * Optional Ops 用于证明单层装饰关系的类型合同。
 *
 * <p>该接口只允许 composition validation 确认 wrapper 委托当前 Context 的同一执行图，
 * 不是通用扩展点。禁止递归 wrapper chain、unwrap utility、Registry 或 runtime lookup。</p>
 *
 * @since 4.0.0
 */
public interface CompareOperationsDecorator extends CompareOperations {

    /**
     * 返回装饰器构造时绑定的同一基础比较边界。
     *
     * @return 当前 Context 的基础 {@link CompareOperations}，不可为 {@code null}
     */
    CompareOperations delegate();
}
