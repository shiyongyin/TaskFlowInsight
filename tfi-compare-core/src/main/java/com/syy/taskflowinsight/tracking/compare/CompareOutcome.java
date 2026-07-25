package com.syy.taskflowinsight.tracking.compare;

/**
 * 比较对业务事实能得出的结论；与执行是否完整正交，避免失败被压成“相同”。
 *
 * @since 4.0.0
 */
public enum CompareOutcome {
    /** 完整执行后证明两侧值事实相等。 */
    EQUAL,

    /** 至少保留或聚合了一条确定差异事实。 */
    DIFFERENT,

    /** 当前证据不足以安全证明相等或不同。 */
    INDETERMINATE
}
