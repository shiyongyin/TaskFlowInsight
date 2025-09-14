package com.syy.taskflowinsight.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MessageType枚举测试类
 * 测试业务消息类型枚举的所有功能和边界条件
 */
class MessageTypeTest {
    
    @Test
    void testEnumValues() {
        MessageType[] values = MessageType.values();
        assertEquals(4, values.length);
        
        assertEquals(MessageType.PROCESS, MessageType.valueOf("PROCESS"));
        assertEquals(MessageType.METRIC, MessageType.valueOf("METRIC"));
        assertEquals(MessageType.CHANGE, MessageType.valueOf("CHANGE"));
        assertEquals(MessageType.ALERT, MessageType.valueOf("ALERT"));
    }
    
    @Test
    void testDisplayNames() {
        assertTrue(MessageType.PROCESS.getDisplayName().contains("业务流程"));
        assertTrue(MessageType.METRIC.getDisplayName().contains("核心指标"));
        assertTrue(MessageType.CHANGE.getDisplayName().contains("变更记录"));
        assertTrue(MessageType.ALERT.getDisplayName().contains("异常提示"));
    }
    
    @Test
    void testLevels() {
        assertEquals(1, MessageType.PROCESS.getLevel());
        assertEquals(2, MessageType.METRIC.getLevel());
        assertEquals(3, MessageType.CHANGE.getLevel());
        assertEquals(4, MessageType.ALERT.getLevel());
    }
    
    @Test
    void testBusinessMessageTypes() {
        assertTrue(MessageType.PROCESS.isProcess());
        assertFalse(MessageType.PROCESS.isMetric());
        assertFalse(MessageType.PROCESS.isChange());
        assertFalse(MessageType.PROCESS.isAlert());
        
        assertTrue(MessageType.METRIC.isMetric());
        assertFalse(MessageType.METRIC.isProcess());
        
        assertTrue(MessageType.CHANGE.isChange());
        assertFalse(MessageType.CHANGE.isAlert());
        
        assertTrue(MessageType.ALERT.isAlert());
        assertFalse(MessageType.ALERT.isProcess());
    }
    
    @Test
    void testCompareLevel() {
        // PROCESS vs ALERT
        assertTrue(MessageType.PROCESS.compareLevel(MessageType.ALERT) < 0);
        assertTrue(MessageType.ALERT.compareLevel(MessageType.PROCESS) > 0);
        
        // METRIC vs CHANGE
        assertTrue(MessageType.METRIC.compareLevel(MessageType.CHANGE) < 0);
        assertTrue(MessageType.CHANGE.compareLevel(MessageType.METRIC) > 0);
        
        // 相同类型比较
        assertEquals(0, MessageType.PROCESS.compareLevel(MessageType.PROCESS));
        assertEquals(0, MessageType.ALERT.compareLevel(MessageType.ALERT));
    }
    
    @Test
    void testCompareLevelWithNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MessageType.PROCESS.compareLevel(null)
        );
        assertEquals("Other message type cannot be null", exception.getMessage());
    }
    
    @Test
    void testToString() {
        assertTrue(MessageType.PROCESS.toString().contains("业务流程"));
        assertTrue(MessageType.METRIC.toString().contains("核心指标"));
        assertTrue(MessageType.CHANGE.toString().contains("变更记录"));
        assertTrue(MessageType.ALERT.toString().contains("异常提示"));
    }
    
    @Test
    void testIsImportant() {
        assertFalse(MessageType.PROCESS.isImportant());
        assertFalse(MessageType.METRIC.isImportant());
        assertTrue(MessageType.CHANGE.isImportant());
        assertTrue(MessageType.ALERT.isImportant());
    }
    
    @Test
    void testEnumConsistency() {
        // 确保枚举顺序和值的一致性
        MessageType[] values = MessageType.values();
        assertEquals(MessageType.PROCESS, values[0]);
        assertEquals(MessageType.METRIC, values[1]);
        assertEquals(MessageType.CHANGE, values[2]);
        assertEquals(MessageType.ALERT, values[3]);
        
        // 验证级别设计的一致性
        assertTrue(MessageType.ALERT.getLevel() > MessageType.PROCESS.getLevel());
    }
}