package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 变更消息格式验证测试
 * 验证变更消息符合标准格式: Object.field: old → new
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
class MessageFormatIT {
    
    // 标准格式正则: Object.field: old → new（不带类型前缀）
    private static final Pattern CHANGE_MESSAGE_PATTERN =
        Pattern.compile("^([\\w.$]+)\\.([\\w.$]+): (.+) \u2192 (.+)$");
    
    static class TestEntity {
        private String name;
        private Integer age;
        private Boolean active;
        private Date lastModified;
        private String description;
        
        public TestEntity(String name, Integer age, Boolean active) {
            this.name = name;
            this.age = age;
            this.active = active;
            this.lastModified = new Date();
        }
        
        public void setName(String name) { this.name = name; }
        public void setAge(Integer age) { this.age = age; }
        public void setActive(Boolean active) { this.active = active; }
        public void setLastModified(Date lastModified) { this.lastModified = lastModified; }
        public void setDescription(String description) { this.description = description; }
    }
    
    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        TFI.clear();
    }
    
    @AfterEach
    void tearDown() {
        TFI.endSession();
        TFI.clear();
    }
    
    @Test
    void testBasicMessageFormat() {
        // Given
        TFI.startSession("FormatTestSession");
        TFI.start("FormatTest");
        
        TestEntity entity = new TestEntity("Alice", 25, true);
        TFI.track("User", entity, "name", "age", "active");
        
        // When - 修改各种类型的字段
        entity.setName("Bob");
        entity.setAge(30);
        entity.setActive(false);
        TFI.stop();
        
        // Then - 验证消息格式
        Session session = TFI.getCurrentSession();
        TaskNode root = session.getRootTask();
        List<String> changeMessages = getAllMessages(root);
        
        assertEquals(3, changeMessages.size(), "Should have 3 change messages");
        
        // 验证每个消息符合格式
        for (String message : changeMessages) {
            assertTrue(CHANGE_MESSAGE_PATTERN.matcher(message).matches(),
                "Message should match pattern: " + message);
            assertTrue(message.contains("User."));
            assertTrue(message.contains(" → "));
        }
        
        // 验证具体内容
        assertTrue(changeMessages.stream().anyMatch(m -> m.contains("User.name: Alice → Bob")));
        assertTrue(changeMessages.stream().anyMatch(m -> m.contains("User.age: 25 → 30")));
        assertTrue(changeMessages.stream().anyMatch(m -> m.contains("User.active: true → false")));
    }
    
    @Test
    void testNullValueFormat() {
        // Given
        TFI.startSession("NullFormatSession");
        TFI.start("NullTest");
        
        TestEntity entity = new TestEntity(null, null, null);
        TFI.track("Entity", entity, "name", "age", "active", "description");
        
        // When - null → value
        entity.setName("John");
        entity.setAge(40);
        entity.setActive(true);
        TFI.stop();
        
        // Then
        List<String> changeMessages = getAllMessages(TFI.getCurrentSession().getRootTask());
        
        // 验证null值显示为"null"
        assertTrue(changeMessages.stream().anyMatch(m -> 
            m.matches(".*\\.name: null → John$")));
        assertTrue(changeMessages.stream().anyMatch(m -> 
            m.matches(".*\\.age: null → 40$")));
        assertTrue(changeMessages.stream().anyMatch(m -> 
            m.matches(".*\\.active: null → true$")));
        
        // 新会话测试 value → null
        TFI.startSession("NullFormat2Session");
        TFI.start("NullTest2");
        
        TestEntity entity2 = new TestEntity("Jane", 35, false);
        TFI.track("Entity", entity2, "name", "age", "active");
        
        entity2.setName(null);
        entity2.setAge(null);
        entity2.setActive(null);
        TFI.stop();
        
        List<String> changeMessages2 = getAllMessages(TFI.getCurrentSession().getRootTask());
        
        assertTrue(changeMessages2.stream().anyMatch(m -> 
            m.matches(".*\\.name: Jane → null$")));
        assertTrue(changeMessages2.stream().anyMatch(m -> 
            m.matches(".*\\.age: 35 → null$")));
        assertTrue(changeMessages2.stream().anyMatch(m -> 
            m.matches(".*\\.active: false → null$")));
    }
    
    @Test
    void testSpecialCharacterEscaping() {
        // Given
        TFI.startSession("EscapeSession");
        TFI.start("EscapeTest");
        
        TestEntity entity = new TestEntity("Normal", 20, true);
        entity.setDescription("Line 1");
        TFI.track("Entity", entity, "description");
        
        // When - 设置包含特殊字符的值
        entity.setDescription("Line 1\nLine 2\tTabbed\r\nWindows");
        TFI.stop();
        
        // Then
        List<String> changeMessages = getAllMessages(TFI.getCurrentSession().getRootTask());
        
        // 验证换行符被转义
        assertTrue(changeMessages.stream().anyMatch(m -> 
            m.contains("Line 1 → Line 1\\nLine 2\\tTabbed\\r\\nWindows")),
            "Special characters should be escaped");
        
        // 测试引号转义
        TFI.startSession("QuoteSession");
        TFI.start("QuoteTest");
        
        TestEntity entity2 = new TestEntity("Quote Test", 25, true);
        TFI.track("Entity", entity2, "name");
        
        entity2.setName("He said \"Hello\"");
        TFI.stop();
        
        List<String> changeMessages2 = getAllMessages(TFI.getCurrentSession().getRootTask());
        assertTrue(changeMessages2.stream().anyMatch(m -> 
            m.contains("Quote Test → He said \\\"Hello\\\"")),
            "Quotes should be escaped");
    }
    
    @Test
    void testLongValueTruncation() {
        // Given
        TFI.startSession("TruncateSession");
        TFI.start("TruncateTest");
        
        TestEntity entity = new TestEntity("Short", 30, true);
        TFI.track("Entity", entity, "name");
        
        // When - 设置超长字符串
        String longString = "a".repeat(10000);
        entity.setName(longString);
        TFI.stop();
        
        // Then
        List<String> changeMessages = getAllMessages(TFI.getCurrentSession().getRootTask());
        
        // 验证值被截断到8192字符，且以 suffix 结尾
        String message = changeMessages.stream()
            .filter(m -> m.contains("Entity.name:"))
            .findFirst()
            .orElse("");
        
        assertFalse(message.isEmpty());
        
        // 提取new value部分
        String[] parts = message.split(" → ");
        assertTrue(parts.length == 2);
        
        String newValue = parts[1];
        assertEquals(8192, newValue.length());
        assertTrue(newValue.endsWith("... (truncated)"));
    }
    
    @Test
    void testDateFormat() {
        // Given
        TFI.startSession("DateSession");
        TFI.start("DateTest");
        
        Date oldDate = new Date(1000000);
        Date newDate = new Date(2000000);
        
        TestEntity entity = new TestEntity("DateTest", 25, true);
        entity.setLastModified(oldDate);
        TFI.track("Entity", entity, "lastModified");
        
        // When
        entity.setLastModified(newDate);
        TFI.stop();
        
        // Then
        List<String> changeMessages = getAllMessages(TFI.getCurrentSession().getRootTask());
        
        // Date应该显示为时间戳
        assertTrue(changeMessages.stream().anyMatch(m -> 
            m.matches("^Entity\\.lastModified: " + oldDate.getTime() + " \\u2192 " + newDate.getTime() + "$")),
            "Dates should be displayed as timestamps");
    }
    
    @Test
    void testConsoleAndJsonConsistency() {
        // Given
        TFI.startSession("ConsistencySession");
        TFI.start("ConsistencyTest");
        
        TestEntity entity = new TestEntity("Alice", 25, true);
        TFI.track("User", entity, "name", "age");
        
        entity.setName("Bob");
        entity.setAge(30);
        TFI.stop();
        
        // When - 获取JSON输出（Console输出直接打印到标准输出）
        String jsonOutput = TFI.exportToJson();
        
        // Then - JSON应该包含正确格式的变更消息
        assertNotNull(jsonOutput);
        assertTrue(jsonOutput.contains("User.name: Alice → Bob"));
        assertTrue(jsonOutput.contains("User.age: 25 → 30"));
        
        // 验证消息在Session中的格式
        Session session = TFI.getCurrentSession();
        List<String> changeMessages = getAllMessages(session.getRootTask());
        
        assertTrue(changeMessages.stream().anyMatch(m -> m.contains("User.name: Alice → Bob")));
        assertTrue(changeMessages.stream().anyMatch(m -> m.contains("User.age: 25 → 30")));
    }
    
    @Test
    void testMultipleObjectsFormat() {
        // Given
        TFI.startSession("MultiObjectSession");
        TFI.start("MultiTest");
        
        TestEntity user = new TestEntity("User1", 20, true);
        TestEntity admin = new TestEntity("Admin1", 30, false);
        
        TFI.track("User", user, "name", "age");
        TFI.track("Admin", admin, "name", "active");
        
        // When
        user.setName("User2");
        user.setAge(21);
        admin.setName("Admin2");
        admin.setActive(true);
        TFI.stop();
        
        // Then
        List<String> changeMessages = getAllMessages(TFI.getCurrentSession().getRootTask());
        
        // 验证不同对象的消息格式正确
        assertTrue(changeMessages.stream().anyMatch(m -> m.matches("^User\\.name: User1 \\u2192 User2$")));
        assertTrue(changeMessages.stream().anyMatch(m -> m.matches("^User\\.age: 20 \\u2192 21$")));
        assertTrue(changeMessages.stream().anyMatch(m -> m.matches("^Admin\\.name: Admin1 \\u2192 Admin2$")));
        assertTrue(changeMessages.stream().anyMatch(m -> m.matches("^Admin\\.active: false \\u2192 true$")));
    }
    
    private List<String> getAllMessages(TaskNode node) {
        assertNotNull(node);
        List<String> all = new java.util.ArrayList<>();
        for (Message m : node.getMessages()) {
            all.add(m.getContent());
        }
        for (TaskNode child : node.getChildren()) {
            all.addAll(getAllMessages(child));
        }
        return all.stream().filter(s -> s.contains(" → ")).toList();
    }
}
