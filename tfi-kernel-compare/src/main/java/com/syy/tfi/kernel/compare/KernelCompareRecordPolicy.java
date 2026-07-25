package com.syy.tfi.kernel.compare;

/**
 * Compare Bridge 的不可变记录策略；只控制可选 canonical change detail 的前缀上限。
 *
 * @param maxRecordedChanges 单次最多尝试写入的 canonical change 数，范围 0..32；0 表示 summary-only
 * @since 4.0.0
 */
public record KernelCompareRecordPolicy(int maxRecordedChanges) {

    /** detail 前缀的最小允许条数；0 保持默认 summary-only。 */
    private static final int MIN_RECORDED_CHANGES = 0;
    /** detail 前缀的硬上限，防止 integration 绕过 Core 和 Kernel 的有界性。 */
    private static final int MAX_RECORDED_CHANGES = 32;
    /** Bridge 输入与策略拒绝的稳定错误码前缀。 */
    private static final String INVALID_INPUT_PREFIX = "KCS_E_1201";

    /** 在对象进入共享运行时前校验 detail 上限，避免请求期出现配置分支。 */
    public KernelCompareRecordPolicy {
        if (maxRecordedChanges < MIN_RECORDED_CHANGES || maxRecordedChanges > MAX_RECORDED_CHANGES) {
            throw new IllegalArgumentException(
                    INVALID_INPUT_PREFIX + ": maxRecordedChanges must be within [0, 32]");
        }
    }

    /**
     * 返回不创建 projection 的 summary-only 默认策略。
     *
     * @return {@code maxRecordedChanges=0} 的不可变策略
     */
    public static KernelCompareRecordPolicy defaults() {
        return new KernelCompareRecordPolicy(MIN_RECORDED_CHANGES);
    }
}
