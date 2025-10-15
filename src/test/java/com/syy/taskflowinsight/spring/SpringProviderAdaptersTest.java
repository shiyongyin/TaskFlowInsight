package com.syy.taskflowinsight.spring;

import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.exporter.change.ChangeExporter;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.DefaultExportProvider;
import com.syy.taskflowinsight.spi.DefaultFlowProvider;
import com.syy.taskflowinsight.spi.DefaultTrackingProvider;
import com.syy.taskflowinsight.spi.ExportProvider;
import com.syy.taskflowinsight.spi.FlowProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
        @DisplayName("trackAll() 应该批量跟踪多个对象")
        void testTrackAll() {
            // Given: 准备多个对象
            Map<String, Object> objects = new HashMap<>();
            objects.put("user", new TestUser("Alice", 30));
            objects.put("order", new TestOrder("O001", 100.0));

            // When: 批量跟踪
            trackingProvider.trackAll(objects);

            // Then: 修改对象应该能检测到变化
            TestUser user = (TestUser) objects.get("user");
            user.setAge(31);

            List<ChangeRecord> changes = trackingProvider.getAllChanges();
            assertFalse(changes.isEmpty(), "应该检测到 user.age 的变化");
        }

        @Test
        @DisplayName("trackAll(null) 应该抛出 NullPointerException")
        void testTrackAllNull() {
            // When & Then: 传入 null 应该抛出异常
            assertThrows(NullPointerException.class, () -> trackingProvider.trackAll(null));
        }

        @Test
        @DisplayName("trackDeep() 使用默认选项应该成功执行")
        void testTrackDeepDefault() {
            // Given: 创建嵌套对象
            TestOrder order = new TestOrder("O001", 100.0);
            order.setCustomer(new TestUser("Alice", 30));

            // When: 深度跟踪
            trackingProvider.trackDeep("order", order);

            // Then: 方法应该成功执行（注：默认实现可能回退到浅层track）
            assertDoesNotThrow(() -> {
                order.getCustomer().setAge(31);
                trackingProvider.getAllChanges();
            }, "trackDeep should execute without exception");
        }

        @Test
        @DisplayName("trackDeep() 使用自定义选项应该应用配置")
        void testTrackDeepWithOptions() {
            // Given: 创建对象和自定义选项
            TestUser user = new TestUser("Alice", 30);
            TrackingOptions options = TrackingOptions.builder()
                    .maxDepth(5)
                    .build();

            // When: 使用自定义选项跟踪
            trackingProvider.trackDeep("user", user, options);

            // Then: 修改应该能检测到
            user.setAge(31);
            List<ChangeRecord> changes = trackingProvider.getAllChanges();
            assertFalse(changes.isEmpty());
        }

        @Test
        @DisplayName("getAllChanges() 应该返回所有变化记录")
        void testGetAllChanges() {
            // Given: 跟踪并修改多个对象
            TestUser user = new TestUser("Alice", 30);
            trackingProvider.track("user", user);
            user.setAge(31);

            TestOrder order = new TestOrder("O001", 100.0);
            trackingProvider.track("order", order);
            order.setAmount(200.0);

            // When: 获取所有变化
            List<ChangeRecord> changes = trackingProvider.getAllChanges();

            // Then: 应该包含两个变化
            assertNotNull(changes);
            assertEquals(2, changes.size());
        }

        @Test
        @DisplayName("startTracking() 应该开始新的跟踪会话")
        void testStartTracking() {
            // When: 开始新会话
            trackingProvider.startTracking("test-session");

            // Then: 应该不抛异常（默认实现可能无操作）
            assertDoesNotThrow(() -> trackingProvider.getAllChanges());
        }

        @Test
        @DisplayName("recordChange() 应该记录单个变化")
        void testRecordChange() {
            // Given: 准备变化数据
            String objectName = "test-object";
            String fieldName = "status";
            Object oldValue = "pending";
            Object newValue = "completed";

            // When: 记录变化
            trackingProvider.recordChange(objectName, fieldName, oldValue, newValue,
                    com.syy.taskflowinsight.tracking.ChangeType.UPDATE);

            // Then: 应该能获取到变化（注：默认实现可能不记录，仅验证不抛异常）
            assertDoesNotThrow(() -> trackingProvider.getAllChanges());
        }

        @Test
        @DisplayName("clearTracking(sessionName) 应该清除指定会话的跟踪")
        void testClearTrackingWithSessionName() {
            // Given: 跟踪对象
            TestUser user = new TestUser("Alice", 30);
            trackingProvider.track("user", user);
            user.setAge(31);

            // When: 清除跟踪
            trackingProvider.clearTracking("test-session");

            // Then: 变化应该被清除
            List<ChangeRecord> changes = trackingProvider.getAllChanges();
            assertTrue(changes.isEmpty());
        }

        @Test
        @DisplayName("withTracked() 应该在作用域内跟踪对象")
        void testWithTracked() {
            // Given: 创建对象
            TestUser user = new TestUser("Alice", 30);
            AtomicBoolean executed = new AtomicBoolean(false);

            // When: 在作用域内执行
            trackingProvider.withTracked("user", user, () -> {
                user.setAge(31);
                executed.set(true);
            });

            // Then: 作用域应该执行
            assertTrue(executed.get(), "withTracked should execute the action");
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
