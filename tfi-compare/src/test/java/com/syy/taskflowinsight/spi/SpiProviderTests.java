package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.taskflowinsight.tracking.projection.ProjectionMetadata;
import com.syy.taskflowinsight.tracking.projection.ProjectionOptions;
import com.syy.taskflowinsight.tracking.render.RenderOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * SPI 提供者系统测试。
 * 验证 ComparisonProvider、TrackingProvider、RenderProvider 及其默认实现。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("SPI — 服务提供者测试")
class SpiProviderTests {

    // ── DefaultComparisonProvider ──

    @Nested
    @DisplayName("DefaultComparisonProvider")
    class DefaultComparisonProviderTests {

        @Test
        @DisplayName("compare 两个对象 → 返回有效结果")
        void compare_shouldReturnValidResult() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            CompareResult result = provider.compare("hello", "world");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("compare null 安全")
        void compare_withNulls_shouldNotThrow() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            assertThatCode(() -> provider.compare(null, "test"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("compare 相同对象 → identical")
        void compare_sameObject_shouldBeIdentical() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            String obj = "same";
            CompareResult result = provider.compare(obj, obj);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("priority → 0 (默认)")
        void priority_shouldBeZero() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            assertThat(provider.priority()).isEqualTo(0);
        }

    }

    // ── DefaultTrackingProvider ──

    @Nested
    @DisplayName("DefaultTrackingProvider")
    class DefaultTrackingProviderTests {

        @Test
        @DisplayName("typed batch通过final executor完成生命周期")
        void lifecycle_shouldWork() {
            DefaultTrackingProvider provider = new DefaultTrackingProvider();
            int[] target = {1};
            CompareResult result = new TrackingExecutor(provider).withTracked(
                    "obj",
                    target,
                    () -> target[0] = 2,
                    CompareOptions.builder().build());
            assertThat(result.isDifferent()).isTrue();
        }

        @Test
        @DisplayName("priority → 0")
        void priority_shouldBeZero() {
            DefaultTrackingProvider provider = new DefaultTrackingProvider();
            assertThat(provider.priority()).isEqualTo(0);
        }
    }

    // ── DefaultRenderProvider ──

    @Nested
    @DisplayName("DefaultRenderProvider")
    class DefaultRenderProviderTests {

        @Test
        @DisplayName("render 空projection → 不抛异常")
        void renderEmpty_shouldNotThrow() {
            DefaultRenderProvider provider = new DefaultRenderProvider();
            assertThatCode(() -> provider.render(projection(CompareResult.identical()), RenderOptions.markdown()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("priority → 0")
        void priority_shouldBeZero() {
            DefaultRenderProvider provider = new DefaultRenderProvider();
            assertThat(provider.priority()).isEqualTo(0);
        }

        @Test
        @DisplayName("render 带变更 → 返回 markdown 文本")
        void renderWithChanges_shouldReturnMarkdown() {
            DefaultRenderProvider provider = new DefaultRenderProvider();
            CompareResult result = CompareResult.ofNullDiff(null, "newObject");
            String rendered = provider.render(projection(result), RenderOptions.markdown());
            assertThat(rendered).contains("# Compare Projection");
        }

        private CompareProjection projection(CompareResult result) {
            return new CompareProjectionFactory().create(
                    result,
                    ProjectionMetadata.empty(),
                    MaskingPolicy.safeDefaults(),
                    ProjectionOptions.defaults());
        }
    }
}
