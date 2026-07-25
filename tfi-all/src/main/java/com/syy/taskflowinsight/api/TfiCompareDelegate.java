package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.spi.ComparisonProvider;
import com.syy.taskflowinsight.spi.RenderProvider;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.compare.internal.CompareResultReducer;
import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.taskflowinsight.tracking.projection.ProjectionMetadata;
import com.syy.taskflowinsight.tracking.projection.ProjectionOptions;
import com.syy.taskflowinsight.tracking.render.RenderOptions;

import java.util.Objects;

/**
 * {@link TFI}静态门面的比较与渲染委托。
 *
 * <p>该实现把raw {@link CompareResult}限制在门面内部，只向渲染SPI传递一次构造的安全projection，
 * 避免provider形成第二套schema或masking owner。</p>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
final class TfiCompareDelegate {

    /** static facade只在发布边界构造safe projection，不把raw CompareResult交给provider。 */
    private static final CompareProjectionFactory PROJECTION_FACTORY = new CompareProjectionFactory();

    private TfiCompareDelegate() {
        throw new AssertionError("delegate class");
    }

    // ==================== compare ====================

    /**
     * Compare two objects with zero-config defaults.
     *
     * @see TFI#compare(Object, Object)
     */
    static CompareResult compare(Object a, Object b) {
        ComparisonProvider provider = TfiProviderDelegate.getComparisonProvider();
        if (provider == null) {
            return CompareResultReducer.failure(
                    CompareProblemCode.PROVIDER_UNAVAILABLE,
                    CompareStage.PROVIDER);
        }
        return provider.compare(a, b);
    }

    // ==================== comparator ====================

    /**
     * Create a fluent comparator builder.
     *
     * @see TFI#comparator()
     */
    static ComparatorBuilder comparator() {
        ComparisonProvider provider = TfiProviderDelegate.getComparisonProvider();
        return provider == null ? ComparatorBuilder.disabled() : new ComparatorBuilder(null, provider);
    }

    // ==================== render ====================

    /**
     * 构造安全projection后按typed布局渲染。
     *
     * @see TFI#render(CompareResult, RenderOptions)
     */
    static String render(CompareResult result, RenderOptions options) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(options, "options");
        CompareProjection projection = PROJECTION_FACTORY.create(
                result,
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults());
        RenderProvider provider = TfiProviderDelegate.getRenderProvider();
        if (provider == null) {
            throw new IllegalStateException("No RenderProvider is available from the Core Registry");
        }
        return provider.render(projection, options);
    }
}
