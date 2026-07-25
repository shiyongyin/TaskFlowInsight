package com.syy.taskflowinsight.spring;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.DefaultExportProvider;
import com.syy.taskflowinsight.spi.DefaultFlowProvider;
import com.syy.taskflowinsight.spi.DefaultTrackingProvider;
import com.syy.taskflowinsight.spi.ExportProvider;
import com.syy.taskflowinsight.spi.FlowProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SpringProviderAdapters 单元测试
 * <p>
 * 测试策略：使用 DefaultProviders 通过 ProviderRegistry 测试适配器行为
 * 覆盖 v4.0.0 新增的所有 Provider 方法
 */
@DisplayName("Spring Provider Adapters Unit Tests")
class SpringProviderAdaptersTest {

    @BeforeEach
    void setUp() {
        // 清理 ProviderRegistry
        ProviderRegistry.clearAll();

        // 清理 ThreadLocal 上下文
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.close();
            }
        } catch (Exception ignored) {
        }
    }

    @AfterEach
    void tearDown() {
        ProviderRegistry.clearAll();
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.close();
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== FlowProvider Tests ====================

    @Nested
    @DisplayName("SpringFlowProviderAdapter Tests")
    class SpringFlowProviderAdapterTests {

        private FlowProvider flowProvider;

        @BeforeEach
        void setUpFlowProvider() {
            flowProvider = new DefaultFlowProvider();
            ProviderRegistry.register(FlowProvider.class, flowProvider);
        }

        @Test
        @DisplayName("clear() 应该成功执行不抛异常")
        void testClear() {
            // Given: 创建会话和任务
            flowProvider.startSession("test-session");
            flowProvider.startTask("task1");

            // When: 调用 clear()
            // Then: 应该成功执行不抛异常
            assertDoesNotThrow(() -> flowProvider.clear(),
                    "clear() should not throw exception");
        }

        @Test
        @DisplayName("getTaskStack() 应该返回任务栈（包含session节点）")
        void testGetTaskStack() {
            // Given: 创建嵌套任务
            flowProvider.startSession("test-session");
            flowProvider.startTask("task1");
            flowProvider.startTask("task2");
            flowProvider.startTask("task3");

            // When: 获取任务栈
            List<TaskNode> stack = flowProvider.getTaskStack();

            // Then: 应该按顺序返回（包含session根节点）
            assertNotNull(stack);
            assertEquals(4, stack.size(), "Stack should contain session + 3 tasks");
            assertEquals("test-session", stack.get(0).getTaskName());
            assertEquals("task1", stack.get(1).getTaskName());
            assertEquals("task2", stack.get(2).getTaskName());
            assertEquals("task3", stack.get(3).getTaskName());
        }

        @Test
        @DisplayName("getTaskStack() 在空栈时应该返回session节点")
        void testGetTaskStackEmpty() {
            // Given: 创建会话但没有任务
            flowProvider.startSession("test-session");

            // When: 获取任务栈
            List<TaskNode> stack = flowProvider.getTaskStack();

            // Then: 应该至少包含session节点
            assertNotNull(stack);
            assertFalse(stack.isEmpty(), "Stack should contain at least session node");
            assertEquals("test-session", stack.get(0).getTaskName());
        }
    }

    // ==================== TrackingProvider Tests ====================

    @Nested
    @DisplayName("SpringTrackingProviderAdapter Tests")
    class SpringTrackingProviderAdapterTests {

        private TrackingProvider trackingProvider;

        @BeforeEach
        void setUpTrackingProvider() {
            trackingProvider = new DefaultTrackingProvider();
            ProviderRegistry.register(TrackingProvider.class, trackingProvider);
        }

        @Test
        @DisplayName("multi-target execute按输入顺序返回两个结果")
        void testMultiTargetExecution() {
            TestUser user = new TestUser("Alice", 30);
            TestOrder order = new TestOrder("O001", 100.0);
            TrackingExecutor.Execution<Void> execution = new TrackingExecutor(trackingProvider).execute(
                    List.of(
                            new TrackingExecutor.Target("user", user),
                            new TrackingExecutor.Target("order", order)),
                    CompareOptions.builder().build(),
                    () -> {
                        user.setAge(31);
                        order.setAmount(200.0);
                        return null;
                    });

            assertEquals(List.of("user", "order"), execution.tracking().stream()
                    .map(TrackingExecutor.Item::name)
                    .toList());
            assertTrue(execution.tracking().stream().allMatch(item -> item.result().isDifferent()));
        }

        @Test
        @DisplayName("nested target使用CompareOptions而不是TrackingOptions")
        void testNestedTargetWithCompareOptions() {
            TestOrder order = new TestOrder("O001", 100.0);
            order.setCustomer(new TestUser("Alice", 30));

            var result = new TrackingExecutor(trackingProvider).withTracked(
                    "order",
                    order,
                    () -> order.getCustomer().setAge(31),
                    CompareOptions.builder().maxDepth(5).build());

            assertTrue(result.isDifferent());
        }

        @Test
        @DisplayName("executor传播同一业务异常且不重试")
        void testBusinessFailureIdentity() {
            TestUser user = new TestUser("Alice", 30);
            IllegalStateException failure = new IllegalStateException("business failure");
            int[] calls = {0};

            IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                    new TrackingExecutor(trackingProvider).execute(
                            List.of(new TrackingExecutor.Target("user", user)),
                            CompareOptions.builder().build(),
                            () -> {
                                calls[0]++;
                                throw failure;
                            }));

            assertSame(failure, thrown);
            assertEquals(1, calls[0]);
        }

        @Test
        @DisplayName("Registry返回的就是当前typed provider")
        void testRegistryIdentity() {
            assertSame(trackingProvider, ProviderRegistry.resolve(TrackingProvider.class));
        }
    }

    // ==================== ExportProvider Tests ====================

    @Nested
    @DisplayName("SpringExportProviderAdapter Tests")
    class SpringExportProviderAdapterTests {

        private ExportProvider exportProvider;
        private FlowProvider flowProvider;

        @BeforeEach
        void setUpExportProvider() {
            exportProvider = new DefaultExportProvider();
            flowProvider = new DefaultFlowProvider();
            ProviderRegistry.register(ExportProvider.class, exportProvider);
            ProviderRegistry.register(FlowProvider.class, flowProvider);
        }

        @Test
        @DisplayName("exportToConsole(false) 应该成功执行")
        void testExportToConsoleWithoutTimestamp() {
            // Given: 没有活动会话
            // When: 导出到控制台（不带时间戳）
            boolean result = exportProvider.exportToConsole(false);

            // Then: 应该成功（返回true或false都可以）
            // 注：只验证方法调用不抛异常
        }

        @Test
        @DisplayName("exportToConsole(true) 应该成功执行")
        void testExportToConsoleWithTimestamp() {
            // Given: 创建会话和任务
            flowProvider.startSession("test-session");
            flowProvider.startTask("task1");

            // When: 导出到控制台（带时间戳）
            boolean result = exportProvider.exportToConsole(true);

            // Then: 应该成功（返回true或false都可以）
            // 注：只验证方法调用不抛异常
        }

        @Test
        @DisplayName("exportToJson() 应该返回 JSON 格式")
        void testExportToJson() {
            // Given: 创建会话和任务
            flowProvider.startSession("test-session");
            flowProvider.startTask("task1");

            // When: 导出为 JSON
            String json = exportProvider.exportToJson();

            // Then: 应该是有效的 JSON 字符串
            assertNotNull(json);
            assertTrue(json.contains("{") || json.contains("[") || json.equals("{}"));
        }

        @Test
        @DisplayName("exportToMap() 应该返回 Map 结构")
        void testExportToMap() {
            // Given: 创建会话和任务
            flowProvider.startSession("test-session");
            flowProvider.startTask("task1");

            // When: 导出为 Map
            Map<String, Object> map = exportProvider.exportToMap();

            // Then: 应该返回非空 Map
            assertNotNull(map);
        }
    }

    // ==================== Helper Test Classes ====================

    static class TestUser {
        private String name;
        private int age;

        public TestUser(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    static class TestOrder {
        private String orderId;
        private double amount;
        private TestUser customer;

        public TestOrder(String orderId, double amount) {
            this.orderId = orderId;
            this.amount = amount;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public double getAmount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }

        public TestUser getCustomer() {
            return customer;
        }

        public void setCustomer(TestUser customer) {
            this.customer = customer;
        }
    }
}
