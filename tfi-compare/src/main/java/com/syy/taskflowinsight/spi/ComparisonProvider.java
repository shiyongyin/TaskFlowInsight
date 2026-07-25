package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareInputException;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.InputViolation;

/**
 * 使用冻结runtime语义的对象比较能力提供者。
 *
 * <p>Provider可以自行拥有runtime，但必须在三参数入口按该runtime policy校验options；接口不提供
 * 忽略options的默认实现，避免自定义Provider静默绕过调用方请求和资源边界。</p>
 *
 * <p>实现通过Core {@code ProviderRegistry}的显式注册或ServiceLoader发现，并按
 * {@link PrioritizedProvider}排序。选择、freeze和缓存均属于Core Registry；本SPI不得建立私有fallback图。</p>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 * @see java.util.ServiceLoader
 */
public interface ComparisonProvider extends PrioritizedProvider {

    /**
     * 比较两个对象的差异
     *
     * @param before 变更前对象 (可以为null)
     * @param after 变更后对象 (可以为null)
     * @return canonical比较结果；无法完成时返回typed failed结果而不是null
     */
    CompareResult compare(Object before, Object after);

    /**
     * 使用显式options比较两个对象。
     *
     * <p>实现必须先按其immutable runtime policy验证options，再执行任何比较工作。默认实现只拒绝调用，
     * 不能回退到两参数入口而静默丢失options。</p>
     *
     * @param before 变更前对象
     * @param after 变更后对象
     * @param options 比较选项（不可变对象）
     * @return 比较结果
     */
    default CompareResult compare(Object before, Object after, CompareOptions options) {
        throw new CompareInputException(InputViolation.INVALID_INPUT_SHAPE);
    }

}
