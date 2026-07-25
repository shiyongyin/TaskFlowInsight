package com.syy.taskflowinsight.tracking.compare.internal;

/**
 * 比较请求中允许消耗预算的事件闭集。
 *
 * <p>事件种类固定在内核层，避免snapshot、diff和配对算法各自解释同一个上限。</p>
 */
enum BudgetEvent {

    /** built-in snapshot实际物化一个root、property或container member节点。 */
    SNAPSHOT_NODE,

    /** diff队列实际取出一个单侧或双侧节点并开始判定。 */
    DIFF_NODE,

    /** key、group或ordered匹配实际检查一个候选配对。 */
    PAIR_CANDIDATE,

    /** built-in snapshot实际准入一个Map entry、Collection element或array element。 */
    CONTAINER_MEMBER
}
