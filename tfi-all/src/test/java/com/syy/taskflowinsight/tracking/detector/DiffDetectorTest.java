package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DiffDetector单元测试
 * 覆盖标量对比、空值处理、集合差异等核心功能
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-12
 */
class DiffDetectorTest {

    @BeforeEach
    void setUp() {
        // 禁用增强路径去重以确保测试预期的变更数量
        DiffDetector.setEnhancedDeduplicationEnabled(false);
    }

    @Test
    @DisplayName("测试空快照对比")
    void testDiffWithNullSnapshots() {
        // 两个都为null
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", null, null);
        assertNotNull(changes);
        assertTrue(changes.isEmpty());
        
        // before为null，after有值
        Map<String, Object> after = new HashMap<>();
        after.put("name", "John");
        after.put("age", 30);
        
        changes = DiffDetector.diff("TestObject", null, after);
        assertEquals(2, changes.size());
        
        // 验证按字段名字典序排序
        assertEquals("age", changes.get(0).getFieldName());
        assertEquals(ChangeType.CREATE, changes.get(0).getChangeType());
        assertEquals(30, changes.get(0).getNewValue());
        assertNull(changes.get(0).getOldValue());
        
        assertEquals("name", changes.get(1).getFieldName());
        assertEquals(ChangeType.CREATE, changes.get(1).getChangeType());
        assertEquals("John", changes.get(1).getNewValue());
    }
    
    @Test
    @DisplayName("测试标量类型CREATE场景")
    void testScalarCreate() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        after.put("stringField", "Hello");
        after.put("intField", 42);
        after.put("boolField", true);
        after.put("doubleField", 3.14);
        
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        
        assertEquals(4, changes.size());
        
        // 验证排序（按字段名字典序）
        assertEquals("boolField", changes.get(0).getFieldName());
        assertEquals("doubleField", changes.get(1).getFieldName());
        assertEquals("intField", changes.get(2).getFieldName());
        assertEquals("stringField", changes.get(3).getFieldName());
        
        // 验证所有都是CREATE
        for (ChangeRecord change : changes) {
            assertEquals(ChangeType.CREATE, change.getChangeType());
            assertNull(change.getOldValue());
            assertNotNull(change.getNewValue());
            assertNotNull(change.getValueRepr());
            assertNotNull(change.getValueKind());
        }
        
