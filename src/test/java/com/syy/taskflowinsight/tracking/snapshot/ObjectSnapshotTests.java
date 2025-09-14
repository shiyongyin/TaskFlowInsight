package com.syy.taskflowinsight.tracking.snapshot;

import org.junit.jupiter.api.Test;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ObjectSnapshot 单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
class ObjectSnapshotTests {
    
    /** 测试对象 */
    static class TestObject {
        private String name = "test";
        private int age = 25;
        private Double salary = 5000.0;
        private Boolean active = true;
        private Date createdAt = new Date();
        private Object complexObject = new Object(); // 复杂对象不应被捕获
        
        public void setName(String name) { this.name = name; }
        public void setAge(int age) { this.age = age; }
        public void setSalary(Double salary) { this.salary = salary; }
        public void setActive(Boolean active) { this.active = active; }
    }
    
    @Test
    void testCaptureAllScalarFields() {
        // Given
        TestObject obj = new TestObject();
        
        // When
        Map<String, Object> snapshot = ObjectSnapshot.capture("TestObject", obj);
        
        // Then
        assertNotNull(snapshot);
        assertEquals("test", snapshot.get("name"));
        assertEquals(25, snapshot.get("age"));
        assertEquals(5000.0, snapshot.get("salary"));
        assertEquals(true, snapshot.get("active"));
        assertNotNull(snapshot.get("createdAt"));
        assertFalse(snapshot.containsKey("complexObject"), "复杂对象不应被捕获");
    }
    
    @Test
    void testCaptureSpecificFields() {
        // Given
        TestObject obj = new TestObject();
        
        // When
        Map<String, Object> snapshot = ObjectSnapshot.capture("TestObject", obj, "name", "age");
        
        // Then
        assertEquals(2, snapshot.size());
        assertEquals("test", snapshot.get("name"));
        assertEquals(25, snapshot.get("age"));
        assertFalse(snapshot.containsKey("salary"));
    }
    
    @Test
    void testCaptureNullObject() {
        // When
        Map<String, Object> snapshot = ObjectSnapshot.capture("NullObject", null);
        
        // Then
        assertNotNull(snapshot);
        assertTrue(snapshot.isEmpty());
    }
    
    @Test
    void testDateDeepCopy() {
        // Given
        TestObject obj = new TestObject();
        Date originalDate = obj.createdAt;
        
        // When
        Map<String, Object> snapshot = ObjectSnapshot.capture("TestObject", obj, "createdAt");
        Date capturedDate = (Date) snapshot.get("createdAt");
        
        // Then
        assertNotNull(capturedDate);
        assertEquals(originalDate.getTime(), capturedDate.getTime());
        assertNotSame(originalDate, capturedDate, "Date应该被深拷贝");
    }
    
    @Test
    void testReprWithNull() {
        // When
        String repr = ObjectSnapshot.repr(null);
        
        // Then
        assertEquals("null", repr);
    }
    
    @Test
    void testReprWithLongString() {
        // Given
        String longString = "a".repeat(10000);
        
        // When
        String repr = ObjectSnapshot.repr(longString);
        
        // Then
        assertEquals(8192, repr.length());
        assertTrue(repr.endsWith("... (truncated)"));
    }
    
    @Test
    void testReprWithSpecialCharacters() {
        // Given
        String special = "line1\nline2\ttab\"quoted\"";
        
        // When
        String repr = ObjectSnapshot.repr(special);
        
        // Then
        assertEquals("line1\\nline2\\ttab\\\"quoted\\\"", repr);
    }
    
    @Test
    void testFieldNotFound() {
        // Given
        TestObject obj = new TestObject();
        
        // When
        Map<String, Object> snapshot = ObjectSnapshot.capture("TestObject", obj, "nonExistentField");
        
        // Then
        assertTrue(snapshot.isEmpty());
    }
}