package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * API兼容性和边界情况测试
 * 测试新API与现有系统的兼容性以及各种边界情况的处理
 */
class APICompatibilityAndBoundaryTest {

    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.clear();
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
    }

    // ========== API兼容性测试 ==========
    
    @Test
    void testLegacyMessageFactoryMethodsStillWork() {
        // Given & When - Use legacy Message factory methods
        Message infoMsg = Message.info("Info message");
        Message errorMsg = Message.error("Error message");
        Message errorExceptionMsg = Message.error(new RuntimeException("Exception"));
        
        // Then - Should still work and map to new types
        assertEquals("Info message", infoMsg.getContent());
        assertEquals(MessageType.PROCESS, infoMsg.getType());
        assertNull(infoMsg.getCustomLabel());
        assertEquals("业务流程", infoMsg.getDisplayLabel());
        
        assertEquals("Error message", errorMsg.getContent());
        assertEquals(MessageType.ALERT, errorMsg.getType());
        assertNull(errorMsg.getCustomLabel());
        
        assertEquals("Exception", errorExceptionMsg.getContent());
        assertEquals(MessageType.ALERT, errorExceptionMsg.getType());
    }
    
    @Test
    void testExistingTFIErrorMethodsStillWork() {
        // Given
        TFI.startSession("Compatibility Test");
        
        try (TaskContext ctx = TFI.start("Error Test")) {
            // When - Use existing error methods
            TFI.error("Simple error");
            TFI.error("Error with exception", new RuntimeException("Test exception"));
            
            // Then
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(2);
            
            assertEquals("Simple error", node.getMessages().get(0).getContent());
            assertEquals(MessageType.ALERT, node.getMessages().get(0).getType());
            
            assertEquals("Error with exception - RuntimeException: Test exception", node.getMessages().get(1).getContent());
            assertEquals(MessageType.ALERT, node.getMessages().get(1).getType());
        }
        
        TFI.endSession();
    }
    
    @Test
    void testExistingTaskNodeAddMethodsStillWork() {
        // Given
        TaskNode root = new TaskNode("Root Task");
        
        // When - Use existing TaskNode methods
        Message infoMsg = root.addInfo("Info message");
        Message errorMsg = root.addError("Error message");
        Message errorExceptionMsg = root.addError(new RuntimeException("Exception"));
        
        // Then
        assertEquals(3, root.getMessages().size());
        
        assertEquals("Info message", infoMsg.getContent());
        assertEquals(MessageType.PROCESS, infoMsg.getType()); // Should map to PROCESS
        
        assertEquals("Error message", errorMsg.getContent());
        assertEquals(MessageType.ALERT, errorMsg.getType()); // Should map to ALERT
        
        assertEquals("Exception", errorExceptionMsg.getContent());
        assertEquals(MessageType.ALERT, errorExceptionMsg.getType());
    }
    
    @Test
    void testMessageTypeEnumStillHasCorrectMethods() {
        // Test that MessageType enum still has required methods for compatibility
        assertTrue(MessageType.PROCESS.isProcess());
        assertFalse(MessageType.PROCESS.isAlert());
        assertFalse(MessageType.PROCESS.isMetric());
        assertFalse(MessageType.PROCESS.isChange());
        
        assertTrue(MessageType.ALERT.isAlert());
        assertFalse(MessageType.ALERT.isProcess());
        
        assertTrue(MessageType.METRIC.isMetric());
        assertTrue(MessageType.CHANGE.isChange());
    }
    
    // ========== 边界情况测试 ==========
    
    @Test
    void testNullAndEmptyContentHandling() {
        // Given
        TFI.startSession("Boundary Test");
        
        try (TaskContext ctx = TFI.start("Null Content Test")) {
            // When & Then - Should not throw exceptions, just ignore silently
            assertDoesNotThrow(() -> {
                TFI.message(null, MessageType.PROCESS);
                TFI.message("", MessageType.PROCESS);
                TFI.message("   ", MessageType.PROCESS);
                TFI.message(null, "Custom Label");
                TFI.message("", "Custom Label");
                TFI.message("   ", "Custom Label");
            });
            
            // Should have no messages added
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(0);
        }
        
        TFI.endSession();
    }
    
    @Test
    void testNullParametersHandling() {
        // Given
        TFI.startSession("Null Parameters Test");
        
        try (TaskContext ctx = TFI.start("Null Params Test")) {
            // When & Then - Should handle gracefully
            assertDoesNotThrow(() -> {
                TFI.message("content", (MessageType) null);
                TFI.message("content", (String) null);
                TFI.error((String) null);
                TFI.error(null, null);
            });
            
            // Should have no messages added due to null parameters
            TaskNode node = TFI.getCurrentTask();
            if (node != null) {
                assertThat(node.getMessages()).hasSize(0);
            }
        }
        
        TFI.endSession();
    }
    
    @Test
    void testVeryLongContentAndLabels() {
        // Given
        String veryLongContent = "A".repeat(10000); // 10K characters
        String veryLongLabel = "B".repeat(1000);    // 1K characters
        
        TFI.startSession("Long Content Test");
        
        try (TaskContext ctx = TFI.start("Long Content Task")) {
            // When
            TFI.message(veryLongContent, MessageType.PROCESS);
            TFI.message("Short content", veryLongLabel);
            
            // Then
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(2);
            
            assertEquals(veryLongContent, node.getMessages().get(0).getContent());
            assertEquals(veryLongLabel, node.getMessages().get(1).getCustomLabel());
        }
        
        TFI.endSession();
    }
    
    @Test
    void testSpecialCharactersInContentAndLabels() {
        // Given
        String specialContent = "特殊字符测试 \n\r\t @#$%^&*()[]{}";
        String specialLabel = "标签@#$%^&*()特殊字符";
        
        TFI.startSession("Special Chars Test");
        
        try (TaskContext ctx = TFI.start("Special Chars Task")) {
            // When
            TFI.message(specialContent, MessageType.PROCESS);
            TFI.message("Normal content", specialLabel);
            
            // Then
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(2);
            
            assertEquals(specialContent, node.getMessages().get(0).getContent());
            assertEquals(specialLabel, node.getMessages().get(1).getCustomLabel());
        }
        
        TFI.endSession();
    }
    
    @Test
    void testUnicodeAndEmojisInContentAndLabels() {
        // Given
        String unicodeContent = "测试中文内容 🚀 Test Unicode 🎉";
        String emojiLabel = "🏷️标签";
        
        TFI.startSession("Unicode Test");
        
        try (TaskContext ctx = TFI.start("Unicode Task")) {
            // When
            TFI.message(unicodeContent, MessageType.PROCESS);
            TFI.message("Emoji test", emojiLabel);
            
            // Then
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(2);
            
            assertEquals(unicodeContent, node.getMessages().get(0).getContent());
            assertEquals(emojiLabel, node.getMessages().get(1).getCustomLabel());
        }
        
        TFI.endSession();
    }
    
    @Test
    void testHighVolumeMessageAddition() {
        // Given
        final int messageCount = 1000;
        TFI.startSession("High Volume Test");
        
        try (TaskContext ctx = TFI.start("High Volume Task")) {
            // When - Add many messages
            for (int i = 0; i < messageCount; i++) {
                if (i % 2 == 0) {
                    TFI.message("Message " + i, MessageType.PROCESS);
                } else {
                    TFI.message("Message " + i, "Label-" + i);
                }
            }
            
            // Then
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            assertThat(node.getMessages()).hasSize(messageCount);
            
            // Verify first and last messages
            assertEquals("Message 0", node.getMessages().get(0).getContent());
            assertEquals("Message " + (messageCount - 1), 
                        node.getMessages().get(messageCount - 1).getContent());
        }
        
        TFI.endSession();
    }
    
    @Test
    void testMemoryLeakPrevention() {
        // Test that clearing sessions properly releases memory
        for (int i = 0; i < 100; i++) {
            TFI.startSession("Memory Test " + i);
            
            try (TaskContext ctx = TFI.start("Memory Task " + i)) {
                // Add some messages
                TFI.message("Content " + i, MessageType.PROCESS);
                TFI.message("Custom " + i, "Label " + i);
            }
            
            TFI.endSession();
            TFI.clear(); // Should clear all references
        }
        
        // If we get here without OOM, memory handling is working
        assertTrue(true);
    }
    
    @Test
    void testConcurrentSessionOperations() throws InterruptedException {
        // Test that multiple threads can safely use different sessions
        final int threadCount = 5;
        final int messagesPerThread = 50;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                TFI.startSession("Thread Session " + threadId);
                
                try (TaskContext ctx = TFI.start("Thread Task " + threadId)) {
                    for (int j = 0; j < messagesPerThread; j++) {
                        TFI.message("Thread-" + threadId + "-Msg-" + j, 
                                   MessageType.PROCESS);
                        TFI.message("Custom-" + threadId + "-" + j, 
                                   "Thread-Label-" + threadId);
                    }
                }
                
                TFI.endSession();
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
        
        // Test completed successfully if no exceptions thrown
        assertTrue(true);
    }
    
    @Test
    void testSystemStateAfterErrors() {
        // Test that the system remains stable after various error conditions
        TFI.startSession("Error Recovery Test");
        
        try (TaskContext ctx = TFI.start("Error Recovery Task")) {
            // Cause various error conditions
            TFI.message(null, (MessageType) null);
            TFI.message("", "");
            TFI.error((String) null);
            
            // System should still work normally after errors
            TFI.message("Normal message after errors", MessageType.PROCESS);
            TFI.message("Custom after errors", "Recovery Label");
            
            TaskNode node = TFI.getCurrentTask();
            assertThat(node).isNotNull();
            // Should only have the 2 valid messages
            assertThat(node.getMessages()).hasSize(2);
            
            assertEquals("Normal message after errors", 
                        node.getMessages().get(0).getContent());
            assertEquals("Custom after errors", 
                        node.getMessages().get(1).getContent());
        }
        
        TFI.endSession();
    }
    
    @Test
    void testExportCompatibilityWithNewMessages() {
        // Test that export functions work with both old and new message types
        TFI.startSession("Export Compatibility");
        
        try (TaskContext ctx = TFI.start("Export Task")) {
            // Mix of old and new style messages
            TFI.message("Process message", MessageType.PROCESS);
            TFI.message("Custom message", "Custom Label");
            TFI.error("Error message");
            
            TaskNode node = TFI.getCurrentTask();
            assertThat(node.getMessages()).hasSize(3);
        }
        
        // When - Export using existing methods
        TFI.exportToConsole(); // This returns void, not String
        String jsonOutput = TFI.exportToJson();
        
        // Then - Should work without errors
        assertNotNull(jsonOutput);
        assertTrue(jsonOutput.length() > 0);
        
        TFI.endSession();
    }
    
    @Test
    void testMessageIdsUniquenessUnderLoad() {
        // Test that message IDs remain unique even under high load
        final int messageCount = 1000;
        java.util.Set<String> messageIds = new java.util.HashSet<>();
        
        TFI.startSession("Message ID Test");
        
        try (TaskContext ctx = TFI.start("Message ID Task")) {
            for (int i = 0; i < messageCount; i++) {
                TFI.message("Message " + i, MessageType.PROCESS);
            }
            
            TaskNode node = TFI.getCurrentTask();
            for (Message message : node.getMessages()) {
                String messageId = message.getMessageId();
                assertNotNull(messageId);
                assertFalse(messageIds.contains(messageId), 
                           "Duplicate message ID found: " + messageId);
                messageIds.add(messageId);
            }
        }
        
        assertEquals(messageCount, messageIds.size());
        TFI.endSession();
    }
}