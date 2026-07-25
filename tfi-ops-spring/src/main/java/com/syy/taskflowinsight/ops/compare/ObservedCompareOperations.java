package com.syy.taskflowinsight.ops.compare;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.api.CompareOperationsDecorator;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 只观测 Spring direct compare 调用的 Operations 装饰器。
 *
 * <p>基础类型固定为当前 context 的 {@link CompareEngine}，而不是任意 Operations，防止装饰器链形成
 * 第三执行图。指标发布位于 Engine 返回之后，普通 Micrometer 故障只能被忽略，不能改写结果或异常。</p>
 *
 * @since 4.0.0
 */
public final class ObservedCompareOperations implements CompareOperationsDecorator {

    /** 固定消息日志器，不输出 meter 异常或比较业务事实。 */
    private static final Logger logger = LoggerFactory.getLogger(ObservedCompareOperations.class);
    /** 当前 context Runtime 导出的唯一基础 Engine。 */
    private final CompareEngine engine;
    /** 只消费 canonical 结果的低基数指标投影。 */
    private final CompareMetrics metrics;
    /** 当前装饰器是否已报告过指标故障，避免持续故障形成无界 WARN。 */
    private final AtomicBoolean metricsFailureReported = new AtomicBoolean();

    ObservedCompareOperations(CompareEngine engine, CompareMetrics metrics) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * 暴露构造时绑定的基础 Engine，仅供当前 Context 做组合一致性验证。
     *
     * @return 当前 Context Runtime 导出的同一 Engine
     */
    @Override
    public CompareOperations delegate() {
        return engine;
    }

    /**
     * 委托当前 context 的唯一 Engine，并在成功返回后发布结果指标。
     *
     * @param before 变更前对象，可为 {@code null}
     * @param after 变更后对象，可为 {@code null}
     * @return Engine 返回的同一个 canonical 结果实例
     */
    @Override
    public CompareResult compare(Object before, Object after) {
        CompareResult result = engine.compare(before, after);
        publishSafely(result);
        return result;
    }

    /**
     * 使用显式选项委托唯一 Engine；Engine 异常原样传播且不会产生伪结果指标。
     *
     * @param before 变更前对象，可为 {@code null}
     * @param after 变更后对象，可为 {@code null}
     * @param options 已受当前 policy 上界约束的单次选项，不可为 {@code null}
     * @return Engine 返回的同一个 canonical 结果实例
     */
    @Override
    public CompareResult compare(Object before, Object after, CompareOptions options) {
        CompareResult result = engine.compare(before, after, options);
        publishSafely(result);
        return result;
    }

    private void publishSafely(CompareResult result) {
        try {
            metrics.record(result);
        } catch (RuntimeException exception) {
            // 指标异常可能携带宿主细节，只记录固定分类，且绝不覆盖已经得到的业务结果。
            if (metricsFailureReported.compareAndSet(false, true)) {
                logger.warn("Compare metrics publication failed");
            }
        }
    }
}
