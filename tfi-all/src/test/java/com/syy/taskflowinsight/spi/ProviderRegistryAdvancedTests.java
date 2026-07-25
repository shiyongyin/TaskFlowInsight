package com.syy.taskflowinsight.spi;

import org.junit.jupiter.api.*;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProviderRegistry 高级能力与边界场景测试。
 *
 * <p>覆盖点：
 * <ul>
 *   <li>unregister() 动态注销</li>
 *   <li>白名单过滤（手动注册与ServiceLoader）</li>
 *   <li>损坏的 META-INF/services 配置（ServiceConfigurationError）</li>
 * </ul>
 */
class ProviderRegistryAdvancedTests {

    @BeforeEach
    void before() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null); // 关闭白名单
        System.clearProperty("tfi.spi.allowedProviders");
    }

    @AfterEach
    void after() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.clearProperty("tfi.spi.allowedProviders");
        Thread.currentThread().setContextClassLoader(ProviderRegistryAdvancedTests.class.getClassLoader());
    }

    @Test
    @DisplayName("unregister 应可移除指定 Provider 并保持其余不变")
    void unregister_should_remove_specific_provider() {
        ComparisonProvider a = new ProviderRegistryChaosTests.MockComparisonProvider(5, "A");
        ComparisonProvider b = new ProviderRegistryChaosTests.MockComparisonProvider(10, "B");

        ProviderRegistry.register(ComparisonProvider.class, a);
        ProviderRegistry.register(ComparisonProvider.class, b);

        // mutation 必须在首次 selection 前完成。
        assertTrue(ProviderRegistry.unregister(ComparisonProvider.class, b));
        assertEquals(5, ProviderRegistry.lookup(ComparisonProvider.class).priority());

        // 新 epoch 中验证移除最后一个注册项；clearAll 前没有活动 Session/Task scope。
        ProviderRegistry.clearAll();
        ProviderRegistry.register(ComparisonProvider.class, a);
        assertTrue(ProviderRegistry.unregister(ComparisonProvider.class, a));
        assertDoesNotThrow(() -> ProviderRegistry.lookup(ComparisonProvider.class));
    }

    @Test
    @DisplayName("白名单应拦截手动注册的非允许 Provider")
    void whitelist_should_block_manual_registration() {
        // 仅允许默认实现
        ProviderRegistry.setAllowedProviders(Set.of(DefaultComparisonProvider.class.getName()));

        ComparisonProvider mock = new ProviderRegistryChaosTests.MockComparisonProvider(200, "Blocked");
        ProviderRegistry.register(ComparisonProvider.class, mock);

        // 手动注册被过滤，期望从ServiceLoader拿到默认实现或null（环境差异）
        ComparisonProvider selected = ProviderRegistry.lookup(ComparisonProvider.class);
        if (selected != null) {
            assertTrue(selected instanceof DefaultComparisonProvider,
                "When whitelist is enabled, only DefaultComparisonProvider is allowed");
        }
    }

    @Test
    @DisplayName("白名单应对 ServiceLoader 结果生效（仅允许默认实现）")
    void whitelist_should_filter_serviceloader_results() {
        ProviderRegistry.setAllowedProviders(Set.of(DefaultComparisonProvider.class.getName()));
        ProviderRegistry.clearAll();

        ComparisonProvider selected = ProviderRegistry.lookup(ComparisonProvider.class);
        // 在存在默认服务声明时，应选择默认实现
        assertNotNull(selected, "DefaultComparisonProvider should be discoverable under whitelist");
        assertTrue(selected instanceof DefaultComparisonProvider);
    }

    @Test
    @DisplayName("显式损坏 ClassLoader 不应崩溃或污染默认发现")
    void broken_services_file_should_not_crash() throws Exception {
        File brokenRoot = new File("src/test/resources/broken-services");
        assertTrue(brokenRoot.exists(), "broken services resource should exist");
        try (URLClassLoader brokenCl = new URLClassLoader(new URL[]{brokenRoot.toURI().toURL()}, null)) {
            assertDoesNotThrow(() ->
                ProviderRegistry.loadProviders(brokenCl, ComparisonProvider.class));
        }

        // 显式加载失败不发布候选；首次默认 lookup 仍使用 Registry 自身 ClassLoader。
        ComparisonProvider selected = ProviderRegistry.lookup(ComparisonProvider.class);
        assertNotNull(selected, "Default discovery should remain available after failed explicit load");
        assertTrue(selected instanceof DefaultComparisonProvider);
    }
}
