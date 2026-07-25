package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;

/**
 * 比较模板枚举
 * <p>
 * 为常见场景提供开箱即用的比较配置模板，降低配置心智负担。
 * 模板仅组合 {@link CompareOptions} 的常用选项；链式方法仍可覆盖模板默认值（后设覆盖前设）。
 * </p>
 *
 * <h2>模板说明</h2>
 * <ul>
 *   <li><b>AUDIT</b>（审计模式）：在默认深度边界内请求相似度，适用于审计、合规场景</li>
 *   <li><b>FAST</b>（快速模式）：只保留顶层比较，适用于性能敏感场景</li>
 *   <li><b>DEBUG</b>（调试模式）：保留默认最大深度并请求相似度，适用于开发调试</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 使用模板
 * CompareResult result = TFI.comparator()
 *     .useTemplate(ComparisonTemplate.AUDIT)
 *     .compare(obj1, obj2);
 *
 * // 模板 + 链式覆盖（后设覆盖前设）
 * CompareResult result = TFI.comparator()
 *     .useTemplate(ComparisonTemplate.AUDIT)
 *     .withMaxDepth(5)  // 覆盖模板的 maxDepth=10
 *     .compare(obj1, obj2);
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since v3.0.0
 */
public enum ComparisonTemplate {

    /**
     * 审计模式
     * <p>
     * 适用场景：审计追踪、合规检查、变更记录归档
     * </p>
     *
     * <p>配置项：</p>
     * <ul>
     *   <li>深度比较：maxDepth=10</li>
     *   <li>生成报告：Markdown 格式</li>
     *   <li>计算相似度</li>
     *   <li>不包含 null 变更（聚焦有效变更）</li>
     *   <li>不检测移动（简化审计逻辑）</li>
     * </ul>
     */
    AUDIT {
        @Override
        public void apply(CompareOptions.CompareOptionsBuilder builder) {
            builder
                .maxDepth(10)
                .computeSimilarity(true);
        }
    },

    /**
     * 快速模式
     * <p>
     * 适用场景：高频比较、性能敏感场景、实时监控
     * </p>
     *
     * <p>配置项：</p>
     * <ul>
     *   <li>浅比较：仅比较顶层字段</li>
     *   <li>不生成报告（减少开销）</li>
     *   <li>不计算相似度（减少开销）</li>
     *   <li>不包含 null 变更</li>
     *   <li>不检测移动</li>
     * </ul>
     */
    FAST {
        @Override
        public void apply(CompareOptions.CompareOptionsBuilder builder) {
            builder
                .maxDepth(0)
                .computeSimilarity(false);
        }
    },

    /**
     * 调试模式
     * <p>
     * 适用场景：开发调试、问题排查、详细分析
     * </p>
     *
     * <p>配置项：</p>
     * <ul>
     *   <li>深度比较：maxDepth=10，不得越过默认policy</li>
     *   <li>计算相似度</li>
     *   <li>容器与类型语义仍由冻结runtime决定</li>
     * </ul>
     */
    DEBUG {
        @Override
        public void apply(CompareOptions.CompareOptionsBuilder builder) {
            builder
                .maxDepth(10)
                .computeSimilarity(true);
        }
    };

    /**
     * 应用模板配置到 CompareOptions.Builder
     * <p>
     * <b>重要：</b>模板只设置默认值，后续链式方法可覆盖这些值（后设覆盖前设）。
     * </p>
     *
     * @param builder CompareOptions 构建器
     */
    public abstract void apply(CompareOptions.CompareOptionsBuilder builder);
}
