package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.compare.CompareRuntime;

/**
 * 内置Comparison/Tracking provider共享的默认不可变对象图。
 *
 * <p>集中持有避免两个SPI各自形成默认策略、policy或执行状态owner；该类型不对外暴露选择能力，
 * provider选择仍完全属于Core Registry。</p>
 */
final class DefaultCompareRuntimeHolder {

    /** 两个内置provider委托的唯一纯Java冻结runtime。 */
    static final CompareRuntime INSTANCE = CompareRuntime.defaults();

    private DefaultCompareRuntimeHolder() {
    }
}
