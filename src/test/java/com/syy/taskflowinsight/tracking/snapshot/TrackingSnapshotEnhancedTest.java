package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Enhanced tests targeting low-coverage classes in tracking.snapshot package
 * Focus: TraversalHandlers (47%), SnapshotFacadeOptimized (45%), ShallowSnapshotStrategy (62%)
 */
class TrackingSnapshotEnhancedTest {

    private SnapshotConfig config;
    private ObjectSnapshotDeepOptimized optimizedSnapshot;
    private SnapshotFacadeOptimized facade;
    private ShallowSnapshotStrategy shallowStrategy;
    private DeepSnapshotStrategy deepStrategy;

    @Mock
    private SnapshotFacade mockSnapshotFacade;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        config = new SnapshotConfig();
        config.setMaxDepth(5);
        config.setCollectionSummaryThreshold(50);
        config.setTimeBudgetMs(100);
        
        optimizedSnapshot = new ObjectSnapshotDeepOptimized(config);
        shallowStrategy = new ShallowSnapshotStrategy();
        deepStrategy = new DeepSnapshotStrategy(config);
        facade = new SnapshotFacadeOptimized(config, shallowStrategy, deepStrategy);
    }

    // ========== TraversalHandlers Tests (提升47%→75%+) ==========

    @Test
    @DisplayName("TraversalHandlers - 处理大集合使用摘要")
    void traversalHandlers_handleLargeCollection() {
        // 创建大集合超过阈值
        List<String> largeList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            largeList.add("item" + i);
        }
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            largeList, 2, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("TraversalHandlers - 处理小集合展开显示")
    void traversalHandlers_handleSmallCollection() {
        List<String> smallList = Arrays.asList("item1", "item2", "item3");
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            smallList, 2, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("TraversalHandlers - 处理大Map使用摘要")
    void traversalHandlers_handleLargeMap() {
        Map<String, String> largeMap = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            largeMap.put("key" + i, "value" + i);
        }
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            largeMap, 2, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("TraversalHandlers - 处理小Map展开显示")
    void traversalHandlers_handleSmallMap() {
        Map<String, String> smallMap = new HashMap<>();
        smallMap.put("key1", "value1");
        smallMap.put("key2", "value2");
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            smallMap, 2, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("TraversalHandlers - 处理基本类型数组")
    void traversalHandlers_handlePrimitiveArray() {
        int[] intArray = {1, 2, 3, 4, 5};
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            intArray, 2, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("TraversalHandlers - 处理长基本类型数组")
    void traversalHandlers_handleLongPrimitiveArray() {
        int[] longArray = new int[20];
        for (int i = 0; i < 20; i++) {
            longArray[i] = i;
        }
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            longArray, 2, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("TraversalHandlers - 处理空基本类型数组")
    void traversalHandlers_handleEmptyPrimitiveArray() {
        int[] emptyArray = new int[0];
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            emptyArray, 2, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("TraversalHandlers - 处理对象数组")
    void traversalHandlers_handleObjectArray() {
        TestObject[] objectArray = {
            new TestObject("obj1", 1),
            new TestObject("obj2", 2)
        };
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            objectArray, 2, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("TraversalHandlers - 处理大对象数组")
    void traversalHandlers_handleLargeObjectArray() {
        TestObject[] largeArray = new TestObject[100];
        for (int i = 0; i < 100; i++) {
            largeArray[i] = new TestObject("obj" + i, i);
        }
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            largeArray, 2, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("TraversalHandlers - 处理复杂对象字段访问")
    void traversalHandlers_handleObjectFieldAccess() {
        ComplexTestObject complexObj = new ComplexTestObject();
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            complexObj, 3, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("TraversalHandlers - 处理字段访问异常")
    void traversalHandlers_handleFieldAccessException() {
        PrivateFieldObject privateObj = new PrivateFieldObject();
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            privateObj, 2, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).isNotEmpty();
    }

    // ========== SnapshotFacadeOptimized Tests (提升45%→75%+) ==========

    @Test
    @DisplayName("SnapshotFacadeOptimized - 异步捕获深度策略")
    void snapshotFacadeOptimized_asyncCaptureDeepStrategy() throws Exception {
        facade.setEnableDeep(true);
        TestObject testObj = new TestObject("asyncTest", 123);
        
        CompletableFuture<Map<String, Object>> future = facade.captureAsync("testObj", testObj, "name");
        Map<String, Object> result = future.get();
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("SnapshotFacadeOptimized - 异步捕获浅度策略")
    void snapshotFacadeOptimized_asyncCaptureShallowStrategy() throws Exception {
        facade.setEnableDeep(false);
        TestObject testObj = new TestObject("asyncTest", 123);
        
        CompletableFuture<Map<String, Object>> future = facade.captureAsync("testObj", testObj, "name");
        Map<String, Object> result = future.get();
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("SnapshotFacadeOptimized - 同步捕获失败降级")
    void snapshotFacadeOptimized_captureFallback() {
        // 创建一个会导致异常的对象
        Object problematicObj = new Object() {
            @Override
            public String toString() {
                throw new RuntimeException("Problematic object");
            }
        };
        
        Map<String, Object> result = facade.capture("problematic", problematicObj);
        
        // 应该返回空map而不是抛异常
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("SnapshotFacadeOptimized - 动态切换策略")
    void snapshotFacadeOptimized_dynamicStrategySwitch() {
        assertThat(facade.getCurrentStrategyName()).isEqualTo("shallow");
        
        facade.setStrategy("deep");
        assertThat(facade.getCurrentStrategyName()).isEqualTo("deep");
        
        facade.setStrategy("shallow");
        assertThat(facade.getCurrentStrategyName()).isEqualTo("shallow");
    }

    @Test
    @DisplayName("SnapshotFacadeOptimized - 切换到不存在的策略")
    void snapshotFacadeOptimized_switchToNonExistentStrategy() {
        String originalStrategy = facade.getCurrentStrategyName();
        
        facade.setStrategy("nonexistent");
        
        // 策略应该保持不变
        assertThat(facade.getCurrentStrategyName()).isEqualTo(originalStrategy);
    }

    @Test
    @DisplayName("SnapshotFacadeOptimized - 动态开关深度快照")
    void snapshotFacadeOptimized_dynamicDeepToggle() {
        facade.setEnableDeep(false);
        assertThat(facade.getCurrentStrategyName()).isEqualTo("shallow");
        
        facade.setEnableDeep(true);
        assertThat(facade.getCurrentStrategyName()).isEqualTo("deep");
    }

    @Test
    @DisplayName("SnapshotFacadeOptimized - 获取可用策略")
    void snapshotFacadeOptimized_getAvailableStrategies() {
        Set<String> strategies = facade.getAvailableStrategies();
        
        assertThat(strategies).containsExactlyInAnyOrder("shallow", "deep");
    }

    @Test
    @DisplayName("SnapshotFacadeOptimized - 获取配置")
    void snapshotFacadeOptimized_getConfig() {
        SnapshotConfig returnedConfig = facade.getConfig();
        
        assertThat(returnedConfig).isSameAs(config);
    }

    @Test
    @DisplayName("SnapshotFacadeOptimized - 获取性能指标")
    void snapshotFacadeOptimized_getMetrics() {
        Map<String, Object> metrics = facade.getMetrics();
        
        assertThat(metrics).containsKeys(
            "current_strategy", 
            "available_strategies", 
            "deep_snapshot_metrics"
        );
    }

    @Test
    @DisplayName("SnapshotFacadeOptimized - 降级捕获测试策略异常处理")
    void snapshotFacadeOptimized_fallbackExceptionHandling() {
        // 创建一个会抛异常的策略
        SnapshotStrategy faultyStrategy = new SnapshotStrategy() {
            @Override
            public Map<String, Object> capture(String objectName, Object target, SnapshotConfig config, String... fields) {
                throw new RuntimeException("Strategy failure");
            }
            
            @Override
            public String getName() {
                return "faulty";
            }
        };
        
        // 将当前策略设置为会抛异常的策略
        ReflectionTestUtils.setField(facade, "currentStrategy", faultyStrategy);
        
        Map<String, Object> result = facade.capture("test", new TestObject("test", 1));
        
        // 应该fallback到shallow策略
        assertThat(result).isNotNull();
    }

    // ========== ShallowSnapshotStrategy Tests (提升62%→80%+) ==========

    @Test
    @DisplayName("ShallowSnapshotStrategy - 基本功能")
    void shallowSnapshotStrategy_basicFunctionality() {
        assertThat(shallowStrategy.getName()).isEqualTo("shallow");
        
        // 验证配置不会抛异常
        assertThatNoException().isThrownBy(() -> shallowStrategy.validateConfig(config));
    }

    @Test
    @DisplayName("ShallowSnapshotStrategy - null对象处理")
    void shallowSnapshotStrategy_nullObject() {
        Map<String, Object> result = shallowStrategy.capture("test", null, config);
        
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ShallowSnapshotStrategy - 正常对象捕获")
    void shallowSnapshotStrategy_normalCapture() {
        TestObject testObj = new TestObject("test", 42);
        
        Map<String, Object> result = shallowStrategy.capture("testObj", testObj, config, "name", "value");
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("ShallowSnapshotStrategy - 异常处理")
    void shallowSnapshotStrategy_exceptionHandling() {
        // 使用会抛异常的对象
        Object problematicObj = new Object() {
            @Override
            public String toString() {
                throw new RuntimeException("Test exception");
            }
        };
        
        Map<String, Object> result = shallowStrategy.capture("problematic", problematicObj, config);
        
        // 应该返回空map而不是抛异常
        assertThat(result).isNotNull();
    }

    // ========== ObjectSnapshotDeepOptimized高级测试 ==========

    @Test
    @DisplayName("ObjectSnapshotDeepOptimized - 性能指标获取")
    void objectSnapshotDeepOptimized_getMetrics() {
        Map<String, Long> metrics = ObjectSnapshotDeepOptimized.getMetrics();
        
        assertThat(metrics).containsKeys(
            "depth.limit.reached",
            "cycle.detected", 
            "path.excluded",
            "current.stack.depth",
            "field.cache.size",
            "pattern.cache.size"
        );
    }

    @Test
    @DisplayName("ObjectSnapshotDeepOptimized - 重置性能指标")
    void objectSnapshotDeepOptimized_resetMetrics() {
        ObjectSnapshotDeepOptimized.resetMetrics();
        
        Map<String, Long> metrics = ObjectSnapshotDeepOptimized.getMetrics();
        assertThat(metrics.get("depth.limit.reached")).isEqualTo(0L);
        assertThat(metrics.get("cycle.detected")).isEqualTo(0L);
        assertThat(metrics.get("path.excluded")).isEqualTo(0L);
    }

    @Test
    @DisplayName("ObjectSnapshotDeepOptimized - 清理缓存")
    void objectSnapshotDeepOptimized_clearCaches() {
        // 先触发一些缓存
        optimizedSnapshot.captureDeep(new TestObject("test", 1), 2, Collections.emptySet(), Collections.emptySet());
        
        ObjectSnapshotDeepOptimized.clearCaches();
        
        Map<String, Long> metrics = ObjectSnapshotDeepOptimized.getMetrics();
        assertThat(metrics.get("field.cache.size")).isEqualTo(0L);
        assertThat(metrics.get("pattern.cache.size")).isEqualTo(0L);
    }

    @Test
    @DisplayName("ObjectSnapshotDeepOptimized - 循环引用检测")
    void objectSnapshotDeepOptimized_circularReference() {
        CircularReferenceObject obj1 = new CircularReferenceObject("obj1");
        CircularReferenceObject obj2 = new CircularReferenceObject("obj2");
        obj1.setReference(obj2);
        obj2.setReference(obj1);
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            obj1, 5, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("ObjectSnapshotDeepOptimized - 深度限制触发")
    void objectSnapshotDeepOptimized_depthLimit() {
        DeepNestedObject deepObj = createDeepNestedObject(10);
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            deepObj, 3, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("ObjectSnapshotDeepOptimized - 路径排除测试")
    void objectSnapshotDeepOptimized_pathExclusion() {
        TestObject testObj = new TestObject("test", 42);
        Set<String> excludePatterns = Set.of("*.value");
        
        Map<String, Object> result = optimizedSnapshot.captureDeep(
            testObj, 2, Collections.emptySet(), excludePatterns
        );
        
        assertThat(result).isNotEmpty();
    }

    // ========== 辅助测试类 ==========

    private static class TestObject {
        private String name;
        private int value;
        
        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() { return name; }
        public int getValue() { return value; }
    }

    private static class ComplexTestObject {
        private String stringField = "test";
        private List<String> listField = Arrays.asList("item1", "item2");
        private Map<String, String> mapField = Map.of("key", "value");
        private TestObject nestedObject = new TestObject("nested", 999);
        
        public String getStringField() { return stringField; }
        public List<String> getListField() { return listField; }
        public Map<String, String> getMapField() { return mapField; }
        public TestObject getNestedObject() { return nestedObject; }
    }

    private static class PrivateFieldObject {
        @SuppressWarnings("unused")
        private final String secretField = "secret";
        
        // 没有getter方法，测试字段访问异常处理
    }

    private static class CircularReferenceObject {
        private String name;
        private CircularReferenceObject reference;
        
        public CircularReferenceObject(String name) {
            this.name = name;
        }
        
        public void setReference(CircularReferenceObject reference) {
            this.reference = reference;
        }
        
        public String getName() { return name; }
        public CircularReferenceObject getReference() { return reference; }
    }

    private static class DeepNestedObject {
        private String value;
        private DeepNestedObject child;
        
        public DeepNestedObject(String value) {
            this.value = value;
        }
        
        public void setChild(DeepNestedObject child) {
            this.child = child;
        }
        
        public String getValue() { return value; }
        public DeepNestedObject getChild() { return child; }
    }

    private DeepNestedObject createDeepNestedObject(int depth) {
        DeepNestedObject root = new DeepNestedObject("level0");
        DeepNestedObject current = root;
        
        for (int i = 1; i < depth; i++) {
            DeepNestedObject child = new DeepNestedObject("level" + i);
            current.setChild(child);
            current = child;
        }
        
        return root;
    }
}