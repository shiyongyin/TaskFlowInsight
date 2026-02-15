package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.PatchFormat;
import com.syy.taskflowinsight.tracking.compare.ReportFormat;
import com.syy.taskflowinsight.annotation.ObjectType;
import com.syy.taskflowinsight.annotation.ValueObjectCompareStrategy;
import com.syy.taskflowinsight.spi.ComparisonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * 比较器构建器
 * <p>
 * 提供链式 API 配置比较选项，将常用配置安全映射到 CompareOptions。
 * 支持深度配置、字段过滤、相似度计算、报告生成等高级功能。
 * </p>
 *
 * <h3>使用示例</h3>
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
 * // 带相似度和报告
 * CompareResult result = TFI.comparator()
 *     .withSimilarity()
 *     .withReport()
 *     .compare(obj1, obj2);
 *
 * // 类型感知比较
 * CompareResult result = TFI.comparator()
 *     .typeAware()
 *     .detectMoves()
 *     .compare(list1, list2);
 * }</pre>
 *
 * <h3>配置覆盖规则</h3>
 * <p>
 * 后设覆盖前设。例如：
 * <pre>{@code
 * comparator()
 *     .withMaxDepth(5)      // 设置深度为 5
 *     .withMaxDepth(10)     // 覆盖为 10
 *     .compare(a, b);
 * }</pre>
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since v3.0.0
 */
