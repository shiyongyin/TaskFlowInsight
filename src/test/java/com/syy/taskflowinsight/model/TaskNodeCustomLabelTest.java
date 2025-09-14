package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.enums.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskNode类支持自定义标签功能测试
 * 测试TaskNode新增的自定义标签消息方法
 */
class TaskNodeCustomLabelTest {
    
    private TaskNode rootTask;
    private TaskNode childTask;
    
    @BeforeEach
    void setUp() {
        rootTask = new TaskNode("Root Task");
        childTask = rootTask.createChild("Child Task");
    }
    
    @Test
    void testAddMessageWithMessageType() {
        // When
        Message processMsg = rootTask.addMessage("Process message", MessageType.PROCESS);
        Message metricMsg = rootTask.addMessage("Metric message", MessageType.METRIC);
        Message changeMsg = rootTask.addMessage("Change message", MessageType.CHANGE);
        Message alertMsg = rootTask.addMessage("Alert message", MessageType.ALERT);
        
        // Then
        assertEquals(4, rootTask.getMessages().size());
        
        // Verify messages were created with correct types
        assertEquals("Process message", processMsg.getContent());
        assertEquals(MessageType.PROCESS, processMsg.getType());
        assertNull(processMsg.getCustomLabel());
        
        assertEquals("Metric message", metricMsg.getContent());
        assertEquals(MessageType.METRIC, metricMsg.getType());
        
        assertEquals("Change message", changeMsg.getContent());
        assertEquals(MessageType.CHANGE, changeMsg.getType());
        
        assertEquals("Alert message", alertMsg.getContent());
        assertEquals(MessageType.ALERT, alertMsg.getType());
    }
    
    @Test
    void testAddMessageWithCustomLabel() {
        // When
        Message orderMsg = rootTask.addMessage("订单创建成功", "订单状态");
        Message userMsg = rootTask.addMessage("用户登录", "用户行为");
        Message perfMsg = rootTask.addMessage("响应时间: 100ms", "性能指标");
        Message warnMsg = rootTask.addMessage("库存不足", "业务警告");
        
        // Then
        assertEquals(4, rootTask.getMessages().size());
        
        // Verify messages were created with custom labels
        assertEquals("订单创建成功", orderMsg.getContent());
        assertNull(orderMsg.getType());
        assertEquals("订单状态", orderMsg.getCustomLabel());
        assertEquals("订单状态", orderMsg.getDisplayLabel());
        
        assertEquals("用户登录", userMsg.getContent());
        assertEquals("用户行为", userMsg.getCustomLabel());
        
        assertEquals("响应时间: 100ms", perfMsg.getContent());
        assertEquals("性能指标", perfMsg.getCustomLabel());
        
        assertEquals("库存不足", warnMsg.getContent());
        assertEquals("业务警告", warnMsg.getCustomLabel());
    }
    
    @Test
    void testMixedMessageTypes() {
        // When - Mix MessageType and custom labels
        Message processMsg = rootTask.addMessage("开始处理", MessageType.PROCESS);
        Message customMsg = rootTask.addMessage("订单ID: 12345", "订单信息");
        Message metricMsg = rootTask.addMessage("验证通过", MessageType.METRIC);
        Message statusMsg = rootTask.addMessage("支付成功", "支付状态");
        
        // Then
        assertEquals(4, rootTask.getMessages().size());
        
        // Verify mixed types
        assertEquals(MessageType.PROCESS, processMsg.getType());
        assertNull(processMsg.getCustomLabel());
        
        assertNull(customMsg.getType());
        assertEquals("订单信息", customMsg.getCustomLabel());
        
        assertEquals(MessageType.METRIC, metricMsg.getType());
        assertNull(metricMsg.getCustomLabel());
        
        assertNull(statusMsg.getType());
        assertEquals("支付状态", statusMsg.getCustomLabel());
    }
    
    @Test
    void testAddMessageWithNullContent() {
        // When & Then - Should throw exception
        IllegalArgumentException exception1 = assertThrows(
            IllegalArgumentException.class,
            () -> rootTask.addMessage(null, MessageType.PROCESS)
        );
        assertEquals("Message content cannot be null or empty", exception1.getMessage());
        
        IllegalArgumentException exception2 = assertThrows(
            IllegalArgumentException.class,
            () -> rootTask.addMessage(null, "Custom Label")
        );
        assertEquals("Message content cannot be null or empty", exception2.getMessage());
    }
    
    @Test
    void testAddMessageWithEmptyContent() {
        // When & Then - Should throw exception
        IllegalArgumentException exception1 = assertThrows(
            IllegalArgumentException.class,
            () -> rootTask.addMessage("", MessageType.PROCESS)
        );
        assertEquals("Message content cannot be null or empty", exception1.getMessage());
        
        IllegalArgumentException exception2 = assertThrows(
            IllegalArgumentException.class,
            () -> rootTask.addMessage("   ", "Custom Label")
        );
        assertEquals("Message content cannot be null or empty", exception2.getMessage());
    }
    
