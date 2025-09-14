package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证valueRepr语义修改后的正确性
 * 确认valueRepr仅包含新值的统一字符串表现，而非"old → new"
 */
class ValueReprVerificationTest {
    
    @Test
    void testValueReprOnlyContainsNewValue() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("status", "PENDING");
        before.put("amount", 100.0);
        
        Map<String, Object> after = new HashMap<>();
        after.put("status", "PAID");
        after.put("amount", 150.0);
        
        // When
        List<ChangeRecord> changes = DiffDetector.diff("Order", before, after);
        
        // Then
        assertEquals(2, changes.size());
        
        // 验证amount变更的valueRepr
        ChangeRecord amountChange = changes.stream()
            .filter(c -> c.getFieldName().equals("amount"))
            .findFirst().orElse(null);
        assertNotNull(amountChange);
        assertEquals("150.0", amountChange.getValueRepr(), 
            "valueRepr应该只包含新值，而非'100.0 → 150.0'");
        assertFalse(amountChange.getValueRepr().contains("→"), 
            "valueRepr不应包含箭头符号");
        
        // 验证status变更的valueRepr
        ChangeRecord statusChange = changes.stream()
            .filter(c -> c.getFieldName().equals("status"))
            .findFirst().orElse(null);
        assertNotNull(statusChange);
        assertEquals("PAID", statusChange.getValueRepr(),
            "valueRepr应该只包含新值，而非'PENDING → PAID'");
        assertFalse(statusChange.getValueRepr().contains("→"),
            "valueRepr不应包含箭头符号");
    }
    
    @Test
    void testValueReprForDelete() {
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
        assertEquals(ChangeType.DELETE, change.getChangeType());
        assertNull(change.getValueRepr(), 
            "DELETE场景下valueRepr应该为null");
    }
    
    @Test
    void testValueReprForCreate() {
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
        assertEquals(ChangeType.CREATE, change.getChangeType());
        assertEquals("John", change.getValueRepr(),
            "CREATE场景下valueRepr应该包含新值");
        assertFalse(change.getValueRepr().contains("null"),
            "valueRepr不应包含null → John格式");
    }
    
    @Test
    void testFormatValueUsesObjectSnapshotRepr() {
        // 验证长字符串的截断行为一致
        String longString = "a".repeat(10000);
        String repr = ObjectSnapshot.repr(longString);
        
        // 验证统一的截断长度和后缀
        assertEquals(8192, repr.length());
        assertTrue(repr.endsWith("... (truncated)"));
        
        // 验证特殊字符转义
        String special = "line1\nline2\ttab";
        String escapedRepr = ObjectSnapshot.repr(special);
        assertEquals("line1\\nline2\\ttab", escapedRepr);
    }
}