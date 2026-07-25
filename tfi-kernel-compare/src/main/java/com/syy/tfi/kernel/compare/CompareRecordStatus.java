package com.syy.tfi.kernel.compare;

/**
 * 一次 Compare 到 Kernel 观测组合的结构化结果，区分未执行、summary 接纳和 detail 接纳程度。
 *
 * @since 4.0.0
 */
public enum CompareRecordStatus {
    /** Kernel 已无记录容量，因此比较没有执行，也没有产生任何 Record。 */
    SKIPPED_NO_RECORDING_CAPACITY,

    /** Summary 已接纳，且本次没有请求或无需写入 change detail。 */
    RECORDED_SUMMARY,

    /** Summary 与全部可用 change detail 均已接纳。 */
    RECORDED_DETAILS,

    /** Summary 已接纳，但 integration limit 或 Kernel 预算只允许保留 canonical detail 前缀。 */
    RECORDED_PARTIAL_DETAILS,

    /** Summary 已接纳，但 canonical projection 或 detail 映射发生普通失败。 */
    RECORDED_DETAIL_FAILURE,

    /** 比较已经执行，但 Kernel 拒绝 summary，因此没有尝试写入 detail。 */
    EXECUTED_NOT_RECORDED
}