        // 验证valueKind分类
        assertEquals("BOOLEAN", changes.get(0).getValueKind());
        assertEquals("NUMBER", changes.get(1).getValueKind());
        assertEquals("NUMBER", changes.get(2).getValueKind());
        assertEquals("STRING", changes.get(3).getValueKind());
    }
    
    @Test
    @DisplayName("测试标量类型DELETE场景")
    void testScalarDelete() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        before.put("field1", "value1");
        before.put("field2", 100);
        before.put("field3", false);
        
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        
        assertEquals(3, changes.size());
        
        // 验证所有都是DELETE
        for (ChangeRecord change : changes) {
            assertEquals(ChangeType.DELETE, change.getChangeType());
            assertNotNull(change.getOldValue());
            assertNull(change.getNewValue());
            assertNull(change.getValueRepr()); // DELETE场景valueRepr为null
        }
    }
    
    @Test
    @DisplayName("测试标量类型UPDATE场景")
    void testScalarUpdate() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        before.put("name", "Alice");
        before.put("age", 25);
        before.put("active", true);
        
        after.put("name", "Bob");
        after.put("age", 30);
        after.put("active", false);
        
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        
        assertEquals(3, changes.size());
        
        // 验证所有都是UPDATE
        for (ChangeRecord change : changes) {
            assertEquals(ChangeType.UPDATE, change.getChangeType());
            assertNotNull(change.getOldValue());
            assertNotNull(change.getNewValue());
            assertNotNull(change.getValueRepr());
            assertNotEquals(change.getOldValue(), change.getNewValue());
        }
        
        // 验证具体值
        ChangeRecord nameChange = changes.stream()
            .filter(c -> "name".equals(c.getFieldName()))
            .findFirst().orElseThrow();
        assertEquals("Alice", nameChange.getOldValue());
        assertEquals("Bob", nameChange.getNewValue());
        assertEquals("STRING", nameChange.getValueKind());
    }
    
    @Test
    @DisplayName("测试无变化场景")
    void testNoChanges() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        before.put("field1", "same");
        before.put("field2", 42);
        before.put("field3", true);
        
        after.put("field1", "same");
        after.put("field2", 42);
        after.put("field3", true);
        
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        
        assertTrue(changes.isEmpty());
    }
    
    @Test
    @DisplayName("测试混合变更场景")
    void testMixedChanges() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        // 设置before
        before.put("unchanged", "same");
        before.put("updated", "old");
        before.put("deleted", "gone");
        
        // 设置after
        after.put("unchanged", "same");
        after.put("updated", "new");
        after.put("created", "fresh");
        
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        
        assertEquals(3, changes.size());
        
        // 验证CREATE
        ChangeRecord created = changes.stream()
            .filter(c -> "created".equals(c.getFieldName()))
            .findFirst().orElseThrow();
        assertEquals(ChangeType.CREATE, created.getChangeType());
        assertEquals("fresh", created.getNewValue());
        
        // 验证DELETE
        ChangeRecord deleted = changes.stream()
            .filter(c -> "deleted".equals(c.getFieldName()))
            .findFirst().orElseThrow();
        assertEquals(ChangeType.DELETE, deleted.getChangeType());
        assertEquals("gone", deleted.getOldValue());
        assertNull(deleted.getValueRepr());
        
        // 验证UPDATE
        ChangeRecord updated = changes.stream()
            .filter(c -> "updated".equals(c.getFieldName()))
            .findFirst().orElseThrow();
        assertEquals(ChangeType.UPDATE, updated.getChangeType());
        assertEquals("old", updated.getOldValue());
        assertEquals("new", updated.getNewValue());
    }
    
    @Test
    @DisplayName("测试Date类型归一化")
    void testDateNormalization() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        Date date1 = new Date(1000000000L);
        Date date2 = new Date(1000000000L); // 相同时间戳
        Date date3 = new Date(2000000000L); // 不同时间戳
        
        before.put("sameDate", date1);
        before.put("changedDate", date1);
        
        after.put("sameDate", date2);
        after.put("changedDate", date3);
        
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        
        // 相同时间戳的Date应该被识别为无变化
        assertEquals(1, changes.size());
        
        ChangeRecord dateChange = changes.get(0);
        assertEquals("changedDate", dateChange.getFieldName());
        assertEquals(ChangeType.UPDATE, dateChange.getChangeType());
        assertEquals("DATE", dateChange.getValueKind());
    }
    
    @Test
    @DisplayName("测试null值处理")
    void testNullValueHandling() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        // null -> value
        before.put("field1", null);
        after.put("field1", "value");
        
        // value -> null
        before.put("field2", "value");
        after.put("field2", null);
        
        // null -> null (无变化)
        before.put("field3", null);
        after.put("field3", null);
        
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        
        assertEquals(2, changes.size());
        
        // null -> value 应该是CREATE
        ChangeRecord field1Change = changes.stream()
            .filter(c -> "field1".equals(c.getFieldName()))
            .findFirst().orElseThrow();
        assertEquals(ChangeType.CREATE, field1Change.getChangeType());
        assertNull(field1Change.getOldValue());
        assertEquals("value", field1Change.getNewValue());
        
        // value -> null 应该是DELETE
        ChangeRecord field2Change = changes.stream()
            .filter(c -> "field2".equals(c.getFieldName()))
            .findFirst().orElseThrow();
        assertEquals(ChangeType.DELETE, field2Change.getChangeType());
        assertEquals("value", field2Change.getOldValue());
        assertNull(field2Change.getNewValue());
    }
    
    @Test
    @DisplayName("测试枚举类型处理")
    void testEnumHandling() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        before.put("changeType", ChangeType.CREATE);
        after.put("changeType", ChangeType.UPDATE);
        
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        
        assertEquals(1, changes.size());
        
        ChangeRecord enumChange = changes.get(0);
        assertEquals(ChangeType.UPDATE, enumChange.getChangeType());
        assertEquals(ChangeType.CREATE, enumChange.getOldValue());
        assertEquals(ChangeType.UPDATE, enumChange.getNewValue());
        assertEquals("ENUM", enumChange.getValueKind());
    }
    
    @Test
    @DisplayName("测试字段排序稳定性")
    void testFieldSortingStability() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        // 故意以非字典序添加字段
        after.put("zebra", "z");
        after.put("apple", "a");
        after.put("middle", "m");
        after.put("banana", "b");
        after.put("123", "number");
        after.put("_underscore", "u");
        after.put("CamelCase", "c");
        
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        
        assertEquals(7, changes.size());
        
        // 验证字典序排序
        assertEquals("123", changes.get(0).getFieldName());
        assertEquals("CamelCase", changes.get(1).getFieldName());
        assertEquals("_underscore", changes.get(2).getFieldName());
        assertEquals("apple", changes.get(3).getFieldName());
        assertEquals("banana", changes.get(4).getFieldName());
        assertEquals("middle", changes.get(5).getFieldName());
        assertEquals("zebra", changes.get(6).getFieldName());
    }
    
    @Test
    @DisplayName("测试特殊字符字段名")
    void testSpecialCharacterFieldNames() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        after.put("field.with.dots", "value1");
        after.put("field-with-dashes", "value2");
        after.put("field_with_underscores", "value3");
        after.put("field with spaces", "value4");
        after.put("field$with$dollar", "value5");
        after.put("中文字段", "value6");
        
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        
        assertEquals(6, changes.size());
        
        // 验证所有特殊字符字段都被正确处理
        for (ChangeRecord change : changes) {
            assertEquals(ChangeType.CREATE, change.getChangeType());
            assertNotNull(change.getFieldName());
            assertNotNull(change.getNewValue());
        }
    }
    
    @Test
    @DisplayName("测试空Map与空字段")
    void testEmptyMapsAndFields() {
        Map<String, Object> emptyMap = new HashMap<>();
        Map<String, Object> mapWithEmptyString = new HashMap<>();
        mapWithEmptyString.put("emptyField", "");
        
        // 空Map对比
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", emptyMap, emptyMap);
        assertTrue(changes.isEmpty());
        
        // 空Map vs 有空字符串值的Map
        changes = DiffDetector.diff("TestObject", emptyMap, mapWithEmptyString);
        assertEquals(1, changes.size());
        assertEquals(ChangeType.CREATE, changes.get(0).getChangeType());
        assertEquals("", changes.get(0).getNewValue());
    }
    
    @Test
    @DisplayName("测试集合类型基础支持")
    void testCollectionBasicSupport() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("a", "b", "d");
        Set<Integer> set1 = new HashSet<>(Arrays.asList(1, 2, 3));
        Set<Integer> set2 = new HashSet<>(Arrays.asList(1, 2, 4));
        
        before.put("list", list1);
        before.put("set", set1);
        
        after.put("list", list2);
        after.put("set", set2);
        
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        
        // 集合不相等应该检测为UPDATE
        assertEquals(2, changes.size());
        
        for (ChangeRecord change : changes) {
            assertEquals(ChangeType.UPDATE, change.getChangeType());
            assertNotNull(change.getOldValue());
            assertNotNull(change.getNewValue());
            // 验证valueKind正确识别为COLLECTION或MAP
            String fieldName = change.getFieldName();
            if ("list".equals(fieldName)) {
                assertEquals("COLLECTION", change.getValueKind());
            } else if ("set".equals(fieldName)) {
                assertEquals("COLLECTION", change.getValueKind());  // HashSet implements Collection
            }
        }
    }
}