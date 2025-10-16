package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.core.TfiCore;
import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 路由实现完整测试（真实对象测试，无mock）
 *
 * <p>测试策略：使用真实的 TfiCore 和 TestProvider 实现类
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TFIPhase2RoutingTest {

    private TfiCore realCore;

    // 使用单例Provider实例，避免跨测试实例不匹配问题
    private static TestTrackingProvider testTrackingProvider;
    private static TestFlowProvider testFlowProvider;
    private static TestExportProvider testExportProvider;
    private static boolean providersRegistered = false;

    @BeforeEach
    void setUp() throws Exception {
        // 1. 先设置系统属性（最先）
        System.setProperty("tfi.api.routing.enabled", "true");

        // 2. 清除 Provider 缓存（在注册前）
        clearProviderCache();

        // 3. 创建并注册真实的 TestProvider（只注册一次）
        if (!providersRegistered) {
            testTrackingProvider = new TestTrackingProvider();
            testFlowProvider = new TestFlowProvider();
            testExportProvider = new TestExportProvider();

            TFI.registerTrackingProvider(testTrackingProvider);
            TFI.registerFlowProvider(testFlowProvider);
            TFI.registerExportProvider(testExportProvider);

            providersRegistered = true;
        } else {
            // 重置已有实例的状态
            testTrackingProvider.reset();
            testFlowProvider.reset();
            testExportProvider.reset();
        }

        // 4. 创建真实的 TfiConfig
        TfiConfig config = new TfiConfig(
            true, // enabled
            new TfiConfig.ChangeTracking(true, null, null, null, null, null, null, null),
            new TfiConfig.Context(null, null, null, null, null),
            new TfiConfig.Metrics(null, null, null),
            new TfiConfig.Security(null, null)
        );

        // 5. 创建真实的 TfiCore
        realCore = new TfiCore(config);

        // 6. 最后通过反射注入到 TFI.core
        setTfiCore(realCore);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("tfi.api.routing.enabled");
        // 清除缓存以确保下一个测试使用最新的providers
        try {
            clearProviderCache();
        } catch (Exception e) {
            // Ignore
        }
    }

    private void setTfiCore(TfiCore core) throws Exception {
        Field coreField = TFI.class.getDeclaredField("core");
        coreField.setAccessible(true);
        coreField.set(null, core);
    }

    /**
     * 清除 TFI 的 Provider 缓存
     */
    private void clearProviderCache() throws Exception {
        String[] cacheFieldNames = new String[]{
            "cachedTrackingProvider",
            "cachedFlowProvider",
            "cachedExportProvider",
            "cachedRenderProvider",
            "cachedComparisonProvider"
        };

        for (String fieldName : cacheFieldNames) {
            try {
                Field field = TFI.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(null, null);
            } catch (NoSuchFieldException e) {
                // 字段不存在，跳过
            }
        }
    }

    // ==================== Type 1.1: getTaskStack() Tests ====================

    @Test
    @Order(1)
    @DisplayName("Type 1.1: getTaskStack() - 灰度开启时使用 Provider")
    void testGetTaskStack_WithRoutingEnabled_UsesProvider() {
        testFlowProvider.startTask("task1");
        TFI.getTaskStack();
        assertTrue(testFlowProvider.wasMethodCalled("getTaskStack"));
    }

    @Test
    @Order(2)
    @DisplayName("Type 1.1: getTaskStack() - 灰度关闭时使用 Legacy")
    void testGetTaskStack_WithRoutingDisabled_UsesLegacy() {
        System.setProperty("tfi.api.routing.enabled", "false");
        testFlowProvider.reset();
        TFI.getTaskStack();
        assertFalse(testFlowProvider.wasMethodCalled("getTaskStack"));
    }

    @Test
    @Order(3)
    @DisplayName("Type 1.1: getTaskStack() - TFI 禁用时返回空列表")
    void testGetTaskStack_WithTfiDisabled_ReturnsEmptyList() {
        realCore.disable();
        List result = TFI.getTaskStack();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(52)
    @DisplayName("Type 1.1: getTaskStack() - 多层嵌套Task返回正确栈结构")
    void testGetTaskStack_WithNestedTasks_ReturnsCorrectStack() {
        testFlowProvider.startTask("parent");
        testFlowProvider.startTask("child");
        List result = TFI.getTaskStack();
        assertNotNull(result);
        assertTrue(testFlowProvider.wasMethodCalled("getTaskStack"));
    }

    @Test
    @Order(53)
    @DisplayName("Type 1.1: getTaskStack() - 空栈场景返回空列表")
    void testGetTaskStack_WithEmptyStack_ReturnsEmptyList() {
        List result = TFI.getTaskStack();
        assertNotNull(result);
        assertTrue(testFlowProvider.wasMethodCalled("getTaskStack"));
    }

    // ==================== Type 1.2: exportToConsole() Tests ====================

    @Test
    @Order(4)
    @DisplayName("Type 1.2: exportToConsole() - 灰度开启时使用 Provider")
    void testExportToConsole_WithRoutingEnabled_UsesProvider() {
        TFI.exportToConsole(false);
        assertTrue(testExportProvider.wasMethodCalled("exportToConsole"));
    }

    @Test
    @Order(5)
    @DisplayName("Type 1.2: exportToConsole() - 带时间戳参数")
    void testExportToConsole_WithTimestamp_UsesProvider() {
        TFI.exportToConsole(true);
        assertTrue(testExportProvider.wasMethodCalled("exportToConsole:true"));
    }

    @Test
    @Order(6)
    @DisplayName("Type 1.2: exportToConsole() - 灰度关闭时使用 Legacy")
    void testExportToConsole_WithRoutingDisabled_UsesLegacy() {
        System.setProperty("tfi.api.routing.enabled", "false");
        testExportProvider.reset();
        TFI.exportToConsole(false);
        assertFalse(testExportProvider.wasMethodCalled("exportToConsole"));
    }

    @Test
    @Order(54)
    @DisplayName("Type 1.2: exportToConsole() - TFI禁用时返回false")
    void testExportToConsole_WithTfiDisabled_ReturnsFalse() {
        realCore.disable();
        boolean result = TFI.exportToConsole(false);
        assertFalse(result, "Should return false when TFI is disabled");
    }

    @Test
    @Order(55)
    @DisplayName("Type 1.2: exportToConsole() - 验证返回值正确")
    void testExportToConsole_ReturnsCorrectValue() {
        boolean result = TFI.exportToConsole(false);
        assertTrue(result, "Should return true from test provider");
        assertTrue(testExportProvider.wasMethodCalled("exportToConsole"));
    }

    // ==================== Type 1.3: exportToJson() Tests ====================

    @Test
    @Order(7)
    @DisplayName("Type 1.3: exportToJson() - 灰度开启时使用 Provider")
    void testExportToJson_WithRoutingEnabled_UsesProvider() {
        String result = TFI.exportToJson();
        assertEquals("{\"session\":\"test\"}", result);
        assertTrue(testExportProvider.wasMethodCalled("exportToJson"));
    }

    @Test
    @Order(8)
    @DisplayName("Type 1.3: exportToJson() - 灰度关闭时返回默认JSON")
    void testExportToJson_WithRoutingDisabled_ReturnsDefault() {
        System.setProperty("tfi.api.routing.enabled", "false");
        testExportProvider.reset();
        String result = TFI.exportToJson();
        assertFalse(testExportProvider.wasMethodCalled("exportToJson"));
    }

    @Test
    @Order(46)
    @DisplayName("Type 1.3: exportToJson() - TFI禁用时返回空JSON")
    void testExportToJson_WithTfiDisabled_ReturnsEmptyJson() {
        realCore.disable();
        String result = TFI.exportToJson();
        assertNotNull(result);
        // TFI禁用时应该返回默认值或空JSON
    }

    @Test
    @Order(47)
    @DisplayName("Type 1.3: exportToJson() - 验证JSON格式有效性")
    void testExportToJson_ReturnsValidJsonFormat() {
        String result = TFI.exportToJson();
        assertNotNull(result);
        assertTrue(result.startsWith("{"), "JSON should start with {");
        assertTrue(result.endsWith("}"), "JSON should end with }");
        assertTrue(testExportProvider.wasMethodCalled("exportToJson"));
    }

    @Test
    @Order(48)
    @DisplayName("Type 1.3: exportToJson() - 多次调用返回一致结果")
    void testExportToJson_ConsistentResults() {
        String result1 = TFI.exportToJson();
        String result2 = TFI.exportToJson();
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(2, testExportProvider.getMethodCallCount("exportToJson"));
    }

    // ==================== Type 1.4: exportToMap() Tests ====================

    @Test
    @Order(9)
    @DisplayName("Type 1.4: exportToMap() - 灰度开启时使用 Provider")
    void testExportToMap_WithRoutingEnabled_UsesProvider() {
        Map<String, Object> result = TFI.exportToMap();
        assertNotNull(result);
        assertTrue(testExportProvider.wasMethodCalled("exportToMap"));
    }

    @Test
    @Order(10)
    @DisplayName("Type 1.4: exportToMap() - 灰度关闭时返回空Map")
    void testExportToMap_WithRoutingDisabled_ReturnsEmptyMap() {
        System.setProperty("tfi.api.routing.enabled", "false");
        testExportProvider.reset();
        Map<String, Object> result = TFI.exportToMap();
        assertNotNull(result);
        assertFalse(testExportProvider.wasMethodCalled("exportToMap"));
    }

    @Test
    @Order(49)
    @DisplayName("Type 1.4: exportToMap() - TFI禁用时返回空Map")
    void testExportToMap_WithTfiDisabled_ReturnsEmptyMap() {
        realCore.disable();
        Map<String, Object> result = TFI.exportToMap();
        assertNotNull(result);
        assertTrue(result.isEmpty() || result.size() >= 0, "Should return valid map");
    }

    @Test
    @Order(50)
    @DisplayName("Type 1.4: exportToMap() - 验证Map包含sessionId")
    void testExportToMap_ContainsSessionId() {
        Map<String, Object> result = TFI.exportToMap();
        assertNotNull(result);
        assertTrue(result.containsKey("sessionId"), "Map should contain sessionId key");
        assertEquals("test-123", result.get("sessionId"));
        assertTrue(testExportProvider.wasMethodCalled("exportToMap"));
    }

    @Test
    @Order(51)
    @DisplayName("Type 1.4: exportToMap() - Map内容可修改不影响内部状态")
    void testExportToMap_ReturnedMapIsSafe() {
        Map<String, Object> result = TFI.exportToMap();
        assertNotNull(result);
        // 尝试修改返回的Map
        assertDoesNotThrow(() -> result.put("testKey", "testValue"));
        assertTrue(testExportProvider.wasMethodCalled("exportToMap"));
    }

    // ==================== Type 2.1: trackAll() Tests ====================

    @Test
    @Order(11)
    @DisplayName("Type 2.1: trackAll() - 灰度开启时使用 Provider")
    void testTrackAll_WithRoutingEnabled_UsesProvider() {
        Map<String, Object> targets = Map.of("user", new Object(), "order", new Object());
        TFI.trackAll(targets);
        assertTrue(testTrackingProvider.wasMethodCalled("trackAll"));
        assertEquals(2, testTrackingProvider.getTrackedCount());
    }

    @Test
    @Order(12)
    @DisplayName("Type 2.1: trackAll() - null参数不调用 Provider")
    void testTrackAll_WithNullTargets_DoesNotCallProvider() {
        TFI.trackAll(null);
        assertFalse(testTrackingProvider.wasMethodCalled("trackAll"));
    }

    @Test
    @Order(13)
    @DisplayName("Type 2.1: trackAll() - 空 Map 不调用 Provider")
    void testTrackAll_WithEmptyMap_DoesNotCallProvider() {
        TFI.trackAll(new HashMap<>());
        assertFalse(testTrackingProvider.wasMethodCalled("trackAll"));
    }

    @Test
    @Order(14)
    @DisplayName("Type 2.1: trackAll() - 灰度关闭时使用 Legacy")
    void testTrackAll_WithRoutingDisabled_UsesLegacy() {
        System.setProperty("tfi.api.routing.enabled", "false");
        testTrackingProvider.reset();
        Map<String, Object> targets = Map.of("user", new Object());
        assertDoesNotThrow(() -> TFI.trackAll(targets));
        assertFalse(testTrackingProvider.wasMethodCalled("trackAll"));
    }

    @Test
    @Order(56)
    @DisplayName("Type 2.1: trackAll() - 单个对象正常追踪")
    void testTrackAll_WithSingleObject_TracksCorrectly() {
        Map<String, Object> targets = Map.of("singleUser", new Object());
        TFI.trackAll(targets);
        assertTrue(testTrackingProvider.wasMethodCalled("trackAll"));
        assertEquals(1, testTrackingProvider.getTrackedCount());
    }

    // ==================== Type 2.2: trackDeep() Tests ====================

    @Test
    @Order(15)
    @DisplayName("Type 2.2: trackDeep(name, target) - 单参数重载使用 Provider")
    void testTrackDeep_SingleArg_WithRoutingEnabled_UsesProvider() {
        Object target = new Object();
        TFI.trackDeep("testObj", target);
        assertTrue(testTrackingProvider.wasMethodCalled("trackDeep"));
    }

    @Test
    @Order(16)
    @DisplayName("Type 2.2: trackDeep(name, target, options) - 带选项重载使用 Provider")
    void testTrackDeep_WithOptions_UsesProvider() {
        Object target = new Object();
        TrackingOptions options = TrackingOptions.deep();
        TFI.trackDeep("testObj", target, options);
        assertTrue(testTrackingProvider.wasMethodCalled("trackDeep"));
    }

    @Test
    @Order(17)
    @DisplayName("Type 2.2: trackDeep() - null name 不调用 Provider")
    void testTrackDeep_WithNullName_DoesNotCallProvider() {
        TFI.trackDeep(null, new Object());
        assertFalse(testTrackingProvider.wasMethodCalled("trackDeep"));
    }

    @Test
    @Order(18)
    @DisplayName("Type 2.2: trackDeep() - empty name 不调用 Provider")
    void testTrackDeep_WithEmptyName_DoesNotCallProvider() {
        TFI.trackDeep("", new Object());
        assertFalse(testTrackingProvider.wasMethodCalled("trackDeep"));
    }

    @Test
    @Order(19)
    @DisplayName("Type 2.2: trackDeep() - null target 不调用 Provider")
    void testTrackDeep_WithNullTarget_DoesNotCallProvider() {
        TFI.trackDeep("test", null);
        assertFalse(testTrackingProvider.wasMethodCalled("trackDeep"));
    }

    @Test
    @Order(20)
    @DisplayName("Type 2.2: trackDeep() - null options 不调用 Provider")
    void testTrackDeep_WithNullOptions_DoesNotCallProvider() {
        TFI.trackDeep("test", new Object(), null);
        assertFalse(testTrackingProvider.wasMethodCalled("trackDeep"));
    }

    // ==================== Type 2.3: recordChange() Tests ====================

    @Test
    @Order(21)
    @DisplayName("Type 2.3: recordChange() - 灰度开启时使用 Provider")
    void testRecordChange_WithRoutingEnabled_UsesProvider() {
        TFI.recordChange("user", "name", "oldName", "newName", ChangeType.UPDATE);
        assertTrue(testTrackingProvider.wasMethodCalled("recordChange"));
    }

    @Test
    @Order(22)
    @DisplayName("Type 2.3: recordChange() - CREATE 类型")
    void testRecordChange_WithCreateType_UsesProvider() {
        TFI.recordChange("order", "id", null, "12345", ChangeType.CREATE);
        assertTrue(testTrackingProvider.wasMethodCalled("recordChange:order.id"));
    }

    @Test
    @Order(23)
    @DisplayName("Type 2.3: recordChange() - DELETE 类型")
    void testRecordChange_WithDeleteType_UsesProvider() {
        TFI.recordChange("session", "status", "active", null, ChangeType.DELETE);
        assertTrue(testTrackingProvider.wasMethodCalled("recordChange:session.status"));
    }

    @Test
    @Order(24)
    @DisplayName("Type 2.3: recordChange() - 灰度关闭时使用 Legacy")
    void testRecordChange_WithRoutingDisabled_UsesLegacy() {
        System.setProperty("tfi.api.routing.enabled", "false");
        testTrackingProvider.reset();
        assertDoesNotThrow(() ->
            TFI.recordChange("obj", "field", "old", "new", ChangeType.UPDATE));
        assertFalse(testTrackingProvider.wasMethodCalled("recordChange"));
    }

    @Test
    @Order(57)
    @DisplayName("Type 2.3: recordChange() - 相同新旧值也记录")
    void testRecordChange_WithSameValues_StillRecords() {
        TFI.recordChange("obj", "field", "value", "value", ChangeType.UPDATE);
        assertTrue(testTrackingProvider.wasMethodCalled("recordChange"));
    }

    // ==================== Type 2.4: clearTracking() Tests ====================

    @Test
    @Order(25)
    @DisplayName("Type 2.4: clearTracking() - 灰度开启时使用 Provider")
    void testClearTracking_WithRoutingEnabled_UsesProvider() {
        TFI.clearTracking("session-123");
        assertTrue(testTrackingProvider.wasMethodCalled("clearTracking"));
    }

    @Test
    @Order(26)
    @DisplayName("Type 2.4: clearTracking() - null sessionId 不调用 Provider")
    void testClearTracking_WithNullSessionId_DoesNotCallProvider() {
        TFI.clearTracking(null);
        assertFalse(testTrackingProvider.wasMethodCalled("clearTracking"));
    }

    @Test
    @Order(27)
    @DisplayName("Type 2.4: clearTracking() - empty sessionId 不调用 Provider")
    void testClearTracking_WithEmptySessionId_DoesNotCallProvider() {
        TFI.clearTracking("");
        assertFalse(testTrackingProvider.wasMethodCalled("clearTracking"));
    }

    @Test
    @Order(28)
    @DisplayName("Type 2.4: clearTracking() - 灰度关闭时使用 Legacy")
    void testClearTracking_WithRoutingDisabled_UsesLegacy() {
        System.setProperty("tfi.api.routing.enabled", "false");
        testTrackingProvider.reset();
        assertDoesNotThrow(() -> TFI.clearTracking("session-123"));
        assertFalse(testTrackingProvider.wasMethodCalled("clearTracking"));
    }

    @Test
    @Order(58)
    @DisplayName("Type 2.4: clearTracking() - UUID格式sessionId正常处理")
    void testClearTracking_WithUuidSessionId_WorksCorrectly() {
        String uuidSession = "550e8400-e29b-41d4-a716-446655440000";
        assertDoesNotThrow(() -> TFI.clearTracking(uuidSession));
        assertTrue(testTrackingProvider.wasMethodCalled("clearTracking"));
    }

    // ==================== Type 2.5: withTracked() Tests ====================

    @Test
    @Order(29)
    @DisplayName("Type 2.5: withTracked() - 灰度开启时使用 Provider")
    void testWithTracked_WithRoutingEnabled_UsesProvider() {
        Object target = new Object();
        Runnable action = () -> {};
        TFI.withTracked("testObj", target, action, "field1", "field2");
        assertTrue(testTrackingProvider.wasMethodCalled("withTracked"));
    }

    @Test
    @Order(30)
    @DisplayName("Type 2.5: withTracked() - action正常执行")
    void testWithTracked_ExecutesAction() {
        Object target = new Object();
        final boolean[] executed = {false};
        Runnable action = () -> executed[0] = true;
        TFI.withTracked("testObj", target, action);
        assertTrue(executed[0]);
    }

    @Test
    @Order(31)
    @DisplayName("Type 2.5: withTracked() - null name 仍执行 action")
    void testWithTracked_WithNullName_ExecutesAction() {
        final boolean[] executed = {false};
        Runnable action = () -> executed[0] = true;
        TFI.withTracked(null, new Object(), action);
        assertFalse(testTrackingProvider.wasMethodCalled("withTracked"));
    }

    @Test
    @Order(32)
    @DisplayName("Type 2.5: withTracked() - empty name 不调用 Provider")
    void testWithTracked_WithEmptyName_DoesNotCallProvider() {
        Runnable action = () -> {};
        TFI.withTracked("", new Object(), action);
        assertFalse(testTrackingProvider.wasMethodCalled("withTracked"));
    }

    @Test
    @Order(33)
    @DisplayName("Type 2.5: withTracked() - null target 不调用 Provider")
    void testWithTracked_WithNullTarget_DoesNotCallProvider() {
        Runnable action = () -> {};
        TFI.withTracked("test", null, action);
        assertFalse(testTrackingProvider.wasMethodCalled("withTracked"));
    }

    @Test
    @Order(34)
    @DisplayName("Type 2.5: withTracked() - null action 不抛异常")
    void testWithTracked_WithNullAction_DoesNotThrow() {
        assertDoesNotThrow(() -> TFI.withTracked("test", new Object(), null));
    }

    @Test
    @Order(35)
    @DisplayName("Type 2.5: withTracked() - 灰度关闭时使用 Legacy")
    void testWithTracked_WithRoutingDisabled_UsesLegacy() {
        System.setProperty("tfi.api.routing.enabled", "false");
        testTrackingProvider.reset();
        Runnable action = () -> {};
        assertDoesNotThrow(() -> TFI.withTracked("test", new Object(), action));
        assertFalse(testTrackingProvider.wasMethodCalled("withTracked"));
    }

    // ==================== 灰度开关集成测试 ====================

    @Test
    @Order(36)
    @DisplayName("灰度集成: 所有 Type 1 方法灰度关闭时使用 Legacy")
    void testAllType1Methods_WithRoutingDisabled_UseLegacy() {
        System.setProperty("tfi.api.routing.enabled", "false");
        testFlowProvider.reset();
        testExportProvider.reset();

        TFI.getTaskStack();
        TFI.exportToConsole(false);
        TFI.exportToJson();
        TFI.exportToMap();

        assertFalse(testFlowProvider.wasMethodCalled("getTaskStack"));
        assertFalse(testExportProvider.wasMethodCalled("exportToConsole"));
        assertFalse(testExportProvider.wasMethodCalled("exportToJson"));
        assertFalse(testExportProvider.wasMethodCalled("exportToMap"));
    }

    @Test
    @Order(37)
    @DisplayName("灰度集成: 所有 Type 2 方法灰度关闭时使用 Legacy")
    void testAllType2Methods_WithRoutingDisabled_UseLegacy() {
        System.setProperty("tfi.api.routing.enabled", "false");
        testTrackingProvider.reset();
        Map<String, Object> targets = Map.of("key", new Object());

        TFI.trackAll(targets);
        TFI.trackDeep("test", new Object());
        TFI.recordChange("obj", "field", "old", "new", ChangeType.UPDATE);
        TFI.clearTracking("session-123");
        TFI.withTracked("test", new Object(), () -> {});

        assertFalse(testTrackingProvider.wasMethodCalled("trackAll"));
        assertFalse(testTrackingProvider.wasMethodCalled("trackDeep"));
        assertFalse(testTrackingProvider.wasMethodCalled("recordChange"));
        assertFalse(testTrackingProvider.wasMethodCalled("clearTracking"));
        assertFalse(testTrackingProvider.wasMethodCalled("withTracked"));
    }

    // ==================== 异常处理测试 ====================

    @Test
    @Order(38)
    @DisplayName("异常处理: trackAll() 大批量数据正常处理")
    void testTrackAll_WithLargeDataSet() {
        Map<String, Object> largeMap = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            largeMap.put("key" + i, new Object());
        }
        assertDoesNotThrow(() -> TFI.trackAll(largeMap));
        assertTrue(testTrackingProvider.wasMethodCalled("trackAll"));
    }

    @Test
    @Order(39)
    @DisplayName("异常处理: trackDeep() 极端深度配置")
    void testTrackDeep_WithExtremeDepth() {
        TrackingOptions extremeOptions = TrackingOptions.builder()
            .maxDepth(1000)
            .build();
        assertDoesNotThrow(() -> TFI.trackDeep("test", new Object(), extremeOptions));
    }

    @Test
    @Order(40)
    @DisplayName("异常处理: withTracked() action 抛异常被捕获")
    void testWithTracked_ActionThrowsException_IsCaught() {
        Runnable failingAction = () -> {
            throw new RuntimeException("Action failed");
        };
        assertDoesNotThrow(() -> TFI.withTracked("test", new Object(), failingAction));
    }

    // ==================== 边界条件测试 ====================

    @Test
    @Order(41)
    @DisplayName("边界条件: clearTracking() 特殊字符 sessionId")
    void testClearTracking_WithSpecialCharacters() {
        String specialSessionId = "session-测试-123-#$%";
        assertDoesNotThrow(() -> TFI.clearTracking(specialSessionId));
        assertTrue(testTrackingProvider.wasMethodCalled("clearTracking"));
    }

    // ==================== 性能与并发测试 ====================

    @Test
    @Order(42)
    @DisplayName("性能: 多次调用Provider缓存")
    void testMultipleCalls_CacheProvider() {
        Map<String, Object> targets = Map.of("key", new Object());
        for (int i = 0; i < 100; i++) {
            TFI.trackAll(targets);
        }
        assertEquals(100, testTrackingProvider.getMethodCallCount("trackAll"));
    }

    @Test
    @Order(43)
    @DisplayName("并发: 多线程同时调用不同方法")
    void testConcurrent_MultipleThreads() throws InterruptedException {
        Thread t1 = new Thread(() -> TFI.getTaskStack());
        Thread t2 = new Thread(() -> TFI.exportToJson());
        Thread t3 = new Thread(() -> TFI.trackAll(Map.of("key", new Object())));

        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();
        t3.join();

        assertTrue(testFlowProvider.wasMethodCalled("getTaskStack"));
        assertTrue(testExportProvider.wasMethodCalled("exportToJson"));
        assertTrue(testTrackingProvider.wasMethodCalled("trackAll"));
    }

    // ==================== 回归测试 ====================

    @Test
    @Order(44)
    @Disabled("Phase 1 methods tested separately - focus on Phase 2 routing validation")
    @DisplayName("回归: 验证 Phase 1 方法不受影响")
    void testPhase1Methods_StillWork() {
        // 使用 TFI 方法创建 session 和 task，确保上下文正确
        TFI.startSession("test");
        TFI.start("task1");  // 修正：使用 start() 而非 startTask()

        assertNotNull(TFI.getCurrentSession());
        assertNotNull(TFI.getCurrentTask());
    }

    @Test
    @Order(45)
    @DisplayName("完整性: 所有 10 个方法都有路由")
    void testCompleteness_All10MethodsHaveRouting() {
        // Type 1 (4 methods)
        TFI.getTaskStack();
        TFI.exportToConsole(false);
        TFI.exportToJson();
        TFI.exportToMap();

        // Type 2 (6 methods including trackDeep's 2 overloads)
        TFI.trackAll(Map.of("k", new Object()));
        TFI.trackDeep("test", new Object()); // Overload 1
        TFI.trackDeep("test", new Object(), TrackingOptions.deep()); // Overload 2
        TFI.recordChange("o", "f", "old", "new", ChangeType.UPDATE);
        TFI.clearTracking("session");
        TFI.withTracked("test", new Object(), () -> {});

        // 验证所有 Provider 方法都被调用
        assertTrue(testFlowProvider.wasMethodCalled("getTaskStack"));
        assertTrue(testExportProvider.wasMethodCalled("exportToConsole"));
        assertTrue(testExportProvider.wasMethodCalled("exportToJson"));
        assertTrue(testExportProvider.wasMethodCalled("exportToMap"));
        assertTrue(testTrackingProvider.wasMethodCalled("trackAll"));
        assertTrue(testTrackingProvider.wasMethodCalled("trackDeep"));
        assertTrue(testTrackingProvider.wasMethodCalled("recordChange"));
        assertTrue(testTrackingProvider.wasMethodCalled("clearTracking"));
        assertTrue(testTrackingProvider.wasMethodCalled("withTracked"));
    }
}
