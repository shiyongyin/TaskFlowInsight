package com.syy.tfi.kernel.spi;

import com.syy.tfi.kernel.model.FlowSession;

/**
 * 接收冻结的 Session 终态；实现必须线程安全，并为同步调用和下游设置有限 timeout。
 *
 * <p>不得在回调内重入同一 {@code KernelRuntime.close()}；Runtime 会在关闭前快速失败。</p>
 */
@FunctionalInterface
public interface FlowSink {

    /** 按配置顺序同步处理一个已结束且不可再修改的 Session。 */
    void accept(FlowSession session);
}
