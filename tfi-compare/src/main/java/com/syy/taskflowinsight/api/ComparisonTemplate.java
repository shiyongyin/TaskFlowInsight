package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.ReportFormat;

/**
 * 比较模板枚举
 * <p>
 * 为常见场景提供开箱即用的比较配置模板，降低配置心智负担。
 * 模板仅组合 {@link CompareOptions} 的常用选项；链式方法仍可覆盖模板默认值（后设覆盖前设）。
 * </p>
 *
 * <h3>模板说明</h3>
 * <ul>
 *   <li><b>AUDIT</b>（审计模式）：深度比较 + 报告 + 相似度，适用于审计、合规场景</li>
 *   <li><b>FAST</b>（快速模式）：浅比较 + 无报告，适用于性能敏感场景</li>
 *   <li><b>DEBUG</b>（调试模式）：深度比较 + 完整信息 + 类型感知，适用于开发调试</li>
 * </ul>
 *
 * <h3>使用示例</h3>
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
                .enableDeepCompare(true)
                .maxDepth(10)
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .calculateSimilarity(true)
                .includeNullChanges(false)
                .detectMoves(false);
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
                .enableDeepCompare(false)
                .generateReport(false)
                .calculateSimilarity(false)
                .includeNullChanges(false)
                .detectMoves(false);
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
     *   <li>深度比较：maxDepth=20（更深遍历）</li>
     *   <li>生成报告：Markdown 格式</li>
     *   <li>计算相似度</li>
     *   <li>类型感知：自动识别 @Entity/@ValueObject</li>
     *   <li>包含 null 变更（完整信息）</li>
     *   <li>检测移动（完整变更类型）</li>
     * </ul>
     */
    DEBUG {
        @Override
        public void apply(CompareOptions.CompareOptionsBuilder builder) {
            builder
                .enableDeepCompare(true)
                .maxDepth(20)
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .calculateSimilarity(true)
                .typeAwareEnabled(true)
                .includeNullChanges(true)
                .detectMoves(true);
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
