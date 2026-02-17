package com.syy.taskflowinsight.spi;

import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link ProviderRegistry} 扩展测试，覆盖 loadProviders、ServiceLoader 路径。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.1
 */
class ProviderRegistryExtendedTest {

    @BeforeEach
    void setup() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
    }

    @AfterEach
    void cleanup() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        System.clearProperty("tfi.spi.allowedProviders");
    }

    // ==================== loadProviders ====================

    @Test
    @DisplayName("loadProviders - null ClassLoader 安全忽略")
    void loadProvidersNullClassLoader() {
        assertThatNoException().isThrownBy(() ->
                ProviderRegistry.loadProviders(null));
    }

    @Test
    @DisplayName("loadProviders - 空 providerTypes 使用默认类型")
    void loadProvidersDefaultTypes() {
        assertThatNoException().isThrownBy(() ->
                ProviderRegistry.loadProviders(Thread.currentThread().getContextClassLoader()));
    }

    @Test
    @DisplayName("loadProviders - 自定义类型列表")
    void loadProvidersCustomTypes() {
        assertThatNoException().isThrownBy(() ->
                ProviderRegistry.loadProviders(
                        Thread.currentThread().getContextClassLoader(),
                        FlowProvider.class, ExportProvider.class));
    }

    // ==================== 系统属性白名单 ====================

    @Test
    @DisplayName("系统属性 - tfi.spi.allowedProviders 影响白名单")
    void systemPropertyAllowedProviders() {
        System.setProperty("tfi.spi.allowedProviders",
                "com.syy.taskflowinsight.spi.ProviderRegistryExtendedTest$TestProvider");

        TestProvider provider = new TestProvider();
        ProviderRegistry.register(FlowProvider.class, provider);

        FlowProvider found = ProviderRegistry.lookup(FlowProvider.class);
        assertThat(found).isSameAs(provider);
    }

    @Test
    @DisplayName("系统属性 - 空值不影响注册")
    void systemPropertyEmpty() {
        System.setProperty("tfi.spi.allowedProviders", "");

        TestProvider provider = new TestProvider();
        ProviderRegistry.register(FlowProvider.class, provider);
        // 空白名单等于禁用白名单，应允许注册
        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isNotNull();
    }

    @Test
    @DisplayName("系统属性 - 含空格逗号分隔正确解析")
    void systemPropertyWithSpaces() {
        System.setProperty("tfi.spi.allowedProviders",
                " com.syy.taskflowinsight.spi.ProviderRegistryExtendedTest$TestProvider , com.other.* ");

        TestProvider provider = new TestProvider();
        ProviderRegistry.register(FlowProvider.class, provider);
        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(provider);
    }

    // ==================== ServiceLoader 缓存 ====================

    @Test
    @DisplayName("clearAll 清除 ServiceLoader 缓存")
    void clearAllClearsServiceLoaderCache() {
        // 先触发一次 ServiceLoader 查找
        ProviderRegistry.lookup(FlowProvider.class);

        // 清空后再查找，应重新加载
        ProviderRegistry.clearAll();
        assertThatNoException().isThrownBy(() ->
                ProviderRegistry.lookup(FlowProvider.class));
    }

    @Test
    @DisplayName("setAllowedProviders 清除 ServiceLoader 缓存")
    void setAllowedClearsServiceLoaderCache() {
        ProviderRegistry.lookup(FlowProvider.class);
        ProviderRegistry.setAllowedProviders(List.of("com.example.*"));
        // 缓存应被清除，下次 lookup 重新走 ServiceLoader
        assertThatNoException().isThrownBy(() ->
                ProviderRegistry.lookup(FlowProvider.class));
    }

    // ==================== 测试 Provider ====================

    static class TestProvider implements FlowProvider {
        @Override public String startSession(String name) { return "test-session"; }
        @Override public void endSession() {}
        @Override public com.syy.taskflowinsight.model.TaskNode startTask(String name) { return null; }
        @Override public void endTask() {}
        @Override public com.syy.taskflowinsight.model.Session currentSession() { return null; }
        @Override public com.syy.taskflowinsight.model.TaskNode currentTask() { return null; }
        @Override public void message(String content, String label) {}
        @Override public int priority() { return 10; }
    }
}
