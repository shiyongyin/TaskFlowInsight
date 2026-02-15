package com.syy.taskflowinsight.exporter.text;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConsoleExporter自定义标签显示测试
 * 测试ConsoleExporter对自定义标签的显示支持
 */
class ConsoleExporterCustomLabelTest {
    
    private ConsoleExporter exporter;
    private Session testSession;
    
    @BeforeEach
    void setUp() {
        exporter = new ConsoleExporter();
        testSession = createTestSession();
    }
    
    @Test
    void testDisplayMessageTypeLabels() {
        // Given
        Session session = Session.create("MessageType Test");
        TaskNode root = session.getRootTask();
        
        // Add messages with MessageType
        root.addMessage("Process message", MessageType.PROCESS);
        root.addMessage("Metric message", MessageType.METRIC);
        root.addMessage("Change message", MessageType.CHANGE);
        root.addMessage("Alert message", MessageType.ALERT);
        
        // When
        String output = exporter.export(session, false); // No timestamp
        
        // Then
        assertTrue(output.contains("业务流程] Process message"));
        assertTrue(output.contains("核心指标] Metric message"));
        assertTrue(output.contains("变更记录] Change message"));
        assertTrue(output.contains("异常提示] Alert message"));
    }
    
    @Test
    void testDisplayCustomLabels() {
        // Given
        Session session = Session.create("Custom Label Test");
        TaskNode root = session.getRootTask();
        
        // Add messages with custom labels
        root.addMessage("订单创建成功", "订单状态");
        root.addMessage("用户登录验证", "用户行为");
        root.addMessage("响应时间: 100ms", "性能指标");
        root.addMessage("库存不足警告", "业务异常");
        
        // When
        String output = exporter.export(session, false);
        
        // Then
        assertTrue(output.contains("[订单状态] 订单创建成功"));
        assertTrue(output.contains("[用户行为] 用户登录验证"));
        assertTrue(output.contains("[性能指标] 响应时间: 100ms"));
        assertTrue(output.contains("[业务异常] 库存不足警告"));
    }
    
    @Test
    void testMixedMessageTypesAndCustomLabels() {
        // Given
        Session session = Session.create("Mixed Test");
        TaskNode root = session.getRootTask();
        
        // Add mixed message types
        root.addMessage("开始处理", MessageType.PROCESS);
        root.addMessage("订单ID: 12345", "订单信息");
        root.addMessage("验证通过", MessageType.METRIC);
        root.addMessage("支付成功", "支付状态");
        root.addMessage("异常警告", MessageType.ALERT);
        
        // When
        String output = exporter.export(session, false);
        
        // Then
        assertTrue(output.contains("业务流程] 开始处理"));
        assertTrue(output.contains("订单信息] 订单ID: 12345"));
        assertTrue(output.contains("核心指标] 验证通过"));
        assertTrue(output.contains("支付状态] 支付成功"));
        assertTrue(output.contains("异常提示] 异常警告"));
    }
    
    @Test
    void testCustomLabelsWithTimestamp() {
        // Given
        Session session = Session.create("Timestamp Test");
        TaskNode root = session.getRootTask();
        root.addMessage("时间戳消息", "时间测试");
        
        // When
        String output = exporter.export(session, true); // With timestamp
        
        // Then
        assertTrue(output.contains("[时间测试 @"));
        assertTrue(output.contains("] 时间戳消息"));
        // Should contain timestamp in format like "2025-09-09T..."
        assertTrue(output.matches("(?s).*\\[时间测试 @\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z\\] 时间戳消息.*"));
    }
    
    @Test
    void testNestedTasksWithCustomLabels() {
        // Given
        Session session = Session.create("Nested Test");
        TaskNode root = session.getRootTask();
        TaskNode child = root.createChild("Child Task");
        TaskNode grandChild = child.createChild("Grand Child Task");
        
        root.addMessage("根任务消息", "根任务标签");
        child.addMessage("子任务消息", MessageType.PROCESS);
        grandChild.addMessage("孙任务消息", "孙任务标签");
        
        // When
        String output = exporter.export(session, false);
        
        // Then
        // Verify nested structure with proper labels
        assertTrue(output.contains("Nested Test"));
        assertTrue(output.contains("Child Task"));
        assertTrue(output.contains("Grand Child Task"));
        assertTrue(output.contains("[根任务标签] 根任务消息"));
        assertTrue(output.contains("[业务流程] 子任务消息"));
        assertTrue(output.contains("[孙任务标签] 孙任务消息"));
    }
    
    @Test
    void testSpecialCharactersInCustomLabels() {
        // Given
        Session session = Session.create("Special Chars Test");
        TaskNode root = session.getRootTask();
        
        // Add messages with special characters in labels
        root.addMessage("消息内容1", "标签[特殊]字符");
        root.addMessage("消息内容2", "标签@#$%符号");
        root.addMessage("消息内容3", "标签With英文Mixed");
        root.addMessage("消息内容4", "标签123数字");
        
        // When
        String output = exporter.export(session, false);
        
        // Then
        assertTrue(output.contains("[标签[特殊]字符] 消息内容1"));
        assertTrue(output.contains("[标签@#$%符号] 消息内容2"));
        assertTrue(output.contains("[标签With英文Mixed] 消息内容3"));
        assertTrue(output.contains("[标签123数字] 消息内容4"));
    }
    
