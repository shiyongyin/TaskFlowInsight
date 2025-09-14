package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Message;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Message类测试
 * 测试消息对象的创建、工厂方法和所有功能
 */
class MessageTest {
    
    @Test
    void testCreateInfoMessage() {
        String content = "Test info message";
        Message message = Message.info(content);
        
        assertNotNull(message.getMessageId());
        assertEquals(MessageType.PROCESS, message.getType());
        assertEquals(content, message.getContent());
        assertTrue(message.isProcess());
        assertFalse(message.isAlert());
        assertNotNull(message.getThreadName());
        assertTrue(message.getTimestampMillis() > 0);
        assertTrue(message.getTimestampNanos() > 0);
    }
    
    @Test
    void testCreateErrorMessage() {
        String content = "Test error message";
        Message message = Message.error(content);
        
        assertNotNull(message.getMessageId());
        assertEquals(MessageType.ALERT, message.getType());
        assertEquals(content, message.getContent());
        assertFalse(message.isProcess());
        assertTrue(message.isAlert());
        assertNotNull(message.getThreadName());
        assertTrue(message.getTimestampMillis() > 0);
        assertTrue(message.getTimestampNanos() > 0);
    }
    
    @Test
    void testCreateErrorMessageFromThrowable() {
        RuntimeException exception = new RuntimeException("Test exception");
        Message message = Message.error(exception);
        
        assertEquals(MessageType.ALERT, message.getType());
        assertEquals("Test exception", message.getContent());
        assertTrue(message.isAlert());
    }
    
    @Test
    void testCreateErrorMessageFromThrowableWithoutMessage() {
        RuntimeException exception = new RuntimeException();
        Message message = Message.error(exception);
        
        assertEquals(MessageType.ALERT, message.getType());
        assertEquals("RuntimeException", message.getContent());
        assertTrue(message.isAlert());
    }
    
    @Test
    void testInfoWithNullContent() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Message.info(null)
        );
        assertEquals("Message content cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testInfoWithEmptyContent() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Message.info("")
        );
        assertEquals("Message content cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testInfoWithWhitespaceContent() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Message.info("   ")
        );
        assertEquals("Message content cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testErrorWithNullContent() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Message.error((String) null)
        );
        assertEquals("Message content cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testErrorWithEmptyContent() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Message.error("")
        );
        assertEquals("Message content cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testErrorWithNullThrowable() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Message.error((Throwable) null)
        );
        assertEquals("Throwable cannot be null", exception.getMessage());
    }
    
    @Test
    void testContentTrimming() {
        String content = "  Test content  ";
        Message infoMessage = Message.info(content);
        Message errorMessage = Message.error(content);
        
        assertEquals("Test content", infoMessage.getContent());
        assertEquals("Test content", errorMessage.getContent());
    }
    
    @Test
    void testUniqueMessageIds() {
        Message message1 = Message.info("Test 1");
        Message message2 = Message.info("Test 2");
        
        assertNotEquals(message1.getMessageId(), message2.getMessageId());
    }
    
    @Test
    void testThreadNameCapture() {
        Thread.currentThread().setName("TestThread");
        Message message = Message.info("Test");
        
        assertEquals("TestThread", message.getThreadName());
    }
    
    @Test
    void testTimestampConsistency() {
        long beforeMillis = System.currentTimeMillis();
        long beforeNanos = System.nanoTime();
        
        Message message = Message.info("Test");
        
        long afterMillis = System.currentTimeMillis();
        long afterNanos = System.nanoTime();
        
        // 验证时间戳在合理范围内
        assertTrue(message.getTimestampMillis() >= beforeMillis);
        assertTrue(message.getTimestampMillis() <= afterMillis);
        assertTrue(message.getTimestampNanos() >= beforeNanos);
        assertTrue(message.getTimestampNanos() <= afterNanos);
    }
    
    @Test
    void testEquals() {
        Message message1 = Message.info("Test");
        Message message2 = Message.info("Test");
        
        assertEquals(message1, message1); // 相同对象
        assertNotEquals(message1, message2); // 不同对象（不同ID）
        assertNotEquals(message1, null);
        assertNotEquals(message1, "String");
    }
    
    @Test
    void testHashCode() {
        Message message1 = Message.info("Test");
        Message message2 = Message.info("Test");
        
        assertEquals(message1.hashCode(), message1.hashCode()); // 一致性
        assertNotEquals(message1.hashCode(), message2.hashCode()); // 不同对象
    }
    
    @Test
    void testToString() {
        Message infoMessage = Message.info("Test info");
        Message errorMessage = Message.error("Test error");
        
        String infoString = infoMessage.toString();
        String errorString = errorMessage.toString();
        
        assertTrue(infoString.contains("业务流程"));
        assertTrue(infoString.contains("Test info"));
        assertTrue(infoString.contains(infoMessage.getThreadName()));
        
        assertTrue(errorString.contains("异常提示"));
        assertTrue(errorString.contains("Test error"));
        assertTrue(errorString.contains(errorMessage.getThreadName()));
    }
    
    @Test
    void testImmutability() {
        Message message = Message.info("Test");
        
        // 验证所有getter方法返回的值在多次调用时保持不变
        String messageId = message.getMessageId();
        MessageType type = message.getType();
        String content = message.getContent();
        long timestampMillis = message.getTimestampMillis();
        long timestampNanos = message.getTimestampNanos();
        String threadName = message.getThreadName();
        
        assertEquals(messageId, message.getMessageId());
        assertEquals(type, message.getType());
        assertEquals(content, message.getContent());
        assertEquals(timestampMillis, message.getTimestampMillis());
        assertEquals(timestampNanos, message.getTimestampNanos());
        assertEquals(threadName, message.getThreadName());
    }
}