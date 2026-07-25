package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.spi.ComparisonProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import com.syy.taskflowinsight.spi.RenderProvider;
import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.TrackingBatchScope;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.internal.CompareResultReducer;
import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.render.RenderOptions;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TFI对Core Registry的无状态适配合同。
 *
 * <p>验证点：
 * - compare/render/withTracked只消费Registry选中的typed Provider
 * - facade不缓存选择，也不吞掉Registry freeze异常
 */
class TFIRoutingTests {

    private OptionsAwareComparisonProvider comparisonProvider;
    private MockTrackingProvider trackingProvider;
    private MockRenderProvider renderProvider;

    @BeforeEach
    void setUp() {
        TFI.clear();
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.setProperty("tfi.api.routing.enabled", "true");
        comparisonProvider = new OptionsAwareComparisonProvider(300);
        trackingProvider = new MockTrackingProvider(300);
        renderProvider = new MockRenderProvider(300);
        TFI.registerComparisonProvider(comparisonProvider);
        TFI.registerTrackingProvider(trackingProvider);
        TFI.registerRenderProvider(renderProvider);
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
        TFI.disable();
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.clearProperty("tfi.api.routing.enabled");
    }

    @Test
    @DisplayName("compare() 应路由到 ComparisonProvider 并返回 provider 结果")
    void compare_should_route_to_provider() {
        var r = TFI.compare("foo", "bar");

        assertEquals(1, comparisonProvider.callCount.get(),
                "ComparisonProvider should be invoked exactly once");
        assertNotNull(r);
        assertTrue(r.isIdentical(), "complete empty result should stay identical");
    }

    @Test
    @DisplayName("comparator() 应返回 Provider-aware builder 并透传 CompareOptions")
    void comparator_should_be_provider_aware() throws Exception {
        var builder = TFI.comparator().withSimilarity();
        var r = builder.compare("x", "y");

        assertTrue(r.isIdentical());
        assertEquals(1, comparisonProvider.callCount.get(), "Provider.compare(options) should be called");
        assertTrue(comparisonProvider.optionsSeen.get(), "CompareOptions should be forwarded to provider");
    }

    @Test
    @DisplayName("render() 应路由到 RenderProvider 并返回 provider 文本")
    void render_should_route_to_provider() {
        var result = CompareResult.identical();
        String md = TFI.render(result, RenderOptions.markdown());

        assertEquals("MOCK_RENDER", md);
    }

    @Test
    @DisplayName("withTracked() 应经 Registry 路由到 typed TrackingProvider")
    void track_should_route_to_provider() {
        AtomicInteger actionCalls = new AtomicInteger();
        TFI.withTracked("obj", new Object(), actionCalls::incrementAndGet);

        assertEquals(1, trackingProvider.beginCalls.get(), "TrackingProvider.begin should be invoked");
        assertEquals(1, actionCalls.get(), "action should be invoked exactly once");
    }

    @Test
    @DisplayName("扩展 Provider 选择应随 Registry epoch 切换")
    void extension_provider_selection_should_follow_registry_epoch() {
        assertSame(comparisonProvider, TfiProviderDelegate.getComparisonProvider());
        assertSame(trackingProvider, TfiProviderDelegate.getTrackingProvider());
        assertSame(renderProvider, TfiProviderDelegate.getRenderProvider());

        ProviderRegistry.clearAll();
        MockComparisonProvider secondComparison = new MockComparisonProvider(400);
        MockTrackingProvider secondTracking = new MockTrackingProvider(400);
        MockRenderProvider secondRender = new MockRenderProvider(400);
        TFI.registerComparisonProvider(secondComparison);
        TFI.registerTrackingProvider(secondTracking);
        TFI.registerRenderProvider(secondRender);

        assertSame(secondComparison, TfiProviderDelegate.getComparisonProvider());
        assertSame(secondTracking, TfiProviderDelegate.getTrackingProvider());
        assertSame(secondRender, TfiProviderDelegate.getRenderProvider());
    }

    @Test
    @DisplayName("首次解析后公开注册原样抛freeze异常且不能替换当前Provider")
    void latePublicRegistrationPropagatesFreezeFailure() {
        assertTrue(TFI.compare("first", "resolution").isIdentical());
        MockComparisonProvider lateProvider = new MockComparisonProvider(400);

        assertThrows(IllegalStateException.class,
                () -> TFI.registerComparisonProvider(lateProvider));
        assertTrue(TFI.compare("second", "resolution").isIdentical());

        assertSame(comparisonProvider, TfiProviderDelegate.getComparisonProvider());
        assertEquals(2, comparisonProvider.callCount.get());
        assertEquals(0, lateProvider.callCount.get());
    }

    // ========== Mocks ==========

    static class MockComparisonProvider implements ComparisonProvider {
        /** provider选择优先级。 */
        final int prio;
        /** compare调用次数。 */
        final AtomicInteger callCount = new AtomicInteger();
        MockComparisonProvider(int prio) { this.prio = prio; }
        @Override
        public CompareResult compare(Object before, Object after) {
            callCount.incrementAndGet();
            return CompareResultReducer.complete(Collections.emptyList());
        }
        @Override
        public CompareResult compare(Object before, Object after, CompareOptions options) {
            return compare(before, after);
        }
        @Override public int priority() { return prio; }
        @Override public String toString() { return "MockComparisonProvider{" + prio + "}"; }
    }

    static class OptionsAwareComparisonProvider extends MockComparisonProvider {
        /** typed options是否到达provider边界。 */
        final AtomicBoolean optionsSeen = new AtomicBoolean(false);
        OptionsAwareComparisonProvider(int prio) { super(prio); }
        @Override
        public CompareResult compare(
                Object before,
                Object after,
                CompareOptions options) {
            callCount.incrementAndGet();
            if (options != null) {
                optionsSeen.set(true);
            }
            return CompareResultReducer.complete(Collections.emptyList());
        }
    }

    static class MockRenderProvider implements RenderProvider {
        final int prio;
        MockRenderProvider(int prio) { this.prio = prio; }
        @Override
        public String render(CompareProjection projection, RenderOptions options) {
            return "MOCK_RENDER";
        }
        @Override public int priority() { return prio; }
    }

    static class MockTrackingProvider implements TrackingProvider {
        final int prio;
        final AtomicInteger beginCalls = new AtomicInteger();
        MockTrackingProvider(int prio) { this.prio = prio; }
        @Override
        public TrackingBatchScope begin(
                List<TrackingExecutor.Target> targets,
                CompareOptions options) {
            beginCalls.incrementAndGet();
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
        }
        @Override public int priority() { return prio; }
    }

}
