package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

/**
 * 默认ComparisonProvider实现。
 *
 * <p>ServiceLoader会长期复用该实例，但即使宿主重复实例化provider，所有实例也只委托同一个
 * immutable runtime。输入错误必须原样越过SPI边界，调用方才能区分可修复的合同错误与执行故障。</p>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class DefaultComparisonProvider implements ComparisonProvider {

    /**
     * 比较两个对象
     *
     * @param before 变更前对象
     * @param after 变更后对象
     * @return 共享默认runtime产生的canonical比较结果
     */
    @Override
    public CompareResult compare(Object before, Object after) {
        return DefaultCompareRuntimeHolder.INSTANCE.engine().compare(before, after);
    }

    /**
     * 使用共享默认runtime校验options并执行比较。
     *
     * @param before 变更前对象
     * @param after 变更后对象
     * @param options 必须按默认runtime policy构造的不可变选项
     * @return canonical比较结果；输入错误以typed异常原样传播
     */
    @Override
    public CompareResult compare(Object before, Object after, CompareOptions options) {
        return DefaultCompareRuntimeHolder.INSTANCE.engine().compare(before, after, options);
    }

    /**
     * 使用最低默认优先级，使显式注册的实现可以确定覆盖ServiceLoader默认项。
     *
     * @return 0
     */
    @Override
    public int priority() {
        return 0;
    }

    /**
     * 返回不包含运行期状态的诊断标签。
     *
     * @return 固定Provider身份文本
     */
    @Override
    public String toString() {
        return "DefaultComparisonProvider{priority=0, type=default}";
    }
}
