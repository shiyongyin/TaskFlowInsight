package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.spi.ComparisonProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import com.syy.taskflowinsight.spi.RenderProvider;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.taskflowinsight.tracking.projection.ProjectionMetadata;
import com.syy.taskflowinsight.tracking.projection.ProjectionOptions;
import com.syy.taskflowinsight.tracking.render.RenderOptions;

import java.util.Collections;
import java.util.List;

/**
 * 通过Core Registry执行列表比较的无状态静态入口。
 *
 * <p>该兼容门面不保存Provider、Spring上下文或fallback runtime。每次调用都把选择权交给
 * {@link ProviderRegistry}，从而与Registry的冻结世代保持一致，并避免一个应用上下文影响另一个上下文。</p>
 *
 * @author TaskFlow Insight Team
 * @version 4.0.0
 * @since 3.0.0
 */
public class TfiListDiff {

    /** 静态渲染边界只构造一次无状态projection工厂。 */
    private static final CompareProjectionFactory PROJECTION_FACTORY = new CompareProjectionFactory();

    /**
     * 保留3.x可实例化形状，但实例不持有Provider或Spring上下文。
     *
     * <p>全部能力仍由静态方法在调用时解析Core Registry；构造对象不会建立另一条执行路径。</p>
     */
    public TfiListDiff() {
    }

    /**
     * 使用Registry选中的Provider比较两个列表。
     *
     * @param oldList 旧列表，{@code null}按空列表处理
     * @param newList 新列表，{@code null}按空列表处理
     * @return canonical比较结果
     * @throws IllegalStateException Registry没有可用ComparisonProvider时抛出
     */
    public static CompareResult diff(List<?> oldList, List<?> newList) {
        return comparisonProvider().compare(safeList(oldList), safeList(newList));
    }

    /**
     * 使用显式不可变选项比较两个列表。
     *
     * @param oldList 旧列表，{@code null}按空列表处理
     * @param newList 新列表，{@code null}按空列表处理
     * @param options 当前调用选项；{@code null}时使用Provider所属runtime的默认值
     * @return canonical比较结果
     * @throws IllegalStateException Registry没有可用ComparisonProvider时抛出
     */
    public static CompareResult diff(List<?> oldList, List<?> newList, CompareOptions options) {
        List<?> safeOld = safeList(oldList);
        List<?> safeNew = safeList(newList);
        return options == null
                ? comparisonProvider().compare(safeOld, safeNew)
                : comparisonProvider().compare(safeOld, safeNew, options);
    }

    /**
     * 将比较结果投影为安全的Markdown诊断文本。
     *
     * @param result canonical比较结果；{@code null}返回空文本
     * @return Markdown诊断文本，不返回{@code null}
     * @throws IllegalStateException Registry没有可用RenderProvider时抛出
     */
    public static String render(CompareResult result) {
        if (result == null) {
            return "";
        }
        CompareProjection projection = PROJECTION_FACTORY.create(
                result,
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults());
        return renderProvider().render(projection, RenderOptions.markdown());
    }

    /**
     * 使用runtime默认值比较并生成实体分组结果。
     *
     * @param oldList 旧列表，{@code null}按空列表处理
     * @param newList 新列表，{@code null}按空列表处理
     * @return 基于同一次canonical比较结果的实体分组
     */
    public static EntityListDiffResult diffEntities(List<?> oldList, List<?> newList) {
        List<?> safeOld = safeList(oldList);
        List<?> safeNew = safeList(newList);
        return EntityListDiffResult.from(diff(safeOld, safeNew), safeOld, safeNew);
    }

    /**
     * 使用显式选项比较并生成实体分组结果。
     *
     * @param oldList 旧列表，{@code null}按空列表处理
     * @param newList 新列表，{@code null}按空列表处理
     * @param options 当前调用选项；{@code null}时继承runtime默认值
     * @return 基于同一次canonical比较结果的实体分组
     */
    public static EntityListDiffResult diffEntities(
            List<?> oldList,
            List<?> newList,
            CompareOptions options) {
        List<?> safeOld = safeList(oldList);
        List<?> safeNew = safeList(newList);
        return EntityListDiffResult.from(diff(safeOld, safeNew, options), safeOld, safeNew);
    }

    private static ComparisonProvider comparisonProvider() {
        ComparisonProvider provider = ProviderRegistry.resolve(ComparisonProvider.class);
        if (provider == null) {
            throw new IllegalStateException("No ComparisonProvider is available from the Core Registry");
        }
        return provider;
    }

    private static RenderProvider renderProvider() {
        RenderProvider provider = ProviderRegistry.resolve(RenderProvider.class);
        if (provider == null) {
            throw new IllegalStateException("No RenderProvider is available from the Core Registry");
        }
        return provider;
    }

    private static List<?> safeList(List<?> value) {
        return value != null ? value : Collections.emptyList();
    }
}
