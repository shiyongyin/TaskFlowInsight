package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.compare.internal.CompareResultReducer;
import com.syy.taskflowinsight.spi.ComparisonProvider;

/**
 * 比较器构建器
 * <p>
 * 提供链式 API 配置比较选项，将常用配置安全映射到 CompareOptions。
 * 支持深度配置、字段过滤和相似度计算等比较选项；输出在比较完成后由projection formatter负责。
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 基础比较
 * CompareResult result = TFI.comparator()
 *     .compare(obj1, obj2);
 *
 * // 深度比较，忽略字段
 * CompareResult result = TFI.comparator()
 *     .withMaxDepth(5)
 *     .ignoring("id", "createTime")
 *     .compare(obj1, obj2);
 *
 * // 请求算法支持的相似度事实
 * CompareResult result = TFI.comparator()
 *     .withSimilarity()
 *     .compare(obj1, obj2);
 *
 * // 类型感知比较
 * CompareResult result = TFI.comparator()
 *     .typeAware()
 *     .compare(list1, list2);
 * }</pre>
 *
 * <h2>配置覆盖规则</h2>
 * <p>后设覆盖前设。例如：</p>
 * <pre>{@code
 * comparator()
 *     .withMaxDepth(5)      // 设置深度为 5
 *     .withMaxDepth(10)     // 覆盖为 10
 *     .compare(a, b);
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since v3.0.0
 */
public class ComparatorBuilder {

    /** 未走SPI路由时委托的runtime兼容门面。 */
    private final CompareService svc;
    /** 已由Core registry选定的provider；存在时优先委托该入口。 */
    private final ComparisonProvider provider;
    /** 当前调用链尚未冻结的单次options事实。 */
    private CompareOptions.CompareOptionsBuilder optionsBuilder;

    /**
     * 构造函数（包内可见）
     *
     * @param svc CompareService 实例（可为 null，会在 compare 时降级处理）
     */
    ComparatorBuilder(CompareService svc) {
        this(svc, null);
    }

    ComparatorBuilder(CompareService svc, ComparisonProvider provider) {
        this.svc = svc;
        this.provider = provider;
        this.optionsBuilder = CompareOptions.builder();
    }

    /**
     * 创建禁用的构建器（用于特性开关关闭时）
     * <p>
     * 返回的构建器保留链式配置形态，但 compare() 会明确报告比较未执行；禁用不能等同于对象相等。
     * </p>
     *
     * @return 禁用的构建器实例
     */
    static ComparatorBuilder disabled() {
        return new DisabledComparatorBuilder();
    }

    /**
     * 禁用的构建器实现
     * <p>
     * 用于特性开关关闭时保持调用链兼容，同时把结果交给唯一 reducer 生成禁用真值。
     * </p>
     */
    private static class DisabledComparatorBuilder extends ComparatorBuilder {
        DisabledComparatorBuilder() {
            super(null);
        }

        @Override
        public CompareResult compare(Object a, Object b) {
            return CompareResultReducer.disabled();
        }
    }

    /**
     * 设置最大比较深度
     * <p>
     * 自动启用深度比较 (enableDeepCompare=true)。
     * 后续调用会覆盖前面设置的深度值。
     * </p>
     *
     * @param depth 最大深度（必须 > 0）
     * @return this，支持链式调用
     * @throws IllegalArgumentException 如果 {@code depth <= 0}
     */
    public ComparatorBuilder withMaxDepth(int depth) {
        if (depth <= 0) {
            throw new IllegalArgumentException("Max depth must be positive, got: " + depth);
        }
        optionsBuilder.maxDepth(depth);
        return this;
    }

    /**
     * 启用相似度计算
     * <p>
     * 使用 Jaccard 相似度算法计算对象相似度 (0.0 ~ 1.0)。
     * 后续调用不会改变此设置（幂等操作）。
     * </p>
     *
     * @return this，支持链式调用
     */
    public ComparatorBuilder withSimilarity() {
        optionsBuilder.computeSimilarity(true);
        return this;
    }

    /**
     * 使用比较模板
     * <p>
     * 应用预定义的比较模板（{@link ComparisonTemplate}），为常见场景提供开箱即用的配置。
     * </p>
     *
     * <p><b>重要：</b>模板只设置默认值，后续链式方法可覆盖这些值（后设覆盖前设）。</p>
     *
     * <p>使用示例：</p>
     * <pre>{@code
     * // 使用审计模板
     * CompareResult result = TFI.comparator()
     *     .useTemplate(ComparisonTemplate.AUDIT)
     *     .compare(obj1, obj2);
     *
     * // 模板 + 链式覆盖
     * CompareResult result = TFI.comparator()
     *     .useTemplate(ComparisonTemplate.AUDIT)
     *     .withMaxDepth(5)  // 覆盖模板的 maxDepth=10
     *     .ignoring("id")   // 额外配置
     *     .compare(obj1, obj2);
     * }</pre>
     *
     * @param template 比较模板（AUDIT/FAST/DEBUG）
     * @return this，支持链式调用
     */
    public ComparatorBuilder useTemplate(ComparisonTemplate template) {
        if (template != null) {
            // 应用模板配置（模板设置默认值，后续链式可覆盖）
            template.apply(optionsBuilder);
        }
        return this;
    }

    /**
     * 执行比较
     * <p>
     * 根据配置的选项比较两个对象，返回比较结果。
     * 如果 CompareService 不可用，会返回安全的降级结果。
     * </p>
     *
     * @param a 第一个对象
     * @param b 第二个对象
     * @return 比较结果；失败时返回稳定的错误结果
     */
    public CompareResult compare(Object a, Object b) {
        CompareOptions options = optionsBuilder.build();
        if (provider != null) {
            return provider.compare(a, b, options);
        }
        if (svc == null) {
            return CompareResultReducer.failure(
                    CompareProblemCode.PROVIDER_UNAVAILABLE,
                    CompareStage.PROVIDER);
        }
        return svc.compare(a, b, options);
    }
}
