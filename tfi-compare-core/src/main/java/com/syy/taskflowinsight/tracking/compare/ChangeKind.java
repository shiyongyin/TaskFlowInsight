package com.syy.taskflowinsight.tracking.compare;

/**
 * 变更事实的闭集；每个kind对应唯一合法的before/after side组合。
 *
 * @since 4.0.0
 */
public enum ChangeKind {
    /** 路径只在变更后存在。 */
    ADD,

    /** 路径只在变更前存在。 */
    REMOVE,

    /** 同一路径两侧均存在，但有界值事实不同。 */
    MODIFY,

    /** 同一值事实从一个typed路径移动到另一个typed路径。 */
    MOVE,

    /** 同一路径发生null与非null之间的切换。 */
    NULLNESS,

    /** root或字段的运行时类型不兼容，不能继续按同型内容比较。 */
    TYPE_MISMATCH
}
