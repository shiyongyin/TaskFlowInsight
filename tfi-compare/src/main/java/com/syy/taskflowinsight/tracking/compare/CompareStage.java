package com.syy.taskflowinsight.tracking.compare;

/**
 * issue发生的稳定执行阶段；用于诊断归属，不携带实现类名或异常类型。
 *
 * @since 4.0.0
 */
public enum CompareStage {
    /** 输入校验、policy解析或执行计划构建阶段。 */
    PLAN,

    /** 单侧对象图捕获与typed path物化阶段。 */
    SNAPSHOT,

    /** 两侧事实配对、比较和change发布阶段。 */
    DIFF,

    /** baseline/after捕获及tracking批次编排阶段。 */
    TRACKING,

    /** Core Registry选择或provider委托阶段。 */
    PROVIDER,

    /** 无法归入外部能力边界的内核不变量检查阶段。 */
    INTERNAL
}