    @Test
    void testLongCustomLabels() {
        // Given
        Session session = Session.create("Long Label Test");
        TaskNode root = session.getRootTask();
        
        String longLabel = "这是一个非常非常长的自定义标签名称用来测试显示效果";
        root.addMessage("长标签消息", longLabel);
        
        // When
        String output = exporter.export(session, false);
        
        // Then
        assertTrue(output.contains("[" + longLabel + "] 长标签消息"));
    }
    
    @Test
    void testEmptySession() {
        // Given
        Session emptySession = Session.create("Empty Session");
        // No messages added
        
        // When
        String output = exporter.export(emptySession, false);
        
        // Then
        assertTrue(output.contains("Empty Session"));
        assertFalse(output.contains("|-")); // No message indicators
    }
    
    @Test
    void testNullSession() {
        // When
        String output = exporter.export(null, false);
        
        // Then
        assertEquals("", output);
    }
    
    @Test
    void testMultipleMessagesInSingleTask() {
        // Given
        Session session = Session.create("Multiple Messages Test");
        TaskNode root = session.getRootTask();
        
        // Add multiple messages with different label types
        root.addMessage("第一条消息", MessageType.PROCESS);
        root.addMessage("第二条消息", "自定义标签1");
        root.addMessage("第三条消息", MessageType.METRIC);
        root.addMessage("第四条消息", "自定义标签2");
        root.addMessage("第五条消息", MessageType.ALERT);
        
        // When
        String output = exporter.export(session, false);
        
        // Then
        // Verify all messages are displayed in order
        String[] lines = output.split("\n");
        int messageCount = 0;
        for (String line : lines) {
            if (line.contains("|-")) {
                messageCount++;
            }
        }
        assertEquals(5, messageCount);
        
        assertTrue(output.contains("业务流程] 第一条消息"));
        assertTrue(output.contains("自定义标签1] 第二条消息"));
        assertTrue(output.contains("核心指标] 第三条消息"));
        assertTrue(output.contains("自定义标签2] 第四条消息"));
        assertTrue(output.contains("异常提示] 第五条消息"));
    }
    
    @Test
    void testIndentationWithCustomLabels() {
        // Given
        Session session = Session.create("Indentation Test");
        TaskNode root = session.getRootTask();
        TaskNode child = root.createChild("Child");
        TaskNode grandChild = child.createChild("GrandChild");
        
        root.addMessage("Root message", "Root Label");
        child.addMessage("Child message", "Child Label"); 
        grandChild.addMessage("GrandChild message", "GrandChild Label");
        
        // When
        String output = exporter.export(session, false);
        
        // Then
        String[] lines = output.split("\n");
        
        // Find message lines and verify indentation
        for (String line : lines) {
            if (line.contains("[Root Label]")) {
                assertTrue(line.startsWith("    |-")); // Root task indentation
            } else if (line.contains("[Child Label]")) {
                assertTrue(line.startsWith("        |-")); // Child task indentation
            } else if (line.contains("[GrandChild Label]")) {
                assertTrue(line.startsWith("            |-")); // GrandChild indentation
            }
        }
    }
    
    @Test
    void testSessionStatusInOutput() {
        // Given
        Session session = Session.create("Status Test");
        session.getRootTask().addMessage("测试消息", "状态测试");
        session.complete(); // Complete the session
        
        // When
        String output = exporter.export(session, false);
        
        // Then
        assertTrue(output.contains("Status:  COMPLETED"));
        assertTrue(output.contains("[状态测试] 测试消息"));
    }
    
    @Test
    void testOutputFormat() {
        // Given
        Session session = Session.create("Format Test");
        TaskNode root = session.getRootTask();
        root.addMessage("格式测试消息", "格式标签");
        
        // When
        String output = exporter.export(session, false);
        
        // Then
        // Verify basic output structure
        assertTrue(output.startsWith("="));  // Header separator
        assertTrue(output.contains("TaskFlow Insight Report"));
        assertTrue(output.contains("Session: " + session.getSessionId()));
        assertTrue(output.contains("Format Test"));
        assertTrue(output.contains("[格式标签] 格式测试消息"));
        assertTrue(output.endsWith("=".repeat(50) + "\n")); // Footer separator
    }
    
    private Session createTestSession() {
        Session session = Session.create("Test Session");
        TaskNode root = session.getRootTask();
        
        root.addMessage("Test message 1", MessageType.PROCESS);
        root.addMessage("Test message 2", "Custom Label");
        
        TaskNode child = root.createChild("Child Task");
        child.addMessage("Child message", "Child Label");
        
        return session;
    }
}