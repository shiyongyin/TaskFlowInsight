package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.MessageType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Message类自定义标签功能测试
 * 测试新增的自定义标签功能和相关方法
 */
class CustomLabelMessageTest {
    
    @Test
    void testCreateMessageWithType() {
        // Given
        String content = "Test message content";
        MessageType type = MessageType.PROCESS;
        
        // When
        Message message = Message.withType(content, type);
        
        // Then
        assertNotNull(message.getMessageId());
        assertEquals(type, message.getType());
        assertEquals(content, message.getContent());
        assertNull(message.getCustomLabel());
        assertFalse(message.hasCustomLabel());
        assertEquals("业务流程", message.getDisplayLabel());
    }
    
    @Test
    void testCreateMessageWithCustomLabel() {
        // Given
        String content = "Custom label message";
        String customLabel = "订单状态";
        
        // When  
        Message message = Message.withLabel(content, customLabel);
        
        // Then
        assertNotNull(message.getMessageId());
        assertNull(message.getType());
        assertEquals(content, message.getContent());
        assertEquals(customLabel, message.getCustomLabel());
        assertTrue(message.hasCustomLabel());
        assertEquals(customLabel, message.getDisplayLabel());
    }
    
    @Test
    void testWithTypeNullContent() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Message.withType(null, MessageType.PROCESS)
        );
        assertEquals("Message content cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testWithTypeEmptyContent() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Message.withType("", MessageType.PROCESS)
        );
        assertEquals("Message content cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testWithTypeWhitespaceContent() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Message.withType("   ", MessageType.PROCESS)
        );
        assertEquals("Message content cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testWithTypeNullType() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Message.withType("content", null)
        );
        assertEquals("Message type cannot be null", exception.getMessage());
    }
    
    @Test
    void testWithLabelNullContent() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Message.withLabel(null, "label")
        );
        assertEquals("Message content cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testWithLabelEmptyContent() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Message.withLabel("", "label")
        );
        assertEquals("Message content cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testWithLabelNullLabel() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Message.withLabel("content", null)
        );
        assertEquals("Custom label cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testWithLabelEmptyLabel() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Message.withLabel("content", "")
        );
        assertEquals("Custom label cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testWithLabelWhitespaceLabel() {
        // When & Then  
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Message.withLabel("content", "   ")
        );
        assertEquals("Custom label cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testContentTrimming() {
        // Given
        String content = "  Test content  ";
        String customLabel = "  Custom Label  ";
        
        // When
        Message typeMessage = Message.withType(content, MessageType.METRIC);
        Message labelMessage = Message.withLabel(content, customLabel);
        
        // Then
        assertEquals("Test content", typeMessage.getContent());
        assertEquals("Test content", labelMessage.getContent());
        assertEquals("Custom Label", labelMessage.getCustomLabel());
    }
    
    @Test
    void testDisplayLabelPriority() {
        // Test that custom label takes priority over MessageType
        
        // Given - Message with MessageType
        Message typeMessage = Message.withType("content", MessageType.ALERT);
        
        // Then
        assertTrue(typeMessage.getDisplayLabel().contains("异常提示"));
        
        // Given - Message with custom label
        Message labelMessage = Message.withLabel("content", "自定义标签");
        
        // Then  
        assertEquals("自定义标签", labelMessage.getDisplayLabel());
    }
    
    @Test
    void testAllMessageTypesDisplayLabels() {
        // Test all MessageType enum values have correct display labels
        assertTrue(Message.withType("test", MessageType.PROCESS).getDisplayLabel().contains("业务流程"));
        assertTrue(Message.withType("test", MessageType.METRIC).getDisplayLabel().contains("核心指标"));
        assertTrue(Message.withType("test", MessageType.CHANGE).getDisplayLabel().contains("变更记录"));
        assertTrue(Message.withType("test", MessageType.ALERT).getDisplayLabel().contains("异常提示"));
    }
    
    @Test
    void testUniqueMessageIds() {
        // Given & When
        Message message1 = Message.withLabel("Test 1", "Label 1");
        Message message2 = Message.withLabel("Test 2", "Label 2");
        
        // Then
        assertNotEquals(message1.getMessageId(), message2.getMessageId());
    }
    
    @Test
    void testTimestampConsistency() {
        // Given
        long beforeMillis = System.currentTimeMillis();
        long beforeNanos = System.nanoTime();
        
        // When
        Message message = Message.withLabel("Test", "TestLabel");
        
        // Then
        long afterMillis = System.currentTimeMillis();
        long afterNanos = System.nanoTime();
        
        // 验证时间戳在合理范围内
        assertTrue(message.getTimestampMillis() >= beforeMillis);
        assertTrue(message.getTimestampMillis() <= afterMillis);
        assertTrue(message.getTimestampNanos() >= beforeNanos);
        assertTrue(message.getTimestampNanos() <= afterNanos);
    }
    
    @Test
    void testToStringWithCustomLabel() {
        // Given
        Message labelMessage = Message.withLabel("Test content", "自定义标签");
        String threadName = Thread.currentThread().getName();
        
        // When
        String result = labelMessage.toString();
        
        // Then
        assertTrue(result.contains("自定义标签"));
        assertTrue(result.contains("Test content"));
        assertTrue(result.contains(threadName));
    }
    
    @Test
    void testToStringWithMessageType() {
        // Given
        Message typeMessage = Message.withType("Test content", MessageType.PROCESS);
        String threadName = Thread.currentThread().getName();
        
        // When  
        String result = typeMessage.toString();
        
        // Then
        assertTrue(result.contains("业务流程"));
        assertTrue(result.contains("Test content"));
        assertTrue(result.contains(threadName));
    }
    
    @Test
    void testEqualsAndHashCode() {
        // Given
        Message message1 = Message.withLabel("Test", "Label");
        Message message2 = Message.withLabel("Test", "Label");
        
        // Then
        assertEquals(message1, message1); // 相同对象
        assertNotEquals(message1, message2); // 不同对象（不同ID）
        assertNotEquals(message1, null);
        assertNotEquals(message1, "String");
        
        // HashCode consistency
        assertEquals(message1.hashCode(), message1.hashCode());
        assertNotEquals(message1.hashCode(), message2.hashCode());
    }
}