package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.render.ChangeReportRenderer;
import com.syy.taskflowinsight.tracking.render.RenderStyle;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 列表比较外观（推荐用法）
 * <p>
 * 这是 TaskFlowInsight 提供的列表比较外观 API，简化了底层 ListCompareExecutor 的使用。
 * 推荐通过 Spring 依赖注入使用此 Bean，以获得更好的可测试性和一致性。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * private TfiListDiffFacade listDiff;
 *
 * // 比较列表
 * List<User> oldList = Arrays.asList(new User(1, "Alice"), new User(2, "Bob"));
 * List<User> newList = Arrays.asList(new User(1, "Alice"), new User(3, "Charlie"));
 * CompareResult result = listDiff.diff(oldList, newList);
 *
 * // 渲染为 Markdown
 * String report = listDiff.render(result);
 * System.out.println(report);
 *
 * // 使用不同样式
 * String detailedReport = listDiff.render(result, RenderStyle.detailed());
 * String simpleReport = listDiff.render(result, "simple");
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since v3.0.0
 */
@Component
public class TfiListDiffFacade {

    private final ListCompareExecutor executor;
    private final ChangeReportRenderer markdownRenderer;

    public TfiListDiffFacade(ListCompareExecutor executor,
                             @Qualifier("markdownRenderer") ChangeReportRenderer markdownRenderer) {
        this.executor = executor;
        this.markdownRenderer = markdownRenderer;
    }

    /**
     * 比较两个列表（自动策略选择）
     * <p>
     * 使用自动策略选择机制，根据列表元素类型智能选择合适的比较策略。
     * 对于带 @Entity 或 @Key 注解的实体列表，会自动使用 ENTITY 策略。
     * </p>
     *
     * @param oldList 旧列表（null 视为空列表）
     * @param newList 新列表（null 视为空列表）
     * @return 比较结果，包含所有检测到的变更
     */
    public CompareResult diff(List<?> oldList, List<?> newList) {
        return diff(oldList, newList, (String) null);
    }

    /**
     * 比较两个列表（指定策略）
     * <p>
     * 使用指定的比较策略进行比较。可用策略：
     * <ul>
     *   <li>SIMPLE - 简单顺序比较，适用于小列表或基本类型</li>
     *   <li>ENTITY - 实体比较，基于 @Key 字段匹配，适用于实体列表</li>
     *   <li>AS_SET - 集合比较，忽略顺序，适用于无序列表</li>
     *   <li>LEVENSHTEIN - 编辑距离算法，检测移动、插入、删除操作</li>
     * </ul>
     * </p>
     *
     * @param oldList 旧列表（null 视为空列表）
     * @param newList 新列表（null 视为空列表）
     * @param strategy 策略名称（null 则自动选择）
     * @return 比较结果，包含所有检测到的变更
     */
    public CompareResult diff(List<?> oldList, List<?> newList, String strategy) {
        CompareOptions.CompareOptionsBuilder builder = CompareOptions.builder();
        if (strategy != null) {
            builder.strategyName(strategy);
        }
        return diff(oldList, newList, builder.build());
    }

    /**
     * 比较两个列表（完整配置）
     * <p>
     * 使用完整的比较选项进行比较，支持所有高级配置。
     * </p>
     *
     * @param oldList 旧列表（null 视为空列表）
     * @param newList 新列表（null 视为空列表）
     * @param options 比较选项（null 则使用默认选项）
     * @return 比较结果，包含所有检测到的变更
     */
    public CompareResult diff(List<?> oldList, List<?> newList, CompareOptions options) {
        List<?> safeOld = oldList != null ? oldList : Collections.emptyList();
        List<?> safeNew = newList != null ? newList : Collections.emptyList();
        CompareOptions safeOptions = options != null ? options : CompareOptions.builder().build();
        return executor.compare(safeOld, safeNew, safeOptions);
    }

    /**
     * 便捷方法：比较并返回实体级分组结果（自动策略）
     */
    public EntityListDiffResult diffEntities(List<?> oldList, List<?> newList) {
        return EntityListDiffResult.from(diff(oldList, newList));
    }

    /**
     * 便捷方法：比较并返回实体级分组结果（指定策略）
     */
    public EntityListDiffResult diffEntities(List<?> oldList, List<?> newList, String strategy) {
        CompareOptions.CompareOptionsBuilder builder = CompareOptions.builder();
        if (strategy != null) {
            builder.strategyName(strategy);
        }
        return diffEntities(oldList, newList, builder.build());
    }

    /**
     * 便捷方法：比较并返回实体级分组结果（完整选项）
     */
    public EntityListDiffResult diffEntities(List<?> oldList, List<?> newList, CompareOptions options) {
        CompareResult result = diff(oldList, newList, options);
        return EntityListDiffResult.from(result, oldList, newList);
    }

    /**
     * 渲染比较结果为 Markdown 报告（使用标准样式）
     * <p>
     * 将 CompareResult 转换为可读的 Markdown 格式报告。
     * 使用标准样式（显示统计、GitHub 表格格式）。
     * </p>
     *
     * @param result 比较结果
     * @return Markdown 格式的报告字符串
     */
    public String render(CompareResult result) {
        return render(result, RenderStyle.standard());
    }

    /**
     * 渲染比较结果为 Markdown 报告（指定样式）
     * <p>
     * 支持多种样式参数：
     * <ul>
     *   <li>RenderStyle 对象：直接使用指定样式</li>
     *   <li>字符串 "simple"：使用简洁样式</li>
     *   <li>字符串 "detailed"：使用详细样式</li>
     *   <li>null：使用标准样式</li>
     * </ul>
     * </p>
     *
     * @param result 比较结果（null 返回空字符串）
     * @param style  样式配置（支持 RenderStyle 对象或字符串）
     * @return Markdown 格式的报告字符串
     */
    public String render(CompareResult result, Object style) {
        if (result == null) {
            return "";
        }

        // 转换为实体列表差异结果
        EntityListDiffResult entityResult = EntityListDiffResult.from(result);

        // 解析样式参数
        RenderStyle renderStyle = parseStyle(style);

        // 委托给渲染器
        return markdownRenderer.render(entityResult, renderStyle);
    }

    /**
     * 解析样式参数
     */
    private RenderStyle parseStyle(Object style) {
        if (style instanceof RenderStyle rs) {
            return rs;
        }

        if (style instanceof String styleStr) {
            return switch (styleStr.toLowerCase()) {
                case "simple" -> RenderStyle.simple();
                case "detailed" -> RenderStyle.detailed();
                default -> RenderStyle.standard();
            };
        }

        return RenderStyle.standard();
    }
}
