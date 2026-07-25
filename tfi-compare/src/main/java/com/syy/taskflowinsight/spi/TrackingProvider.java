package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.TrackingBatchScope;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareInputException;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;

import java.util.List;

/**
 * Tracking baseline/capture资源提供者。
 *
 * <p>SPI刻意不接收业务action，也不提供线程或全局changes查询；action sequencing只属于final
 * {@link TrackingExecutor}，provider因此无法吞掉、重试或替换业务执行。</p>
 *
 * @since 4.0.0
 */
@FunctionalInterface
public interface TrackingProvider extends PrioritizedProvider {

    /**
     * 为完整target批次建立action前baseline资源。
     *
     * <p>targets已由executor一次性校验并防御复制；实现按输入顺序建立active/terminal slot，
     * 并在batch内共享一份baseline phase预算。</p>
     *
     * @param targets 已完整校验的有序目标
     * @param options 已受ComparePolicy约束的不可变选项
     * @return 线程封闭、single-capture且幂等close的batch scope
     * @throws CompareInputException options超出当前provider的immutable runtime policy
     */
    TrackingBatchScope begin(
            List<TrackingExecutor.Target> targets,
            CompareOptions options);
}
