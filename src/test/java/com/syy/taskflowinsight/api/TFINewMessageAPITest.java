package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TFI新消息API方法的单元测试
 * 测试新的message()方法和重构后的error()方法
 */
class TFINewMessageAPITest {

    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.clear();
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
    }

    @Test
    void testMessageWithMessageType() {
        // Given
        TFI.startSession("Test Session");
        
        try (TaskContext ctx = TFI.start("Test Task")) {
            // When
            TFI.message("Test process message", MessageType.PROCESS);
            TFI.message("Test metric message", MessageType.METRIC);
            TFI.message("Test change message", MessageType.CHANGE);
            TFI.message("Test alert message", MessageType.ALERT);
            
            // Then
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(4);
            
            Message processMsg = node.getMessages().get(0);
            assertEquals("Test process message", processMsg.getContent());
            assertEquals(MessageType.PROCESS, processMsg.getType());
            assertNull(processMsg.getCustomLabel());
            assertEquals("业务流程", processMsg.getDisplayLabel());
            
            Message metricMsg = node.getMessages().get(1);
            assertEquals("Test metric message", metricMsg.getContent());
            assertEquals(MessageType.METRIC, metricMsg.getType());
            
            Message changeMsg = node.getMessages().get(2);
            assertEquals("Test change message", changeMsg.getContent());
            assertEquals(MessageType.CHANGE, changeMsg.getType());
            
            Message alertMsg = node.getMessages().get(3);
            assertEquals("Test alert message", alertMsg.getContent());
            assertEquals(MessageType.ALERT, alertMsg.getType());
        }
        
        TFI.endSession();
    }

    @Test
    void testMessageWithCustomLabel() {
        // Given
        TFI.startSession("Test Session");
        
        try (TaskContext ctx = TFI.start("Test Task")) {
            // When
            TFI.message("订单创建成功", "订单状态");
            TFI.message("用户登录", "用户行为");
            TFI.message("响应时间: 100ms", "性能指标");
            TFI.message("库存不足警告", "业务异常");
            
            // Then
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(4);
            
            Message orderMsg = node.getMessages().get(0);
            assertEquals("订单创建成功", orderMsg.getContent());
            assertNull(orderMsg.getType());
            assertEquals("订单状态", orderMsg.getCustomLabel());
            assertEquals("订单状态", orderMsg.getDisplayLabel());
            
            Message userMsg = node.getMessages().get(1);
            assertEquals("用户登录", userMsg.getContent());
            assertEquals("用户行为", userMsg.getCustomLabel());
            
            Message perfMsg = node.getMessages().get(2);
            assertEquals("响应时间: 100ms", perfMsg.getContent());
            assertEquals("性能指标", perfMsg.getCustomLabel());
            
            Message alertMsg = node.getMessages().get(3);
            assertEquals("库存不足警告", alertMsg.getContent());
            assertEquals("业务异常", alertMsg.getCustomLabel());
        }
        
        TFI.endSession();
    }

    @Test
    void testMixedMessageTypes() {
        // Given
        TFI.startSession("Mixed Test");
        
        try (TaskContext ctx = TFI.start("Mixed Task")) {
            // When - Mix MessageType and custom labels
            TFI.message("开始处理", MessageType.PROCESS);
            TFI.message("订单ID: 12345", "订单信息");
            TFI.message("用户验证通过", MessageType.METRIC);
            TFI.message("支付状态: 成功", "支付结果");
            
            // Then
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(4);
            
            // Verify mixed types work correctly
            Message processMsg = node.getMessages().get(0);
            assertEquals(MessageType.PROCESS, processMsg.getType());
            assertNull(processMsg.getCustomLabel());
            
            Message orderMsg = node.getMessages().get(1);
            assertNull(orderMsg.getType());
            assertEquals("订单信息", orderMsg.getCustomLabel());
            
            Message metricMsg = node.getMessages().get(2);
            assertEquals(MessageType.METRIC, metricMsg.getType());
            assertNull(metricMsg.getCustomLabel());
            
            Message paymentMsg = node.getMessages().get(3);
            assertNull(paymentMsg.getType());
            assertEquals("支付结果", paymentMsg.getCustomLabel());
        }
        
        TFI.endSession();
    }

    @Test
    void testErrorMethods() {
        // Given
        TFI.startSession("Error Test");
        
        try (TaskContext ctx = TFI.start("Error Task")) {
            // When
            TFI.error("Simple error message");
            TFI.error("Error with exception", new RuntimeException("Test exception"));
            
            // Then
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(2);
            
            Message simpleError = node.getMessages().get(0);
            assertEquals("Simple error message", simpleError.getContent());
            assertEquals(MessageType.ALERT, simpleError.getType());
            
            Message exceptionError = node.getMessages().get(1);
            assertEquals("Error with exception - RuntimeException: Test exception", exceptionError.getContent());
            assertEquals(MessageType.ALERT, exceptionError.getType());
        }
        
        TFI.endSession();
    }

    @Test
    void testMessageWithNullContent() {
        // Given
        TFI.startSession("Null Test");
        
        try (TaskContext ctx = TFI.start("Null Task")) {
            // When & Then - Should not throw exception, just ignore
            TFI.message(null, MessageType.PROCESS);
            TFI.message(null, "Custom Label");
            
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(0); // Should be empty
        }
        
        TFI.endSession();
    }

    @Test
    void testMessageWithEmptyContent() {
        // Given
        TFI.startSession("Empty Test");
        
        try (TaskContext ctx = TFI.start("Empty Task")) {
            // When & Then - Should not throw exception, just ignore
            TFI.message("", MessageType.PROCESS);
            TFI.message("   ", "Custom Label");
            
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(0); // Should be empty
        }
        
        TFI.endSession();
    }

    @Test
    void testMessageWithNullMessageType() {
        // Given
        TFI.startSession("Null Type Test");
        
        try (TaskContext ctx = TFI.start("Null Type Task")) {
            // When & Then - Should not throw exception, just ignore
            TFI.message("Test content", (MessageType) null);
            
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(0); // Should be empty
        }
        
        TFI.endSession();
    }

    @Test
    void testMessageWithNullCustomLabel() {
        // Given
        TFI.startSession("Null Label Test");
        
        try (TaskContext ctx = TFI.start("Null Label Task")) {
            // When & Then - Should not throw exception, just ignore
            TFI.message("Test content", (String) null);
            
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(0); // Should be empty
        }
        
        TFI.endSession();
    }

    @Test
    void testMessageWhenDisabled() {
        // Given
        TFI.disable();
        TFI.startSession("Disabled Test");
        
        try (TaskContext ctx = TFI.start("Disabled Task")) {
            // When
            TFI.message("This should be ignored", MessageType.PROCESS);
            TFI.message("This should also be ignored", "Custom Label");
            
            // Then
            TaskNode node = TFI.getCurrentTask();
            if (node != null) {
                assertThat(node.getMessages()).hasSize(0);
            }
        }
        
        TFI.endSession();
        TFI.enable(); // Reset for other tests
    }

    @Test
    void testMessageWithoutActiveTask() {
        // Given - No active task context
        TFI.startSession("No Task Test");
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> {
            TFI.message("No task message", MessageType.PROCESS);
            TFI.message("No task custom", "Custom Label");
        });
        
        TFI.endSession();
    }

    @Test
    void testContentTrimming() {
        // Given
        TFI.startSession("Trim Test");
        
        try (TaskContext ctx = TFI.start("Trim Task")) {
            // When
            TFI.message("  Trimmed content  ", MessageType.PROCESS);
            TFI.message("  Another trimmed  ", "Custom Label");
            
            // Then
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(2);
            
            assertEquals("Trimmed content", node.getMessages().get(0).getContent());
            assertEquals("Another trimmed", node.getMessages().get(1).getContent());
        }
        
        TFI.endSession();
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        // Given
        TFI.startSession("Thread Safety Test");
        final int threadCount = 10;
        final int messagesPerThread = 100;
        
        try (TaskContext ctx = TFI.start("Thread Safety Task")) {
            TaskNode sharedTask = TFI.getCurrentTask();
            Thread[] threads = new Thread[threadCount];
            
            // When - Multiple threads adding messages concurrently to the shared task
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < messagesPerThread; j++) {
                        // Add messages directly to the shared task node
                        sharedTask.addMessage("Thread-" + threadId + "-Message-" + j, MessageType.PROCESS);
                        sharedTask.addMessage("Custom-" + threadId + "-" + j, "Thread-" + threadId);
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
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            
            // Should have threadCount * messagesPerThread * 2 messages
            int expectedMessages = threadCount * messagesPerThread * 2;
            assertThat(node.getMessages()).hasSize(expectedMessages);
        }
        
        TFI.endSession();
    }
}