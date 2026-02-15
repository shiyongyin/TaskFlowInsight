package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Message;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Message 类单元测试 - 消息对象创建与属性验证
 * 
 * <h2>测试设计思路：</h2>
 * <ul>
 *   <li>基于工厂方法模式设计测试，验证info/error静态工厂方法</li>
 *   <li>使用参数验证测试确保输入数据的合法性检查</li>
 *   <li>通过时间戳一致性测试验证消息创建时间的准确性</li>
 *   <li>采用不变性测试确保消息对象的不可变特性</li>
 *   <li>使用对象契约测试验证equals/hashCode/toString实现</li>
 * </ul>
 * 
 * <h2>覆盖范围：</h2>
 * <ul>
 *   <li><strong>工厂方法：</strong>Message.info()和Message.error()静态方法创建</li>
 *   <li><strong>异常处理：</strong>从Throwable对象提取错误消息</li>
 *   <li><strong>输入验证：</strong>null/empty/whitespace内容的异常处理</li>
 *   <li><strong>消息属性：</strong>ID唯一性、类型正确性、内容准确性</li>
 *   <li><strong>时间戳：</strong>创建时间戳（毫秒/纳秒）的一致性验证</li>
 *   <li><strong>线程信息：</strong>线程名称捕获的准确性</li>
 *   <li><strong>内容处理：</strong>字符串trim处理、异常消息提取</li>
 *   <li><strong>对象行为：</strong>equals/hashCode/toString方法实现</li>
 * </ul>
 * 
 * <h2>测试场景：</h2>
 * <ul>
 *   <li><strong>正常创建：</strong>info/error消息创建、属性验证</li>
 *   <li><strong>异常创建：</strong>从RuntimeException提取消息内容</li>
 *   <li><strong>边界值：</strong>null/empty/whitespace参数处理</li>
 *   <li><strong>内容处理：</strong>前后空格trim、异常类名提取</li>
 *   <li><strong>唯一性：</strong>消息ID唯一性验证</li>
 *   <li><strong>不变性：</strong>多次getter调用结果一致性</li>
 * </ul>
 * 
 * <h2>期望结果：</h2>
 * <ul>
 *   <li><strong>消息创建正确：</strong>类型、内容、时间戳都准确设置</li>
 *   <li><strong>ID唯一性保证：</strong>每个消息对象都有唯一标识符</li>
 *   <li><strong>输入验证健壮：</strong>无效输入正确抛出IllegalArgumentException</li>
 *   <li><strong>异常处理完善：</strong>Throwable对象消息正确提取</li>
 *   <li><strong>时间戳准确：</strong>创建时间在合理时间范围内</li>
 *   <li><strong>对象不变：</strong>消息对象创建后属性不可变</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
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