public class ComparatorBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ComparatorBuilder.class);

    private final CompareService svc;
    private final ComparisonProvider provider;
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
     * 返回的构建器的所有配置方法都返回 this，但 compare() 方法始终返回 identical 结果。
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
     * 用于特性开关关闭时，所有 compare 调用返回 identical 结果。
     * </p>
     */
    private static class DisabledComparatorBuilder extends ComparatorBuilder {
        DisabledComparatorBuilder() {
            super(null);
        }

        @Override
        public CompareResult compare(Object a, Object b) {
            // 特性关闭时始终返回 identical
            return CompareResult.identical();
        }
    }

    /**
     * 忽略指定字段（不参与比较）
     * <p>
     * 自动启用深度比较 (enableDeepCompare=true)。
     * 后续调用会覆盖前面设置的忽略字段列表。
     * </p>
     *
     * @param fields 要忽略的字段名（支持路径，如 "user.address.city"）
     * @return this，支持链式调用
     */
    public ComparatorBuilder ignoring(String... fields) {
        if (fields != null && fields.length > 0) {
            optionsBuilder.enableDeepCompare(true);
            // 后设覆盖前设：直接替换整个列表
            optionsBuilder.ignoreFields(Arrays.asList(fields));
        }
        return this;
    }

    /**
     * 排除指定字段模式（不参与比较）
     * <p>
     * 与 ignoring 类似，但支持模式匹配（如 "*.id"）。
     * 自动启用深度比较 (enableDeepCompare=true)。
     * 后续调用会覆盖前面设置的排除字段列表。
     * </p>
     *
     * @param patterns 要排除的字段模式
     * @return this，支持链式调用
     */
    public ComparatorBuilder exclude(String... patterns) {
        if (patterns != null && patterns.length > 0) {
            optionsBuilder.enableDeepCompare(true);
            // 后设覆盖前设：直接替换整个列表
            optionsBuilder.excludeFields(Arrays.asList(patterns));
        }
        return this;
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
     * @throws IllegalArgumentException 如果 depth <= 0
     */
    public ComparatorBuilder withMaxDepth(int depth) {
        if (depth <= 0) {
            throw new IllegalArgumentException("Max depth must be positive, got: " + depth);
        }
        optionsBuilder.enableDeepCompare(true);
        // 后设覆盖前设
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
        optionsBuilder.calculateSimilarity(true);
        return this;
    }

    /**
     * 启用报告生成（Markdown 格式）
     * <p>
     * 自动启用相似度计算 (calculateSimilarity=true)。
     * 生成 Markdown 格式的变更报告 (reportFormat=MARKDOWN)。
     * 后续调用不会改变此设置。
     * </p>
     *
     * <p><b>注意：</b>此方法会同时启用相似度计算。</p>
     *
     * @return this，支持链式调用
     */
    public ComparatorBuilder withReport() {
        optionsBuilder.generateReport(true);
        optionsBuilder.reportFormat(ReportFormat.MARKDOWN);
        // 按照任务卡要求，withReport 同时启用相似度计算
        optionsBuilder.calculateSimilarity(true);
        return this;
    }

    /**
     * 启用补丁生成
     * <p>
     * 生成指定格式的补丁数据（JSON Patch 或 Merge Patch）。
     * 后续调用会覆盖前面设置的补丁格式。
     * </p>
     *
     * @param format 补丁格式（JSON_PATCH 或 MERGE_PATCH）
     * @return this，支持链式调用
     */
    public ComparatorBuilder withPatch(PatchFormat format) {
        if (format != null) {
            optionsBuilder.generatePatch(true);
            // 后设覆盖前设
            optionsBuilder.patchFormat(format);
        }
        return this;
    }

    /**
     * 指定比较策略名称
     * <p>
     * 用于选择自定义的比较策略（需预先注册）。
     * 后续调用会覆盖前面设置的策略名称。
     * </p>
     *
     * @param strategyName 策略名称
     * @return this，支持链式调用
     */
    public ComparatorBuilder withStrategyName(String strategyName) {
        // 后设覆盖前设
        optionsBuilder.strategyName(strategyName);
        return this;
    }

    /**
     * 包含 null 值变更
     * <p>
     * 默认情况下，null → null 的变更会被过滤。
     * 调用此方法后，会包含所有 null 变更。
     * 后续调用不会改变此设置（幂等操作）。
     * </p>
     *
     * @return this，支持链式调用
     */
    public ComparatorBuilder includeNulls() {
        optionsBuilder.includeNullChanges(true);
        return this;
    }

    /**
     * 启用移动检测（仅对 List 比较有效）
     * <p>
     * 在列表比较中检测元素移动操作（而非简单的删除+新增）。
     * 后续调用不会改变此设置（幂等操作）。
     * </p>
     *
     * @return this，支持链式调用
     */
    public ComparatorBuilder detectMoves() {
        optionsBuilder.detectMoves(true);
        return this;
    }

    /**
     * 启用类型感知比较
     * <p>
     * 根据对象的 @Entity/@ValueObject 注解自动选择比较策略。
     * 自动启用深度比较。
     * 后续调用不会改变此设置（幂等操作）。
     * </p>
     *
     * @return this，支持链式调用
     */
    public ComparatorBuilder typeAware() {
        optionsBuilder.typeAwareEnabled(true);
        optionsBuilder.enableDeepCompare(true);
        return this;
    }

    /**
     * 设置并行处理阈值
     * <p>
     * 当批量比较的对象数量超过此阈值时，将使用并行流处理。
     * 后续调用会覆盖前面设置的阈值。
     * </p>
     *
     * @param threshold 阈值（必须 > 0）
     * @return this，支持链式调用
     * @throws IllegalArgumentException 如果 threshold <= 0
     */
    public ComparatorBuilder withParallelThreshold(int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("Parallel threshold must be positive, got: " + threshold);
        }
        // 后设覆盖前设
        optionsBuilder.parallelThreshold(threshold);
        return this;
    }

    /**
     * 强制指定对象类型（高级用法）
     * <p>
     * 覆盖自动检测的对象类型（Entity 或 ValueObject）。
     * 后续调用会覆盖前面设置的类型。
     * </p>
     *
     * @param objectType 对象类型
     * @return this，支持链式调用
     */
    public ComparatorBuilder forceObjectType(ObjectType objectType) {
        // 后设覆盖前设
        optionsBuilder.forcedObjectType(objectType);
        return this;
    }

    /**
     * 强制指定 ValueObject 比较策略（高级用法）
     * <p>
     * 仅对 ValueObject 有效。
     * 后续调用会覆盖前面设置的策略。
     * </p>
     *
     * @param strategy ValueObject 比较策略
     * @return this，支持链式调用
     */
    public ComparatorBuilder forceStrategy(ValueObjectCompareStrategy strategy) {
        // 后设覆盖前设
        optionsBuilder.forcedStrategy(strategy);
        return this;
    }

    /**
     * 启用 Entity key 的非 @Key 属性追踪（仅对 Map<Entity, ?> 有效）
     * <p>
     * 当 Map 的 key 为 Entity 且 @Key 字段相同时，是否追踪非 @Key 属性的变化。
     * 默认关闭以避免噪音。
     * </p>
     *
     * @param track 是否追踪 Entity key 属性
     * @return this，支持链式调用
     */
    public ComparatorBuilder withTrackEntityKeyAttributes(boolean track) {
        optionsBuilder.trackEntityKeyAttributes(track);
        return this;
    }

    /**
     * 启用严格重复键检查（仅对 Set<Entity> 有效）
     * <p>
     * 当 Set 的元素为 Entity 且检测到重复 @Key 时：
     * - strict=false（默认）：DiagnosticLogger 记录 SET-002 警告，继续比较
     * - strict=true：抛出 IllegalArgumentException
     * </p>
     * <p>
     * 重复 @Key 通常意味着 equals/hashCode 实现与 @Key 不一致。
     * </p>
     *
     * @param strict 是否严格模式
     * @return this，支持链式调用
     */
    public ComparatorBuilder withStrictDuplicateKey(boolean strict) {
        optionsBuilder.strictDuplicateKey(strict);
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
        try {
            // 构建配置
            CompareOptions options = optionsBuilder.build();

            // Provider-aware 路由：优先使用 ComparisonProvider（当路由开启时由 TFI 注入）
            if (provider != null) {
                return provider.compare(a, b, options);
            }

            // 如果没有 CompareService 和 Provider，降级处理
            if (svc == null) {
                logger.warn("CompareService not available, using fallback comparison");
                // 使用 TFI.compare 的降级逻辑
                if (a == b) {
                    return CompareResult.identical();
                }
                if (a == null || b == null) {
                    return CompareResult.ofNullDiff(a, b);
                }
                if (!a.getClass().equals(b.getClass())) {
                    return CompareResult.ofTypeDiff(a, b);
                }
                // 无法进行深度比较，返回类型差异
                return CompareResult.ofTypeDiff(a, b);
            }

            // 委托 CompareService
            return svc.compare(a, b, options);

        } catch (Throwable t) {
            logger.error("Comparison failed: {}", t.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Comparison error details", t);
            }
            // 降级返回类型差异
            return CompareResult.ofTypeDiff(a, b);
        }
    }
}
