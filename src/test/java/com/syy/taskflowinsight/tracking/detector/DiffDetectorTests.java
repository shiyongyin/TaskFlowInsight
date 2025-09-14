package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DiffDetector 单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
class DiffDetectorTests {
    
    @Test
    void testDetectCreate() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("name", null);
        
        Map<String, Object> after = new HashMap<>();
        after.put("name", "John");
        
        // When
        List<ChangeRecord> changes = DiffDetector.diff("User", before, after);
        
        // Then
        assertEquals(1, changes.size());
        ChangeRecord change = changes.get(0);
        assertEquals("User", change.getObjectName());
        assertEquals("name", change.getFieldName());
        assertNull(change.getOldValue());
        assertEquals("John", change.getNewValue());
        assertEquals(ChangeType.CREATE, change.getChangeType());
    }
    
    @Test
    void testDetectUpdate() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("status", "PENDING");
        
        Map<String, Object> after = new HashMap<>();
        after.put("status", "PAID");
        
        // When
        List<ChangeRecord> changes = DiffDetector.diff("Order", before, after);
        
        // Then
        assertEquals(1, changes.size());
        ChangeRecord change = changes.get(0);
        assertEquals("Order", change.getObjectName());
        assertEquals("status", change.getFieldName());
        assertEquals("PENDING", change.getOldValue());
        assertEquals("PAID", change.getNewValue());
        assertEquals(ChangeType.UPDATE, change.getChangeType());
    }
    
    @Test
    void testDetectDelete() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("description", "Some text");
        
        Map<String, Object> after = new HashMap<>();
        after.put("description", null);
        
        // When
        List<ChangeRecord> changes = DiffDetector.diff("Product", before, after);
        
        // Then
        assertEquals(1, changes.size());
        ChangeRecord change = changes.get(0);
        assertEquals("Product", change.getObjectName());
        assertEquals("description", change.getFieldName());
        assertEquals("Some text", change.getOldValue());
        assertNull(change.getNewValue());
        assertEquals(ChangeType.DELETE, change.getChangeType());
    }
    
    @Test
    void testNoChanges() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("value", 100);
        
        Map<String, Object> after = new HashMap<>();
        after.put("value", 100);
        
        // When
        List<ChangeRecord> changes = DiffDetector.diff("Config", before, after);
        
        // Then
        assertTrue(changes.isEmpty());
    }
    
    @Test
    void testMultipleChanges() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("name", "Alice");
        before.put("age", 25);
        before.put("active", true);
        
        Map<String, Object> after = new HashMap<>();
        after.put("name", "Alice");  // 无变化
        after.put("age", 26);        // UPDATE
        after.put("active", false);   // UPDATE
        after.put("role", "Admin");   // CREATE
        
        // When
        List<ChangeRecord> changes = DiffDetector.diff("User", before, after);
        
        // Then
        assertEquals(3, changes.size());
        
        // 验证按字段名字典序排序
        assertEquals("active", changes.get(0).getFieldName());
        assertEquals("age", changes.get(1).getFieldName());
        assertEquals("role", changes.get(2).getFieldName());
    }
    
    @Test
    void testDateComparison() {
        // Given
        Date date1 = new Date(1000000);
        Date date2 = new Date(2000000);
        
        Map<String, Object> before = new HashMap<>();
        before.put("createdAt", date1);
        
        Map<String, Object> after = new HashMap<>();
        after.put("createdAt", date2);
        
        // When
        List<ChangeRecord> changes = DiffDetector.diff("Event", before, after);
        
        // Then
        assertEquals(1, changes.size());
        ChangeRecord change = changes.get(0);
        assertEquals(ChangeType.UPDATE, change.getChangeType());
        assertEquals(date1, change.getOldValue());
        assertEquals(date2, change.getNewValue());
    }
    
    @Test
    void testNullSnapshots() {
        // When
        List<ChangeRecord> changes1 = DiffDetector.diff("Test", null, null);
        List<ChangeRecord> changes2 = DiffDetector.diff("Test", null, Map.of("field", "value"));
        List<ChangeRecord> changes3 = DiffDetector.diff("Test", Map.of("field", "value"), null);
        
        // Then
        assertTrue(changes1.isEmpty());
        assertEquals(1, changes2.size());
        assertEquals(ChangeType.CREATE, changes2.get(0).getChangeType());
        assertEquals(1, changes3.size());
        assertEquals(ChangeType.DELETE, changes3.get(0).getChangeType());
    }
    
    @Test
    void testValueKindDetection() {
        // Given
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        after.put("string", "text");
        after.put("number", 42);
        after.put("boolean", true);
        after.put("date", new Date());
        
        // When
        List<ChangeRecord> changes = DiffDetector.diff("Test", before, after);
        
        // Then
        assertEquals(4, changes.size());
        
        ChangeRecord stringChange = changes.stream()
            .filter(c -> c.getFieldName().equals("string"))
            .findFirst().orElse(null);
        assertNotNull(stringChange);
        assertEquals("STRING", stringChange.getValueKind());
        
        ChangeRecord numberChange = changes.stream()
            .filter(c -> c.getFieldName().equals("number"))
            .findFirst().orElse(null);
        assertNotNull(numberChange);
        assertEquals("NUMBER", numberChange.getValueKind());
        
        ChangeRecord booleanChange = changes.stream()
            .filter(c -> c.getFieldName().equals("boolean"))
            .findFirst().orElse(null);
        assertNotNull(booleanChange);
        assertEquals("BOOLEAN", booleanChange.getValueKind());
        
        ChangeRecord dateChange = changes.stream()
            .filter(c -> c.getFieldName().equals("date"))
            .findFirst().orElse(null);
        assertNotNull(dateChange);
        assertEquals("DATE", dateChange.getValueKind());
    }
    
    // ===== TASK-260: Comprehensive Test Matrix =====
    
    @Test
    void testStringMatrix() {
        // null → value (CREATE)
        assertChangeMatrix("strField", null, "hello", ChangeType.CREATE, "STRING", "hello");
        
        // value → null (DELETE)
        assertChangeMatrix("strField", "hello", null, ChangeType.DELETE, "STRING", null);
        
        // value → same value (NO CHANGE)
        assertNoChange("strField", "hello", "hello");
        
        // value → different value (UPDATE)
        assertChangeMatrix("strField", "hello", "world", ChangeType.UPDATE, "STRING", "world");
        
        // type change: number → string (UPDATE)
        assertChangeMatrix("mixField", 123, "123", ChangeType.UPDATE, "STRING", "123");
    }
    
    @Test
    void testNumberMatrix() {
        // null → value (CREATE)
        assertChangeMatrix("numField", null, 42, ChangeType.CREATE, "NUMBER", "42");
        assertChangeMatrix("doubleField", null, 3.14, ChangeType.CREATE, "NUMBER", "3.14");
        
        // value → null (DELETE)
        assertChangeMatrix("numField", 42, null, ChangeType.DELETE, "NUMBER", null);
        
        // value → same value (NO CHANGE)
        assertNoChange("numField", 42, 42);
        assertNoChange("doubleField", 3.14, 3.14);
        
        // value → different value (UPDATE)
        assertChangeMatrix("numField", 42, 100, ChangeType.UPDATE, "NUMBER", "100");
        assertChangeMatrix("doubleField", 3.14, 2.71, ChangeType.UPDATE, "NUMBER", "2.71");
        
        // type change: string → number (UPDATE)
        assertChangeMatrix("mixField", "42", 42, ChangeType.UPDATE, "NUMBER", "42");
    }
    
    @Test
    void testBooleanMatrix() {
        // null → value (CREATE)
        assertChangeMatrix("boolField", null, true, ChangeType.CREATE, "BOOLEAN", "true");
        assertChangeMatrix("boolField2", null, false, ChangeType.CREATE, "BOOLEAN", "false");
        
        // value → null (DELETE)
        assertChangeMatrix("boolField", true, null, ChangeType.DELETE, "BOOLEAN", null);
        
        // value → same value (NO CHANGE)
        assertNoChange("boolField", true, true);
        assertNoChange("boolField", false, false);
        
        // value → different value (UPDATE)
        assertChangeMatrix("boolField", true, false, ChangeType.UPDATE, "BOOLEAN", "false");
        assertChangeMatrix("boolField", false, true, ChangeType.UPDATE, "BOOLEAN", "true");
        
        // type change: string → boolean (UPDATE)
        assertChangeMatrix("mixField", "true", true, ChangeType.UPDATE, "BOOLEAN", "true");
    }
    
    @Test
    void testDateMatrix() {
        Date date1 = new Date(1000000);
        Date date2 = new Date(2000000);
        
        // null → value (CREATE)
        assertChangeMatrix("dateField", null, date1, ChangeType.CREATE, "DATE", String.valueOf(date1.getTime()));
        
        // value → null (DELETE)
        assertChangeMatrix("dateField", date1, null, ChangeType.DELETE, "DATE", null);
        
        // value → same value (NO CHANGE)
        Date sameDateCopy = new Date(1000000);
        assertNoChange("dateField", date1, sameDateCopy);
        
        // value → different value (UPDATE)
        assertChangeMatrix("dateField", date1, date2, ChangeType.UPDATE, "DATE", String.valueOf(date2.getTime()));
        
        // type change: long → Date (UPDATE) - 注意：long到Date转换会被normalize为long比较，可能无变化
        // 修改为不同的long值确保变化
        assertChangeMatrix("mixField", 999999L, date1, ChangeType.UPDATE, "DATE", String.valueOf(date1.getTime()));
    }
    
    @Test
    void testComplexTypeChanges() {
        // String to Number
        assertChangeMatrix("field", "123", 123, ChangeType.UPDATE, "NUMBER", "123");
        
        // Number to String  
        assertChangeMatrix("field", 456, "456", ChangeType.UPDATE, "STRING", "456");
        
        // Boolean to String
        assertChangeMatrix("field", true, "true", ChangeType.UPDATE, "STRING", "true");
        
        // String to Boolean
        assertChangeMatrix("field", "false", false, ChangeType.UPDATE, "BOOLEAN", "false");
        
        // Date to Long - 注意：normalize后都是long，可能无变化，使用不同值
        Date date = new Date(3000000);
        assertChangeMatrix("field", date, 3000001L, ChangeType.UPDATE, "NUMBER", "3000001");
        
        // Long to Date - 同样使用不同值确保变化
        assertChangeMatrix("field", 4000000L, new Date(4000001), ChangeType.UPDATE, "DATE", "4000001");
    }
    
    @Test
    void testValueReprEscapeAndTruncate() {
        // Test escape characters
        String withNewlines = "line1\nline2\rline3\tTabbed";
        assertChangeMatrix("field", null, withNewlines, ChangeType.CREATE, "STRING", "line1\\nline2\\rline3\\tTabbed");
        
        // Test very long string truncation (>8192 chars)
        String longString = "a".repeat(10000);
        Map<String, Object> before = new HashMap<>();
        before.put("field", null);
        Map<String, Object> after = new HashMap<>();
        after.put("field", longString);
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size());
        ChangeRecord change = changes.get(0);
        assertEquals(ChangeType.CREATE, change.getChangeType());
        String repr = change.getValueRepr();
        assertNotNull(repr);
        assertEquals(8192, repr.length(), "repr length must be 8192 including suffix");
        assertTrue(repr.endsWith("... (truncated)"), "repr must end with truncation suffix");
    }
    
    @Test
    void testSortingStability() {
        // Given multiple fields with changes
        Map<String, Object> before = new HashMap<>();
        before.put("zebra", "old");
        before.put("apple", 1);
        before.put("middle", true);
        
        Map<String, Object> after = new HashMap<>();
        after.put("zebra", "new");
        after.put("apple", 2);
        after.put("middle", false);
        after.put("banana", "created"); // new field
        
        // When
        List<ChangeRecord> changes = DiffDetector.diff("Test", before, after);
        
        // Then - verify alphabetical sorting
        assertEquals(4, changes.size());
        assertEquals("apple", changes.get(0).getFieldName());
        assertEquals("banana", changes.get(1).getFieldName());
        assertEquals("middle", changes.get(2).getFieldName());
        assertEquals("zebra", changes.get(3).getFieldName());
    }
    
    // Helper methods for matrix testing
    private void assertChangeMatrix(String fieldName, Object oldValue, Object newValue, 
                                   ChangeType expectedType, String expectedKind, String expectedRepr) {
        Map<String, Object> before = new HashMap<>();
        before.put(fieldName, oldValue);
        
        Map<String, Object> after = new HashMap<>();
        after.put(fieldName, newValue);
        
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        
        assertEquals(1, changes.size(), "Should detect exactly one change");
        ChangeRecord change = changes.get(0);
        
        assertEquals("TestObject", change.getObjectName());
        assertEquals(fieldName, change.getFieldName());
        assertEquals(oldValue, change.getOldValue());
        assertEquals(newValue, change.getNewValue());
        assertEquals(expectedType, change.getChangeType());
        assertEquals(expectedKind, change.getValueKind());
        assertEquals(expectedRepr, change.getValueRepr());
    }
    
    private void assertNoChange(String fieldName, Object value1, Object value2) {
        Map<String, Object> before = new HashMap<>();
        before.put(fieldName, value1);
        
        Map<String, Object> after = new HashMap<>();
        after.put(fieldName, value2);
        
        List<ChangeRecord> changes = DiffDetector.diff("TestObject", before, after);
        assertTrue(changes.isEmpty(), "Should detect no changes for same values");
    }

    // helper to create a single-field diff list
    private List<ChangeRecord> diffSingle(String fieldName, Object oldValue, Object newValue) {
        Map<String, Object> before = new HashMap<>();
        if (oldValue != null) before.put(fieldName, oldValue);
        Map<String, Object> after = new HashMap<>();
        if (newValue != null) after.put(fieldName, newValue);
        return DiffDetector.diff("TestObject", before, after);
    }
}
