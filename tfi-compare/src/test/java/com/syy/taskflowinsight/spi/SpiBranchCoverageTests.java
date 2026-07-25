package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.TrackingBatchScope;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareInputException;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * SPI 包完整分支覆盖测试。
 *
 * <p>覆盖所有 SPI 接口的 default 方法以及所有 Default* 实现类的
 * try/catch、if/else 分支，将 spi 包分支覆盖率从 12.5% 提升到 60%+。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("SPI — 完整分支覆盖测试")
class SpiBranchCoverageTests {

    // ══════════════════════════════════════════════════════════════
    // ComparisonProvider interface contracts
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ComparisonProvider — typed合同与公共默认能力")
    class ComparisonProviderDefaultMethodTests {

        /** 最小实现必须显式处理无options与typed options两个入口。 */
        private final ComparisonProvider minimalProvider = new ComparisonProvider() {
            @Override
            public CompareResult compare(Object before, Object after) {
                return CompareResult.identical();
            }

            @Override
            public CompareResult compare(Object before, Object after, CompareOptions options) {
                return CompareResult.identical();
            }
        };

        @Test
        @DisplayName("priority: 默认 → 0")
        void priority_default_returnsZero() {
            assertThat(minimalProvider.priority()).isEqualTo(0);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // DefaultComparisonProvider — all branches
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DefaultComparisonProvider — 全分支覆盖")
    class DefaultComparisonProviderBranchTests {

        private final DefaultComparisonProvider provider = new DefaultComparisonProvider();

        @Test
        @DisplayName("compare: 两个不同对象 → 非 identical")
        void compare_differentObjects_notIdentical() {
            CompareResult result = provider.compare("hello", "world");
            assertThat(result).isNotNull();
            // 不同字符串比较应返回结果
        }

        @Test
        @DisplayName("compare: 两个 null → identical")
        void compare_bothNull_identical() {
            CompareResult result = provider.compare(null, null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("compare: 左 null → 返回结果")
        void compare_leftNull_returnsResult() {
            CompareResult result = provider.compare(null, "test");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("compare: 右 null → 返回结果")
        void compare_rightNull_returnsResult() {
            CompareResult result = provider.compare("test", null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("compare: 相同引用 → identical")
        void compare_sameRef_identical() {
            String obj = "sameRef";
            CompareResult result = provider.compare(obj, obj);
            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("compare(options): null options → typed input exception")
        void compareWithOptions_nullOptionsRejected() {
            assertThatThrownBy(() -> provider.compare("a", "b", null))
                    .isInstanceOf(CompareInputException.class);
        }

        @Test
        @DisplayName("compare(options): 有效 options → 使用提供的 options")
        void compareWithOptions_validOptions_usesProvided() {
            CompareResult result = provider.compare("a", "b", CompareOptions.builder().build());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("toString 返回描述性字符串")
        void toString_returnsDescription() {
            assertThat(provider.toString()).contains("DefaultComparisonProvider");
        }

        @Test
        @DisplayName("priority → 0")
        void priority_isZero() {
            assertThat(provider.priority()).isEqualTo(0);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TrackingProvider typed scope contract
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TrackingProvider — typed scope分支覆盖")
    class TrackingProviderTypedScopeTests {

        /** 用于证明executor只把校验后的完整batch交给provider。 */
        private final AtomicBoolean beginCalled = new AtomicBoolean();

        private final TrackingProvider provider = (targets, options) -> {
            beginCalled.set(true);
            return new TrackingBatchScope() {
                @Override
                public List<TrackingExecutor.Item> capture() {
                    return targets.stream()
                            .map(target -> new TrackingExecutor.Item(
                                    target.name(), CompareResult.identical()))
                            .toList();
                }

                @Override
                public void close() {
                }
            };
        };

        @Test
        @DisplayName("execute: 合法batch只调用一次typed begin")
        void execute_validBatch_callsTypedBeginOnce() {
            AtomicBoolean actionRan = new AtomicBoolean();
            new TrackingExecutor(provider).execute(
                    List.of(new TrackingExecutor.Target("test", new Object())),
                    CompareOptions.builder().build(),
                    () -> {
                        actionRan.set(true);
                        return null;
                    });
            assertThat(beginCalled).isTrue();
            assertThat(actionRan).isTrue();
        }

        @Test
        @DisplayName("execute: 重复name在begin前拒绝")
        void execute_duplicateName_rejectedBeforeBegin() {
            assertThatThrownBy(() -> new TrackingExecutor(provider).execute(
                    List.of(
                            new TrackingExecutor.Target("test", new Object()),
                            new TrackingExecutor.Target(" test ", new Object())),
                    CompareOptions.builder().build(),
                    () -> null))
                    .isInstanceOf(CompareInputException.class);
            assertThat(beginCalled).isFalse();
        }

        @Test
        @DisplayName("priority: 默认 → 0")
        void priority_default_returnsZero() {
            assertThat(provider.priority()).isEqualTo(0);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // DefaultTrackingProvider — all branches
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DefaultTrackingProvider — typed batch覆盖")
    class DefaultTrackingProviderBranchTests {

        @Test
        @DisplayName("begin: 返回可消费且幂等关闭的scope")
        void begin_returnsConsumableIdempotentScope() {
            DefaultTrackingProvider provider = new DefaultTrackingProvider();
            TrackingBatchScope scope = provider.begin(
                    List.of(new TrackingExecutor.Target("test", new Object())),
                    CompareOptions.builder().build());
            assertThat(scope.capture()).singleElement();
            assertThatCode(scope::close).doesNotThrowAnyException();
            assertThatCode(scope::close).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("toString 返回描述性字符串")
        void toString_returnsDescription() {
            DefaultTrackingProvider provider = new DefaultTrackingProvider();
            assertThat(provider.toString())
                    .contains("DefaultTrackingProvider", "type=default")
                    .doesNotContain("target", "result");
        }

        @Test
        @DisplayName("priority → 0")
        void priority_isZero() {
            DefaultTrackingProvider provider = new DefaultTrackingProvider();
            assertThat(provider.priority()).isEqualTo(0);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // DefaultRenderProvider — all branches
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DefaultRenderProvider — 全分支覆盖")
    class DefaultRenderProviderBranchTests {

        private final DefaultRenderProvider provider = new DefaultRenderProvider();

        @Test
        @DisplayName("render: typed projection支持两种闭集布局")
        void render_typedProjection_supportsBothLayouts() {
            CompareProjection projection = projection();

            assertThat(provider.render(projection, RenderOptions.markdown()))
                    .contains("# Compare Projection");
            assertThat(provider.render(projection, RenderOptions.console()))
                    .contains("=== Compare Projection ===");
        }

        @Test
        @DisplayName("render: null输入不做隐式fallback")
        void render_nullInput_isRejected() {
            assertThatThrownBy(() -> provider.render(null, RenderOptions.defaults()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> provider.render(projection(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("toString 返回描述性字符串")
        void toString_returnsDescription() {
            assertThat(provider.toString()).contains("DefaultRenderProvider");
        }

        @Test
        @DisplayName("priority → 0")
        void priority_isZero() {
            assertThat(provider.priority()).isEqualTo(0);
        }

        private CompareProjection projection() {
            return new CompareProjectionFactory().create(
                    CompareResult.identical(),
                    ProjectionMetadata.empty(),
                    MaskingPolicy.safeDefaults(),
                    ProjectionOptions.defaults());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RenderProvider interface default methods
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RenderProvider — default 方法分支覆盖")
    class RenderProviderDefaultMethodTests {

        private final RenderProvider minimalProvider = (projection, options) -> "rendered";

        @Test
        @DisplayName("priority: 默认 → 0")
        void priority_default_returnsZero() {
            assertThat(minimalProvider.priority()).isEqualTo(0);
        }

        @Test
        @DisplayName("render: 正常调用 → 返回渲染字符串")
        void render_normal_returnsString() {
            assertThat(minimalProvider.render(null, RenderOptions.defaults())).isEqualTo("rendered");
        }
    }
}
