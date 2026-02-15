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

        assertEquals(10, ProviderRegistry.lookup(ComparisonProvider.class).priority());

        // 移除高优先级的B
        assertTrue(ProviderRegistry.unregister(ComparisonProvider.class, b));
        // 现在应选择A
        assertEquals(5, ProviderRegistry.lookup(ComparisonProvider.class).priority());

        // 再次移除返回true
        assertTrue(ProviderRegistry.unregister(ComparisonProvider.class, a));
        // 无可用注册项，ServiceLoader路径依赖运行环境，允许为 null
        // 仅验证不抛异常
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
    @DisplayName("损坏的服务声明文件不应导致系统崩溃（返回空集合/空结果）")
    void broken_services_file_should_not_crash() throws Exception {
        // 使用仅包含损坏 services 文件的 ClassLoader 作为 TCCL
        File brokenRoot = new File("src/test/resources/broken-services");
        assertTrue(brokenRoot.exists(), "broken services resource should exist");
        URLClassLoader brokenCl = new URLClassLoader(new URL[]{brokenRoot.toURI().toURL()}, null);
        Thread.currentThread().setContextClassLoader(brokenCl);

        ProviderRegistry.clearAll();
        ComparisonProvider selected = ProviderRegistry.lookup(ComparisonProvider.class);
        // 期望加载失败被捕获并返回 null
        assertNull(selected, "Lookup should return null when ServiceLoader config is broken");
    }
}

