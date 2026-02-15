package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
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

        @Test
        @DisplayName("similarity 返回数值")
        void similarity_shouldReturnValue() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            double sim = provider.similarity("hello", "hello");
            assertThat(sim).isBetween(0.0, 1.0);
        }
    }

    // ── DefaultTrackingProvider ──

    @Nested
    @DisplayName("DefaultTrackingProvider")
    class DefaultTrackingProviderTests {

        @Test
        @DisplayName("track + changes + clear 基本生命周期")
        void lifecycle_shouldWork() {
            DefaultTrackingProvider provider = new DefaultTrackingProvider();
            assertThatCode(() -> {
                provider.track("obj", new Object(), "field1");
                provider.changes();
                provider.clear();
            }).doesNotThrowAnyException();
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
        @DisplayName("render 空结果 → 不抛异常")
        void renderEmpty_shouldNotThrow() {
            DefaultRenderProvider provider = new DefaultRenderProvider();
            CompareResult empty = CompareResult.identical();
            assertThatCode(() -> provider.render(empty, "standard"))
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
            String rendered = provider.render(result, "standard");
            assertThat(rendered).isNotNull();
        }
    }
}
