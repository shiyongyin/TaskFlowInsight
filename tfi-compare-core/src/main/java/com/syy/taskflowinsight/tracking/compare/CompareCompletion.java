package com.syy.taskflowinsight.tracking.compare;

/**
 * 比较计划的执行完整性；该维度不能替代业务结论，也不能单独证明相等。
 *
 * @since 4.0.0
 */
public enum CompareCompletion {
    /** 所有计划分支都在预算内完成，结果可以是确定相等或确定不同。 */
    COMPLETE,

    /** 已触达限制或局部故障，但可能仍保留已确认的差异。 */
    PARTIAL,

    /** 尚未确认差异时发生非预期能力故障，业务结论不可确定。 */
    FAILED,

    /** policy在计划执行前关闭比较，不能解释为对象相等。 */
    DISABLED
}