    @Test
    void testAddMessageWithNullMessageType() {
        // When & Then - Should throw exception
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> rootTask.addMessage("content", (MessageType) null)
        );
        assertEquals("Message type cannot be null", exception.getMessage());
    }
    
    @Test
    void testAddMessageWithNullCustomLabel() {
        // When & Then - Should throw exception
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> rootTask.addMessage("content", (String) null)
        );
        assertEquals("Custom label cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testAddMessageWithEmptyCustomLabel() {
        // When & Then - Should throw exception
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> rootTask.addMessage("content", "")
        );
        assertEquals("Custom label cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testContentTrimming() {
        // Given
        String content = "  Test content  ";
        String customLabel = "  Custom Label  ";
        
        // When
        Message typeMsg = rootTask.addMessage(content, MessageType.PROCESS);
        Message labelMsg = rootTask.addMessage(content, customLabel);
        
        // Then
        assertEquals("Test content", typeMsg.getContent());
        assertEquals("Test content", labelMsg.getContent());
        assertEquals("Custom Label", labelMsg.getCustomLabel());
    }
    
    @Test
    void testMessagesOrder() {
        // When - Add messages in specific order
        Message first = rootTask.addMessage("First message", MessageType.PROCESS);
        Message second = rootTask.addMessage("Second message", "Custom1");
        Message third = rootTask.addMessage("Third message", MessageType.METRIC);
        Message fourth = rootTask.addMessage("Fourth message", "Custom2");
        
        // Then - Verify order is preserved
        assertEquals(4, rootTask.getMessages().size());
        assertEquals(first, rootTask.getMessages().get(0));
        assertEquals(second, rootTask.getMessages().get(1));
        assertEquals(third, rootTask.getMessages().get(2));
        assertEquals(fourth, rootTask.getMessages().get(3));
    }
    
    @Test
    void testMessagesInNestedTasks() {
        // Given
        TaskNode grandChild = childTask.createChild("Grand Child");
        
        // When
        rootTask.addMessage("Root message", MessageType.PROCESS);
        childTask.addMessage("Child message", "Child Label");
        grandChild.addMessage("Grand message", MessageType.ALERT);
        
        // Then
        assertEquals(1, rootTask.getMessages().size());
        assertEquals(1, childTask.getMessages().size());
        assertEquals(1, grandChild.getMessages().size());
        
        assertEquals("Root message", rootTask.getMessages().get(0).getContent());
        assertEquals("Child message", childTask.getMessages().get(0).getContent());
        assertEquals("Grand message", grandChild.getMessages().get(0).getContent());
    }
    
    @Test
    void testLegacyAddInfoAndAddErrorMethods() {
        // When - Use legacy methods (should still work)
        Message infoMsg = rootTask.addInfo("Info message");
        Message errorMsg = rootTask.addError("Error message");
        Message errorExceptionMsg = rootTask.addError(new RuntimeException("Exception message"));
        
        // Then
        assertEquals(3, rootTask.getMessages().size());
        
        assertEquals("Info message", infoMsg.getContent());
        assertEquals(MessageType.PROCESS, infoMsg.getType()); // Maps to PROCESS
        
        assertEquals("Error message", errorMsg.getContent());
        assertEquals(MessageType.ALERT, errorMsg.getType()); // Maps to ALERT
        
        assertEquals("Exception message", errorExceptionMsg.getContent());
        assertEquals(MessageType.ALERT, errorExceptionMsg.getType());
    }
    
    @Test
    void testConcurrentMessageAddition() throws InterruptedException {
        // Given
        final int threadCount = 5;
        final int messagesPerThread = 20;
        Thread[] threads = new Thread[threadCount];
        
        // When - Multiple threads adding messages concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < messagesPerThread; j++) {
                    if (j % 2 == 0) {
                        rootTask.addMessage("Thread-" + threadId + "-Msg-" + j, MessageType.PROCESS);
                    } else {
                        rootTask.addMessage("Thread-" + threadId + "-Msg-" + j, "Thread-" + threadId);
                    }
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Then
        int expectedMessages = threadCount * messagesPerThread;
        assertEquals(expectedMessages, rootTask.getMessages().size());
        
        // Verify no messages were lost and all have proper content
        for (Message message : rootTask.getMessages()) {
            assertNotNull(message.getContent());
            assertTrue(message.getContent().startsWith("Thread-"));
        }
    }
    
    @Test
    void testMessageTimestamps() {
        // Given
        long beforeTime = System.currentTimeMillis();
        
        // When
        Message msg1 = rootTask.addMessage("First", MessageType.PROCESS);
        
        try {
            Thread.sleep(10); // Small delay to ensure different timestamps
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Message msg2 = rootTask.addMessage("Second", "Custom");
        long afterTime = System.currentTimeMillis();
        
        // Then
        assertTrue(msg1.getTimestampMillis() >= beforeTime);
        assertTrue(msg1.getTimestampMillis() <= afterTime);
        assertTrue(msg2.getTimestampMillis() >= beforeTime);
        assertTrue(msg2.getTimestampMillis() <= afterTime);
        assertTrue(msg2.getTimestampMillis() >= msg1.getTimestampMillis());
    }
    
    @Test
    void testTaskCompletionWithMessages() {
        // Given
        rootTask.addMessage("Before completion", MessageType.PROCESS);
        rootTask.addMessage("Custom message", "Custom Label");
        
        // When
        rootTask.complete();
        
        // Then
        assertEquals(TaskStatus.COMPLETED, rootTask.getStatus());
        assertEquals(2, rootTask.getMessages().size());
        
        // Messages should still be accessible after completion
        assertEquals("Before completion", rootTask.getMessages().get(0).getContent());
        assertEquals("Custom message", rootTask.getMessages().get(1).getContent());
    }
    
    @Test
    void testTaskFailureWithMessages() {
        // Given
        rootTask.addMessage("Before failure", "Status");
        
        // When
        rootTask.fail("Task failed");
        
        // Then
        assertEquals(TaskStatus.FAILED, rootTask.getStatus());
        assertEquals(2, rootTask.getMessages().size()); // Original + failure message
        
        assertEquals("Before failure", rootTask.getMessages().get(0).getContent());
        assertEquals("Task failed", rootTask.getMessages().get(1).getContent());
        assertEquals(MessageType.ALERT, rootTask.getMessages().get(1).getType());
    }
}