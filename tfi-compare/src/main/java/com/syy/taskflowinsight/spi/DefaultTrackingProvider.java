package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.TrackingBatchScope;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareInputException;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;

import java.util.List;

/**
 * 基于默认canonical Compare runtime的Tracking资源提供者。
 *
 * <p>该实现只建立baseline/capture scope，不记录全局history、不吞基础设施fatal，
 * 也从不持有业务action。Comparison与Tracking默认入口共享同一冻结runtime，避免形成
 * 两套policy或算法图。</p>
 *
 * @since 4.0.0
 */
public class DefaultTrackingProvider implements TrackingProvider {

    /**
     * @param targets executor已完整校验的有序目标
     * @param options 默认runtime policy范围内的不可变选项
     * @return 不持有业务action的线程封闭scope
     * @throws CompareInputException options超出默认runtime policy
     */
    @Override
    public TrackingBatchScope begin(
            List<TrackingExecutor.Target> targets,
            CompareOptions options) {
        return DefaultCompareRuntimeHolder.INSTANCE.engine().beginTracking(targets, options);
    }

    /** @return 内置provider最低优先级，允许Core Registry选择显式实现 */
    @Override
    public int priority() {
        return 0;
    }

    /** @return 不包含target、result或runtime状态的固定身份文本 */
    @Override
    public String toString() {
        return "DefaultTrackingProvider{priority=0, type=default}";
    }
}
