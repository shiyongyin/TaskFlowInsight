package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ObjectSnapshotDeep 单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("ObjectSnapshotDeep 深度快照测试")
public class ObjectSnapshotDeepTest {
    
    private ObjectSnapshotDeep deepSnapshot;
    private SnapshotConfig config;
    
    @BeforeEach
    void setUp() {
        config = new SnapshotConfig();
        config.setEnableDeep(true);
        config.setMaxDepth(3);
        config.setTimeBudgetMs(100);
        deepSnapshot = new ObjectSnapshotDeep(config);
        
        // 重置指标
        ObjectSnapshotDeep.resetMetrics();
    }
    
    @Test
    @DisplayName("测试简单对象快照")
    void testSimpleObjectSnapshot() {
        SimpleObject obj = new SimpleObject("test", 42, true);
        
        Map<String, Object> result = deepSnapshot.captureDeep(
            obj, 3, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).containsEntry("name", "test");
        assertThat(result).containsEntry("value", 42);
        assertThat(result).containsEntry("active", true);
    }
    
    @Test
    @DisplayName("测试嵌套对象快照")
    void testNestedObjectSnapshot() {
        NestedObject root = new NestedObject("root");
        NestedObject child1 = new NestedObject("child1");
        NestedObject child2 = new NestedObject("child2");
        root.child = child1;
        child1.child = child2;
        
        Map<String, Object> result = deepSnapshot.captureDeep(
            root, 3, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).containsEntry("name", "root");
        assertThat(result).containsEntry("child.name", "child1");
        assertThat(result).containsEntry("child.child.name", "child2");
        assertThat(result).containsKey("child.child.child"); // null
    }
    
