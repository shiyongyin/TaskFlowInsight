package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.taskflowinsight.tracking.projection.ProjectionMetadata;
import com.syy.taskflowinsight.tracking.projection.ProjectionOptions;
import com.syy.taskflowinsight.tracking.render.ChangeReportRenderer;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 绑定一个不可变运行时的列表比较外观。
 *
 * <p>该类型位于纯Java API边界，不负责寻找Spring容器或构造默认引擎。宿主必须显式传入
 * {@link CompareOperations}，因此Spring上下文只会使用自身的runtime，standalone调用也不会生成fallback图。</p>
 *
 * <h2>使用示例</h2>
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
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @version 4.0.0
 * @since 3.0.0
 */
public class TfiListDiffFacade {

    /** facade只在渲染边界构造一次safe projection，不把raw result交给formatter。 */
    private static final CompareProjectionFactory PROJECTION_FACTORY = new CompareProjectionFactory();

    /** 当前宿主已经选择的唯一比较执行入口。 */
    private final CompareOperations operations;
    /** 当前宿主的完整脱敏策略，渲染时不得由调用入口覆盖。 */
    private final MaskingPolicy maskingPolicy;
    /** 只消费canonical projection的Markdown渲染器。 */
    private final ChangeReportRenderer markdownRenderer;

    /**
     * 创建绑定到既有runtime的列表门面。
     *
     * @param operations 当前宿主唯一的比较执行入口
     * @param maskingPolicy 当前宿主已经验证的完整脱敏策略
     * @param markdownRenderer 只接受canonical projection的Markdown渲染器
     */
    public TfiListDiffFacade(
            CompareOperations operations,
            MaskingPolicy maskingPolicy,
            ChangeReportRenderer markdownRenderer) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.maskingPolicy = Objects.requireNonNull(maskingPolicy, "maskingPolicy");
        this.markdownRenderer = Objects.requireNonNull(markdownRenderer, "markdownRenderer");
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
        List<?> safeOld = oldList != null ? oldList : Collections.emptyList();
        List<?> safeNew = newList != null ? newList : Collections.emptyList();
        return operations.compare(safeOld, safeNew);
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
        return options == null
                ? operations.compare(safeOld, safeNew)
                : operations.compare(safeOld, safeNew, options);
    }

    /**
     * 便捷方法：比较并返回实体级分组结果（自动策略）
     */
    public EntityListDiffResult diffEntities(List<?> oldList, List<?> newList) {
        return EntityListDiffResult.from(diff(oldList, newList));
    }

    /**
     * 便捷方法：比较并返回实体级分组结果（完整选项）
     */
    public EntityListDiffResult diffEntities(List<?> oldList, List<?> newList, CompareOptions options) {
        CompareResult result = diff(oldList, newList, options);
        return EntityListDiffResult.from(result, oldList, newList);
    }

    /**
     * 渲染比较结果为Markdown诊断报告。
     * <p>
     * 先在facade边界构造safe-default projection，再交给只读renderer，防止raw结果成为输出入口。
     * </p>
     *
     * @param result 比较结果
     * @return Markdown 格式的报告字符串
     */
    public String render(CompareResult result) {
        if (result == null) {
            return "";
        }
        CompareProjection projection = PROJECTION_FACTORY.create(
                result,
                ProjectionMetadata.empty(),
                maskingPolicy,
                ProjectionOptions.defaults());
        return markdownRenderer.render(projection);
    }
}
