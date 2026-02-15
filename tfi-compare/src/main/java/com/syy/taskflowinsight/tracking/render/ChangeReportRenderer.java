package com.syy.taskflowinsight.tracking.render;

import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;

/**
 * 变更报告渲染器接口
 * <p>
 * 支持将实体列表比较结果转换为各种格式的报告。
 * 这是一个可插拔的渲染器体系，遵循策略模式，允许扩展多种输出格式。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * @Qualifier("markdownRenderer")
 * private ChangeReportRenderer renderer;
 *
 * EntityListDiffResult result = EntityListDiffResult.from(compareResult);
 * String report = renderer.render(result);
 * System.out.println(report);
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since v3.0.0
 */
public interface ChangeReportRenderer {

    /**
     * 渲染实体列表比较结果
     * <p>
     * 将 EntityListDiffResult 转换为格式化的报告字符串。
     * 具体格式由实现类决定（如 Markdown、JSON、HTML 等）。
     * </p>
     *
     * @param result 实体列表比较结果
     * @param style  渲染样式配置
     * @return 格式化的报告字符串
     */
    String render(EntityListDiffResult result, RenderStyle style);

    /**
     * 使用默认样式渲染
     *
     * @param result 实体列表比较结果
     * @return 格式化的报告字符串
     */
    default String render(EntityListDiffResult result) {
        return render(result, getDefaultStyle());
    }

    /**
     * 检查是否支持指定的结果类型
     * <p>
     * 用于在多渲染器场景下选择合适的渲染器。
     * </p>
     *
     * @param resultType 结果类型
     * @return true 如果支持该类型
     */
    boolean supports(Class<?> resultType);

    /**
     * 获取默认渲染样式
     *
     * @return 默认样式配置
     */
    RenderStyle getDefaultStyle();

    /**
     * 获取渲染器名称
     * <p>
     * 返回该渲染器的唯一标识名称，如 "markdown"、"json"、"html" 等。
     * </p>
     *
     * @return 渲染器名称
     */
    String getName();
}