    @Test
    @DisplayName("测试深度限制")
    void testDepthLimit() {
        NestedObject root = createDeepNestedObject(5);
        
        Map<String, Object> result = deepSnapshot.captureDeep(
            root, 2, Collections.emptySet(), Collections.emptySet()
        );
        
        // 深度2应该只包含前两层
        assertThat(result).containsEntry("name", "level-0");
        assertThat(result).containsEntry("child.name", "level-1");
        // 第三层应该被限制
        assertThat(result).containsEntry("child.child", "<depth-limit>");
        
        // 验证指标
        Map<String, Long> metrics = ObjectSnapshotDeep.getMetrics();
        assertThat(metrics.get("depth.limit.reached")).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("测试循环引用检测")
    void testCycleDetection() {
        CyclicObject obj1 = new CyclicObject("obj1");
        CyclicObject obj2 = new CyclicObject("obj2");
        obj1.reference = obj2;
        obj2.reference = obj1;
        
        Map<String, Object> result = deepSnapshot.captureDeep(
            obj1, 5, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).containsEntry("name", "obj1");
        assertThat(result).containsEntry("reference.name", "obj2");
        // 循环引用应该被检测到
        assertThat(result).containsEntry("reference.reference", "<circular-reference>");
        
        // 验证指标
        Map<String, Long> metrics = ObjectSnapshotDeep.getMetrics();
        assertThat(metrics.get("cycle.detected")).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("测试集合处理")
    void testCollectionHandling() {
        CollectionObject obj = new CollectionObject();
        obj.list = Arrays.asList("a", "b", "c");
        obj.set = new HashSet<>(Arrays.asList(1, 2, 3));
        
        Map<String, Object> result = deepSnapshot.captureDeep(
            obj, 3, Collections.emptySet(), Collections.emptySet()
        );
        
        // List应该按索引展开
        assertThat(result).containsEntry("list[0]", "a");
        assertThat(result).containsEntry("list[1]", "b");
        assertThat(result).containsEntry("list[2]", "c");
        
        // Set应该被展开（但顺序不确定）
        assertThat(result).containsKeys("set[0]", "set[1]", "set[2]");
    }
    
    @Test
    @DisplayName("测试大集合摘要")
    void testLargeCollectionSummary() {
        CollectionObject obj = new CollectionObject();
        // 创建大集合（超过阈值）
        List<Integer> largeList = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            largeList.add(i);
        }
        obj.list = largeList;
        
        Map<String, Object> result = deepSnapshot.captureDeep(
            obj, 3, Collections.emptySet(), Collections.emptySet()
        );
        
        // 大集合应该被摘要化
        assertThat(result.get("list")).isNotNull();
        // SummaryInfo对象应该包含摘要信息
    }
    
    @Test
    @DisplayName("测试Map处理")
    void testMapHandling() {
        MapObject obj = new MapObject();
        obj.map = new HashMap<>();
        obj.map.put("key1", "value1");
        obj.map.put("key2", 42);
        obj.map.put("key3", null);
        
        Map<String, Object> result = deepSnapshot.captureDeep(
            obj, 3, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).containsEntry("map['key1']", "value1");
        assertThat(result).containsEntry("map['key2']", 42);
        assertThat(result).containsEntry("map['key3']", null);
    }
    
    @Test
    @DisplayName("测试路径排除")
    void testPathExclusion() {
        SensitiveObject obj = new SensitiveObject();
        obj.username = "user123";
        obj.password = "secret123";
        obj.token = "token456";
        obj.data = "public data";
        
        Set<String> excludePatterns = new HashSet<>(Arrays.asList(
            "*.password",
            "*.token"
        ));
        
        Map<String, Object> result = deepSnapshot.captureDeep(
            obj, 3, Collections.emptySet(), excludePatterns
        );
        
        // 普通字段应该被包含
        assertThat(result).containsEntry("username", "user123");
        assertThat(result).containsEntry("data", "public data");
        // 敏感字段应该被排除
        assertThat(result).doesNotContainKeys("password", "token");
        
        // 验证指标
        Map<String, Long> metrics = ObjectSnapshotDeep.getMetrics();
        assertThat(metrics.get("path.excluded")).isEqualTo(2);
    }
    
    @Test
    @DisplayName("测试包含字段过滤")
    void testIncludeFields() {
        SimpleObject obj = new SimpleObject("test", 42, true);
        
        Set<String> includeFields = new HashSet<>(Arrays.asList("name", "value"));
        
        Map<String, Object> result = deepSnapshot.captureDeep(
            obj, 3, includeFields, Collections.emptySet()
        );
        
        // 只包含指定字段
        assertThat(result).containsEntry("name", "test");
        assertThat(result).containsEntry("value", 42);
        assertThat(result).doesNotContainKey("active");
    }
    
    @Test
    @DisplayName("测试数组处理")
    void testArrayHandling() {
        ArrayObject obj = new ArrayObject();
        obj.intArray = new int[]{1, 2, 3};
        obj.stringArray = new String[]{"a", "b", "c"};
        obj.objectArray = new SimpleObject[]{
            new SimpleObject("obj1", 1, true),
            new SimpleObject("obj2", 2, false)
        };
        
        Map<String, Object> result = deepSnapshot.captureDeep(
            obj, 3, Collections.emptySet(), Collections.emptySet()
        );
        
        // 基本类型数组应该被格式化
        assertThat(result.get("intArray")).asString().contains("int[1, 2, 3]");
        
        // 对象数组应该被展开
        assertThat(result).containsEntry("stringArray[0]", "a");
        assertThat(result).containsEntry("stringArray[1]", "b");
        assertThat(result).containsEntry("objectArray[0].name", "obj1");
        assertThat(result).containsEntry("objectArray[1].name", "obj2");
    }
    
    @Test
    @DisplayName("测试null值处理")
    void testNullHandling() {
        SimpleObject obj = new SimpleObject(null, null, null);
        
        Map<String, Object> result = deepSnapshot.captureDeep(
            obj, 3, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result).containsEntry("name", null);
        assertThat(result).containsEntry("value", null);
        assertThat(result).containsEntry("active", null);
    }
    
    @Test
    @DisplayName("测试Date深拷贝")
    void testDateDeepCopy() {
        Date originalDate = new Date();
        DateObject obj = new DateObject();
        obj.date = originalDate;
        
        Map<String, Object> result = deepSnapshot.captureDeep(
            obj, 3, Collections.emptySet(), Collections.emptySet()
        );
        
        assertThat(result.get("date")).isInstanceOf(Date.class);
        Date capturedDate = (Date) result.get("date");
        assertThat(capturedDate).isEqualTo(originalDate);
        assertThat(capturedDate).isNotSameAs(originalDate); // 应该是深拷贝
    }
    
    // ========== 测试用内部类 ==========
    
    static class SimpleObject {
        String name;
        Integer value;
        Boolean active;
        
        SimpleObject(String name, Integer value, Boolean active) {
            this.name = name;
            this.value = value;
            this.active = active;
        }
    }
    
    static class NestedObject {
        String name;
        NestedObject child;
        
        NestedObject(String name) {
            this.name = name;
        }
    }
    
    static class CyclicObject {
        String name;
        CyclicObject reference;
        
        CyclicObject(String name) {
            this.name = name;
        }
    }
    
    static class CollectionObject {
        List<?> list;
        Set<?> set;
    }
    
    static class MapObject {
        Map<String, Object> map;
    }
    
    static class SensitiveObject {
        String username;
        String password;
        String token;
        String data;
    }
    
    static class ArrayObject {
        int[] intArray;
        String[] stringArray;
        SimpleObject[] objectArray;
    }
    
    static class DateObject {
        Date date;
    }
    
    // ========== 辅助方法 ==========
    
    private NestedObject createDeepNestedObject(int depth) {
        NestedObject root = new NestedObject("level-0");
        NestedObject current = root;
        
        for (int i = 1; i < depth; i++) {
            NestedObject child = new NestedObject("level-" + i);
            current.child = child;
            current = child;
        }
        
        return root;
    }
}