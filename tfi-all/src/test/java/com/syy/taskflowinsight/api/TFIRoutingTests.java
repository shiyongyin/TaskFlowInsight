package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.spi.ComparisonProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import com.syy.taskflowinsight.spi.RenderProvider;
import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TFI 路由改造验证（02 卡）
 *
 * <p>验证点：
 * - FeatureFlag 打开后，TFI.compare/render/track 走 ProviderRegistry
 * - 关闭时保持 legacy 行为
 * - Provider 为 null 时不崩溃（降级）
 */
class TFIRoutingTests {

    @BeforeEach
    void setUp() throws Exception {
        // 启用路由
        System.setProperty("tfi.api.routing.enabled", "true");
        // 清空注册表与缓存
        ProviderRegistry.clearAll();
        resetTFICache();
    }

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("tfi.api.routing.enabled");
        ProviderRegistry.clearAll();
        resetTFICache();
    }

    @Test
    @DisplayName("compare() 应路由到 ComparisonProvider 并返回 provider 结果")
    void compare_should_route_to_provider() {
        // 准备：注册一个高优先级的 MockComparisonProvider
        MockComparisonProvider mock = new MockComparisonProvider(300);
        ProviderRegistry.register(ComparisonProvider.class, mock);

        // 调用：字符串不同，provider 会被调用一次
        var r = TFI.compare("foo", "bar");

        // 断言：调用次数与返回结果
        assertEquals(1, mock.callCount.get(), "ComparisonProvider should be invoked exactly once");
        assertNotNull(r);
        assertFalse(r.isIdentical(), "mock result should not be identical");
    }

    @Test
    @DisplayName("comparator() 应返回 Provider-aware builder 并透传 CompareOptions")
    void comparator_should_be_provider_aware() throws Exception {
        // 注册可感知 options 的 provider
        OptionsAwareComparisonProvider mock = new OptionsAwareComparisonProvider(300);
        ProviderRegistry.register(ComparisonProvider.class, mock);

        var builder = TFI.comparator().withSimilarity().withReport();
        var r = builder.compare("x", "y");

        assertFalse(r.isIdentical());
        assertEquals(1, mock.callCount.get(), "Provider.compare(options) should be called");
        assertTrue(mock.optionsSeen.get(), "CompareOptions should be forwarded to provider");
    }

    @Test
    @DisplayName("render() 应路由到 RenderProvider 并返回 provider 文本")
    void render_should_route_to_provider() {
        // 准备：注册一个高优先级的 MockRenderProvider
        ProviderRegistry.register(RenderProvider.class, new MockRenderProvider(300));

        // 调用：使用 compare 先得到一个结果
        var result = com.syy.taskflowinsight.tracking.compare.CompareResult.ofTypeDiff("a", "b");
        String md = TFI.render(result, "standard");

        // 断言：应返回 Mock 字符串
        assertEquals("MOCK_RENDER", md);
    }

    @Test
    @DisplayName("track() 应路由到 TrackingProvider，不抛异常")
    void track_should_route_to_provider() {
        MockTrackingProvider mock = new MockTrackingProvider(300);
        ProviderRegistry.register(TrackingProvider.class, mock);

        // 需要先启用 Facade（isEnabled() 才会继续执行）
        TFI.enable();
        TFI.track("obj", new Object());

        assertEquals(1, mock.trackCalls.get(), "TrackingProvider.track should be invoked");
        // 变更获取
        assertDoesNotThrow(() -> mock.changes());
    }

    // ========== Mocks ==========

    static class MockComparisonProvider implements ComparisonProvider {
        final int prio;
        final AtomicInteger callCount = new AtomicInteger();
        MockComparisonProvider(int prio) { this.prio = prio; }
        @Override
        public com.syy.taskflowinsight.tracking.compare.CompareResult compare(Object before, Object after) {
            callCount.incrementAndGet();
            return com.syy.taskflowinsight.tracking.compare.CompareResult.builder()
                .object1(before).object2(after)
                .identical(false)
                .changes(Collections.emptyList())
                .build();
        }
        @Override public int priority() { return prio; }
        @Override public String toString() { return "MockComparisonProvider{" + prio + "}"; }
    }

    static class OptionsAwareComparisonProvider extends MockComparisonProvider {
        final AtomicInteger callCount = new AtomicInteger();
        final java.util.concurrent.atomic.AtomicBoolean optionsSeen = new java.util.concurrent.atomic.AtomicBoolean(false);
        OptionsAwareComparisonProvider(int prio) { super(prio); }
        @Override
        public com.syy.taskflowinsight.tracking.compare.CompareResult compare(Object before, Object after, com.syy.taskflowinsight.tracking.compare.CompareOptions options) {
            callCount.incrementAndGet();
            if (options != null) {
                optionsSeen.set(true);
            }
            return com.syy.taskflowinsight.tracking.compare.CompareResult.builder()
                .object1(before).object2(after)
                .identical(false)
                .changes(Collections.emptyList())
                .build();
        }
    }

    static class MockRenderProvider implements RenderProvider {
        final int prio;
        MockRenderProvider(int prio) { this.prio = prio; }
        @Override public String render(Object result, Object style) { return "MOCK_RENDER"; }
        @Override public int priority() { return prio; }
    }

    static class MockTrackingProvider implements TrackingProvider {
        final int prio;
        final AtomicInteger trackCalls = new AtomicInteger();
        MockTrackingProvider(int prio) { this.prio = prio; }
        @Override public void track(String name, Object target, String... fields) { trackCalls.incrementAndGet(); }
        @Override public List<ChangeRecord> changes() { return Collections.emptyList(); }
        @Override public void clear() { }
        @Override public int priority() { return prio; }
    }

    // ========== Helpers ==========
    private static void resetTFICache() throws Exception {
        Class<?> tfi = TFI.class;
        for (String f : List.of(
            "cachedComparisonProvider", "cachedTrackingProvider",
            "cachedFlowProvider", "cachedRenderProvider",
            "compareService", "markdownRenderer")) {
            try {
                Field field = tfi.getDeclaredField(f);
                field.setAccessible(true);
                field.set(null, null);
            } catch (NoSuchFieldException ignore) {
                // 忽略不存在的字段（向前兼容）
            }
        }
    }
}
