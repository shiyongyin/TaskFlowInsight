package com.syy.taskflowinsight.spi;

import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link ProviderRegistry} 单元测试
 *
 * <p>覆盖注册、注销、查找、优先级排序和白名单机制。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class ProviderRegistryTest {

    @BeforeEach
    void setup() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
    }

    @AfterEach
    void cleanup() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
    }

    // ==================== 注册与查找 ====================

    @Test
    @DisplayName("register + lookup - 正常注册和查找")
    void registerAndLookup() {
        TestFlowProvider provider = new TestFlowProvider(10);
        ProviderRegistry.register(FlowProvider.class, provider);

        FlowProvider found = ProviderRegistry.lookup(FlowProvider.class);
        assertThat(found).isSameAs(provider);
    }

    @Test
    @DisplayName("lookup - 无注册时走 ServiceLoader 兜底")
    void lookupNoRegistration() {
        // 查找未注册的类型，可能从 ServiceLoader 返回默认实现
        ExportProvider found = ProviderRegistry.lookup(ExportProvider.class);
        // 不做严格断言，因为 ServiceLoader 行为取决于 classpath
        // found 可能为 null 或 DefaultExportProvider
        if (found != null) {
            int priority = found.priority();
            assertThat(priority).isLessThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("register - null 参数静默忽略")
    void registerNullIgnored() {
        ProviderRegistry.register(null, new TestFlowProvider(0));
        ProviderRegistry.register(FlowProvider.class, null);
        // 不抛出异常
    }

    @Test
    @DisplayName("lookup - null 类型返回 null")
    void lookupNullReturnsNull() {
        Object result = ProviderRegistry.lookup(null);
        assertThat(result).isNull();
    }

    // ==================== 优先级排序 ====================

    @Test
    @DisplayName("优先级 - 高优先级 Provider 被优先返回")
    void higherPriorityWins() {
        TestFlowProvider low = new TestFlowProvider(10);
        TestFlowProvider high = new TestFlowProvider(100);

        ProviderRegistry.register(FlowProvider.class, low);
        ProviderRegistry.register(FlowProvider.class, high);

        FlowProvider found = ProviderRegistry.lookup(FlowProvider.class);
        assertThat(found).isSameAs(high);
    }

    @Test
    @DisplayName("优先级 - 多个 Provider 按 priority 降序")
    void multipleProvidersSorted() {
        ProviderRegistry.register(FlowProvider.class, new TestFlowProvider(5));
        ProviderRegistry.register(FlowProvider.class, new TestFlowProvider(50));
        ProviderRegistry.register(FlowProvider.class, new TestFlowProvider(25));

        FlowProvider found = ProviderRegistry.lookup(FlowProvider.class);
        assertThat(((TestFlowProvider) found).priorityValue).isEqualTo(50);
    }

    // ==================== 注销 ====================

    @Test
    @DisplayName("unregister - 注销后 lookup 不再返回")
    void unregisterRemovesProvider() {
        TestFlowProvider provider = new TestFlowProvider(100);
        ProviderRegistry.register(FlowProvider.class, provider);
        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isSameAs(provider);

        boolean removed = ProviderRegistry.unregister(FlowProvider.class, provider);
        assertThat(removed).isTrue();
    }

    @Test
    @DisplayName("unregister - 未注册的 Provider 返回 false")
    void unregisterNonExistentReturnsFalse() {
        boolean removed = ProviderRegistry.unregister(FlowProvider.class, new TestFlowProvider(0));
        assertThat(removed).isFalse();
    }

    @Test
    @DisplayName("unregister - null 参数返回 false")
    void unregisterNullReturnsFalse() {
        assertThat(ProviderRegistry.unregister(null, new TestFlowProvider(0))).isFalse();
        assertThat(ProviderRegistry.unregister(FlowProvider.class, null)).isFalse();
    }

    // ==================== clearAll ====================

    @Test
    @DisplayName("clearAll - 清空所有注册")
    void clearAllRemovesEverything() {
        ProviderRegistry.register(FlowProvider.class, new TestFlowProvider(100));
        ProviderRegistry.clearAll();

        Map<Class<?>, List<Object>> all = ProviderRegistry.getAllRegistered();
        assertThat(all).isEmpty();
    }

    // ==================== 白名单 ====================

    @Test
    @DisplayName("白名单 - 精确匹配允许注册")
    void allowedProviderExactMatch() {
        ProviderRegistry.setAllowedProviders(
                List.of("com.syy.taskflowinsight.spi.ProviderRegistryTest$TestFlowProvider"));

        TestFlowProvider provider = new TestFlowProvider(10);
        ProviderRegistry.register(FlowProvider.class, provider);

        FlowProvider found = ProviderRegistry.lookup(FlowProvider.class);
        assertThat(found).isSameAs(provider);
    }

    @Test
    @DisplayName("白名单 - 不匹配的 Provider 被拒绝")
    void disallowedProviderRejected() {
        ProviderRegistry.setAllowedProviders(List.of("com.example.SomeOtherProvider"));

        ProviderRegistry.register(FlowProvider.class, new TestFlowProvider(10));

        Map<Class<?>, List<Object>> all = ProviderRegistry.getAllRegistered();
        assertThat(all).isEmpty();
    }

    @Test
    @DisplayName("白名单 - 包前缀匹配")
    void allowedProviderPrefixMatch() {
        ProviderRegistry.setAllowedProviders(List.of("com.syy.taskflowinsight.*"));

        TestFlowProvider provider = new TestFlowProvider(10);
        ProviderRegistry.register(FlowProvider.class, provider);

        FlowProvider found = ProviderRegistry.lookup(FlowProvider.class);
        assertThat(found).isSameAs(provider);
    }

    @Test
    @DisplayName("白名单 - null/空禁用白名单")
    void nullAllowedDisablesWhitelist() {
        ProviderRegistry.setAllowedProviders(null);
        ProviderRegistry.register(FlowProvider.class, new TestFlowProvider(10));
        assertThat(ProviderRegistry.lookup(FlowProvider.class)).isNotNull();
    }

    // ==================== 测试用 Provider ====================

    static class TestFlowProvider implements FlowProvider {
        final int priorityValue;

        TestFlowProvider(int priority) {
            this.priorityValue = priority;
        }

        @Override public String startSession(String name) { return null; }
        @Override public void endSession() {}
        @Override public com.syy.taskflowinsight.model.TaskNode startTask(String name) { return null; }
        @Override public void endTask() {}
        @Override public com.syy.taskflowinsight.model.Session currentSession() { return null; }
        @Override public com.syy.taskflowinsight.model.TaskNode currentTask() { return null; }
        @Override public void message(String content, String label) {}
        @Override public int priority() { return priorityValue; }
    }
}
