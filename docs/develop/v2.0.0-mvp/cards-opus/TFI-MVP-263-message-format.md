# TFI-MVP-263 消息格式测试

## 任务概述
验证变更消息格式符合PRD规定的标准格式：`<Object>.<field>: <old> → <new>`，确保截断规范和特殊字符处理的正确性。

## 核心目标
- [ ] 验证标准格式：`<Object>.<field>: <old> → <new>`
- [ ] 验证包含CHANGE标签
- [ ] 测试截断规范（先转义后截断）
- [ ] 验证默认上限8192字符
- [ ] 确认超出以`... (truncated)`结尾
- [ ] 验证空值以`null`表示

## 实现清单

### 1. 消息格式测试主类
```java
package com.syy.taskflowinsight.core.format;

import com.syy.taskflowinsight.core.TFI;
import com.syy.taskflowinsight.core.ChangeTracker;
import com.syy.taskflowinsight.core.ChangeRecord;
import com.syy.taskflowinsight.core.TaskNode;
import com.syy.taskflowinsight.core.message.TaskMessage;
import com.syy.taskflowinsight.core.message.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("变更消息格式测试")
class MessageFormatTest {
    
    // 标准格式正则表达式
    private static final Pattern CHANGE_MESSAGE_PATTERN = Pattern.compile(
        "^[A-Za-z0-9_$.]+\\.[A-Za-z0-9_$.]+: .+ \u2192 .+$"
    );
    
    @BeforeEach
    void setUp() {
        TFI.endSession();
    }
    
    @AfterEach
    void tearDown() {
        TFI.endSession();
    }
    
    @Test
    @DisplayName("标准消息格式测试")
    void testStandardMessageFormat() {
        ChangeTracker tracker = TFI.start("format-test");
        
        try {
            TestObject obj = new TestObject("TestObject");
            tracker.track("testObj", obj);
            
            // 修改对象
            obj.setName("John");
            obj.setAge(30);
            obj.setActive(true);
            
            // 获取当前TaskNode的消息
            TaskNode currentNode = TFI.getCurrentTaskNode();
            assertNotNull(currentNode, "应该有当前TaskNode");
            
            List<TaskMessage> messages = currentNode.getMessages();
            
            // 过滤出CHANGE类型的消息
            List<TaskMessage> changeMessages = messages.stream()
                .filter(msg -> msg.getType() == MessageType.CHANGE)
                .toList();
            
            assertEquals(3, changeMessages.size(), "应该有3个CHANGE消息");
            
            // 验证每个变更消息的格式
            for (TaskMessage message : changeMessages) {
                String content = message.getContent();
                
                // 验证消息类型
                assertEquals(MessageType.CHANGE, message.getType(), 
                    "消息类型应该是CHANGE");
                
                // 验证格式匹配正则表达式
                assertTrue(CHANGE_MESSAGE_PATTERN.matcher(content).matches(), 
                    "消息格式不符合标准: " + content);
                
                // 验证包含箭头符号
                assertTrue(content.contains(" \u2192 "), 
                    "消息应该包含箭头符号: " + content);
                
                // 验证对象和字段分隔符
                assertTrue(content.contains("."), 
                    "消息应该包含对象.字段格式: " + content);
                
                System.out.println("标准格式消息: " + content);
            }
            
        } finally {
            TFI.stop();
        }
    }
    
    @Test
    @DisplayName("各种数据类型格式测试")
    void testVariousDataTypeFormats() {
        ChangeTracker tracker = TFI.start("data-type-format-test");
        
        try {
            TypeTestObject obj = new TypeTestObject();
            tracker.track("typeObj", obj);
            
            // 修改各种类型的字段
            obj.setStringField("Hello World");
            obj.setIntField(42);
            obj.setDoubleField(3.14159);
            obj.setBooleanField(true);
            obj.setDateField(new java.util.Date());
            
            TaskNode currentNode = TFI.getCurrentTaskNode();
            List<TaskMessage> changeMessages = currentNode.getMessages().stream()
                .filter(msg -> msg.getType() == MessageType.CHANGE)
                .toList();
            
            assertEquals(5, changeMessages.size(), "应该有5个类型变更消息");
            
            for (TaskMessage message : changeMessages) {
                String content = message.getContent();
                
                // 验证基本格式
                assertTrue(CHANGE_MESSAGE_PATTERN.matcher(content).matches(), 
                    "消息格式不符合标准: " + content);
                
                // 根据字段类型验证特定格式
                if (content.contains("stringField")) {
                    assertTrue(content.contains("Hello World"), 
                        "String字段格式错误: " + content);
                } else if (content.contains("intField")) {
                    assertTrue(content.contains("42"), 
                        "Integer字段格式错误: " + content);
                } else if (content.contains("doubleField")) {
                    assertTrue(content.contains("3.14159"), 
                        "Double字段格式错误: " + content);
                } else if (content.contains("booleanField")) {
                    assertTrue(content.contains("true"), 
                        "Boolean字段格式错误: " + content);
                }
                
                System.out.println("类型格式消息: " + content);
            }
            
        } finally {
            TFI.stop();
        }
    }
    
    @Test
    @DisplayName("null值格式测试")
    void testNullValueFormat() {
        ChangeTracker tracker = TFI.start("null-format-test");
        
        try {
            NullTestObject obj = new NullTestObject();
            tracker.track("nullObj", obj);
            
            // null -> value
            obj.setNullableField("not null anymore");
            
            // value -> null
            obj.setInitialField(null);
            
            TaskNode currentNode = TFI.getCurrentTaskNode();
            List<TaskMessage> changeMessages = currentNode.getMessages().stream()
                .filter(msg -> msg.getType() == MessageType.CHANGE)
                .toList();
            
            assertEquals(2, changeMessages.size(), "应该有2个null相关变更消息");
            
            for (TaskMessage message : changeMessages) {
                String content = message.getContent();
                
                assertTrue(CHANGE_MESSAGE_PATTERN.matcher(content).matches(), 
                    "null值消息格式不符合标准: " + content);
                
                if (content.contains("nullableField")) {
                    assertTrue(content.contains("null \u2192"), 
                        "null->value格式错误: " + content);
                    assertTrue(content.contains("not null anymore"), 
                        "null->value内容错误: " + content);
                } else if (content.contains("initialField")) {
                    assertTrue(content.contains("\u2192 null"), 
                        "value->null格式错误: " + content);
                    assertTrue(content.contains("initial"), 
                        "value->null原值错误: " + content);
                }
                
                System.out.println("null值格式消息: " + content);
            }
            
        } finally {
            TFI.stop();
        }
    }
    
    @ParameterizedTest
    @DisplayName("特殊字符转义格式测试")
    @ValueSource(strings = {
        "Line1\nLine2",           // 换行符
        "Col1\tCol2",             // 制表符  
        "Say \"Hello\"",          // 双引号
        "Path\\to\\file",         // 反斜杠
        "Multi\r\nLine",          // 回车换行
        "Mixed\n\t\"\\Special"    // 混合特殊字符
    })
    void testSpecialCharacterEscaping(String specialValue) {
        ChangeTracker tracker = TFI.start("special-char-test");
        
        try {
            SpecialCharObject obj = new SpecialCharObject();
            tracker.track("specialObj", obj);
            
            obj.setSpecialField(specialValue);
            
            TaskNode currentNode = TFI.getCurrentTaskNode();
            List<TaskMessage> changeMessages = currentNode.getMessages().stream()
                .filter(msg -> msg.getType() == MessageType.CHANGE)
                .toList();
            
            assertEquals(1, changeMessages.size(), "应该有1个特殊字符变更消息");
            
            TaskMessage message = changeMessages.get(0);
            String content = message.getContent();
            
            // 验证基本格式
            assertTrue(CHANGE_MESSAGE_PATTERN.matcher(content).matches(), 
                "特殊字符消息格式不符合标准: " + content);
            
            // 验证转义处理
            if (specialValue.contains("\n")) {
                assertTrue(content.contains("\\n"), 
                    "换行符应该被转义: " + content);
                assertFalse(content.contains("\n"), 
                    "不应该包含未转义的换行符");
            }
            
            if (specialValue.contains("\t")) {
                assertTrue(content.contains("\\t"), 
                    "制表符应该被转义: " + content);
            }
            
            if (specialValue.contains("\"")) {
                assertTrue(content.contains("\\\""), 
                    "双引号应该被转义: " + content);
            }
            
            if (specialValue.contains("\\")) {
                assertTrue(content.contains("\\\\"), 
                    "反斜杠应该被转义: " + content);
            }
            
            if (specialValue.contains("\r")) {
                assertTrue(content.contains("\\r"), 
                    "回车符应该被转义: " + content);
            }
            
            System.out.println("特殊字符格式消息: " + content);
            
        } finally {
            TFI.stop();
        }
    }
    
    @Test
    @DisplayName("长字符串截断格式测试")
    void testLongStringTruncationFormat() {
        ChangeTracker tracker = TFI.start("truncation-format-test");
        
        try {
            LongStringObject obj = new LongStringObject();
            tracker.track("longObj", obj);
            
            // 创建超长字符串（超过8192字符）
            String veryLongString = "x".repeat(15000);
            obj.setLongField(veryLongString);
            
            TaskNode currentNode = TFI.getCurrentTaskNode();
            List<TaskMessage> changeMessages = currentNode.getMessages().stream()
                .filter(msg -> msg.getType() == MessageType.CHANGE)
                .toList();
            
            assertEquals(1, changeMessages.size(), "应该有1个长字符串变更消息");
            
            TaskMessage message = changeMessages.get(0);
            String content = message.getContent();
            
            // 验证基本格式
            assertTrue(CHANGE_MESSAGE_PATTERN.matcher(content).matches(), 
                "长字符串消息格式不符合标准: " + content);
            
            // 验证截断
            assertTrue(content.length() <= 8192 + 100, // 留一些格式字符的余量
                "消息长度应该被截断: " + content.length());
            
            // 验证截断标记
            assertTrue(content.contains("... (truncated)"), 
                "应该包含截断标记: " + content);
            
            // 验证截断标记在正确位置（在箭头后的新值部分）
            String[] parts = content.split(" \u2192 ");
            assertEquals(2, parts.length, "应该有箭头分隔的两部分");
            assertTrue(parts[1].endsWith("... (truncated)"), 
                "截断标记应该在新值部分的末尾");
            
            System.out.println("截断格式消息长度: " + content.length());
            System.out.println("截断格式消息样本: " + 
                content.substring(0, Math.min(200, content.length())) + "...");
            
        } finally {
            TFI.stop();
        }
    }
    
    @Test
    @DisplayName("先转义后截断顺序测试")
    void testEscapeBeforeTruncationOrder() {
        ChangeTracker tracker = TFI.start("escape-truncate-order-test");
        
        try {
            EscapeTruncateObject obj = new EscapeTruncateObject();
            tracker.track("escapeObj", obj);
            
            // 创建包含特殊字符的超长字符串
            StringBuilder longSpecialString = new StringBuilder();
            for (int i = 0; i < 2000; i++) {
                longSpecialString.append("Line").append(i).append("\n");
                longSpecialString.append("Quote\"").append(i).append("\t");
                longSpecialString.append("Slash\\").append(i).append("\r");
            }
            
            obj.setEscapeField(longSpecialString.toString());
            
            TaskNode currentNode = TFI.getCurrentTaskNode();
            List<TaskMessage> changeMessages = currentNode.getMessages().stream()
                .filter(msg -> msg.getType() == MessageType.CHANGE)
                .toList();
            
            assertEquals(1, changeMessages.size(), "应该有1个转义截断变更消息");
            
            TaskMessage message = changeMessages.get(0);
            String content = message.getContent();
            
            // 验证基本格式
            assertTrue(CHANGE_MESSAGE_PATTERN.matcher(content).matches(), 
                "转义截断消息格式不符合标准: " + content);
            
            // 验证先转义后截断：转义字符应该存在
            assertTrue(content.contains("\\n"), 
                "应该包含转义的换行符");
            assertTrue(content.contains("\\\""), 
                "应该包含转义的双引号");
            assertTrue(content.contains("\\\\"), 
                "应该包含转义的反斜杠");
            assertTrue(content.contains("\\r"), 
                "应该包含转义的回车符");
            assertTrue(content.contains("\\t"), 
                "应该包含转义的制表符");
            
            // 验证截断标记
            assertTrue(content.endsWith("... (truncated)"), 
                "应该以截断标记结尾");
            
            // 验证总长度限制
            assertTrue(content.length() <= 8192 + 100, 
                "总长度应该受限: " + content.length());
            
            System.out.println("转义截断消息长度: " + content.length());
            
        } finally {
            TFI.stop();
        }
    }
    
    @Test
    @DisplayName("复杂对象路径格式测试")
    void testComplexObjectPathFormat() {
        ChangeTracker tracker = TFI.start("complex-path-test");
        
        try {
            ComplexPathObject obj = new ComplexPathObject();
            tracker.track("complexObj", obj);
            
            // 修改各种命名格式的字段
            obj.setSimpleField("simple");
            obj.setCamelCaseField("camelCase");
            obj.setSnake_case_field("snake_case");
            obj.set$dollarField("dollar");
            obj.setField123("numeric");
            
            TaskNode currentNode = TFI.getCurrentTaskNode();
            List<TaskMessage> changeMessages = currentNode.getMessages().stream()
                .filter(msg -> msg.getType() == MessageType.CHANGE)
                .toList();
            
            assertEquals(5, changeMessages.size(), "应该有5个复杂路径变更消息");
            
            for (TaskMessage message : changeMessages) {
                String content = message.getContent();
                
                // 验证基本格式
                assertTrue(CHANGE_MESSAGE_PATTERN.matcher(content).matches(), 
                    "复杂路径消息格式不符合标准: " + content);
                
                // 验证路径格式
                assertTrue(content.startsWith("complexObj."), 
                    "应该以对象名开头: " + content);
                
                // 验证各种字段命名格式都被正确处理
                boolean validFieldName = content.contains(".simpleField:") ||
                                       content.contains(".camelCaseField:") ||
                                       content.contains(".snake_case_field:") ||
                                       content.contains(".$dollarField:") ||
                                       content.contains(".field123:");
                
                assertTrue(validFieldName, 
                    "应该包含有效的字段名: " + content);
                
                System.out.println("复杂路径格式消息: " + content);
            }
            
        } finally {
            TFI.stop();
        }
    }
    
    @Test
    @DisplayName("Console和JSON导出格式一致性测试")
    void testConsoleJsonFormatConsistency() {
        ChangeTracker tracker = TFI.start("format-consistency-test");
        
        try {
            ConsistencyTestObject obj = new ConsistencyTestObject();
            tracker.track("consistencyObj", obj);
            
            obj.setTestField("consistency test value");
            
            TaskNode currentNode = TFI.getCurrentTaskNode();
            List<TaskMessage> changeMessages = currentNode.getMessages().stream()
                .filter(msg -> msg.getType() == MessageType.CHANGE)
                .toList();
            
            assertEquals(1, changeMessages.size(), "应该有1个一致性测试变更消息");
            
            TaskMessage message = changeMessages.get(0);
            String messageContent = message.getContent();
            
            // 验证消息格式
            assertTrue(CHANGE_MESSAGE_PATTERN.matcher(messageContent).matches(), 
                "一致性测试消息格式不符合标准: " + messageContent);
            
            // 获取变更记录（用于JSON导出）
            List<ChangeRecord> changes = tracker.getChanges();
            assertEquals(1, changes.size(), "应该有1个变更记录");
            
            ChangeRecord change = changes.get(0);
            
            // 验证Console消息和变更记录的一致性
            assertTrue(messageContent.contains(change.getFieldPath()), 
                "消息应该包含字段路径");
            assertTrue(messageContent.contains(change.getOldValueRepr()), 
                "消息应该包含旧值");
            assertTrue(messageContent.contains(change.getNewValueRepr()), 
                "消息应该包含新值");
            
            // 构造预期的消息格式
            String expectedFormat = String.format("%s: %s \u2192 %s", 
                change.getFieldPath(), 
                change.getOldValueRepr(), 
                change.getNewValueRepr());
            
            assertEquals(expectedFormat, messageContent, 
                "Console消息应该与变更记录格式一致");
            
            System.out.println("一致性验证 - Console消息: " + messageContent);
            System.out.println("一致性验证 - 变更记录: " + expectedFormat);
            
        } finally {
            TFI.stop();
        }
    }
    
    // 测试数据类
    private static class TestObject {
        private String objectName;
        private String name = "initial";
        private Integer age = 0;
        private Boolean active = false;
        
        public TestObject(String objectName) {
            this.objectName = objectName;
        }
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }
    
    private static class TypeTestObject {
        private String stringField = "initial";
        private Integer intField = 0;
        private Double doubleField = 0.0;
        private Boolean booleanField = false;
        private java.util.Date dateField = new java.util.Date(0);
        
        // Getters and Setters
        public String getStringField() { return stringField; }
        public void setStringField(String stringField) { this.stringField = stringField; }
        public Integer getIntField() { return intField; }
        public void setIntField(Integer intField) { this.intField = intField; }
        public Double getDoubleField() { return doubleField; }
        public void setDoubleField(Double doubleField) { this.doubleField = doubleField; }
        public Boolean getBooleanField() { return booleanField; }
        public void setBooleanField(Boolean booleanField) { this.booleanField = booleanField; }
        public java.util.Date getDateField() { return dateField; }
        public void setDateField(java.util.Date dateField) { this.dateField = dateField; }
    }
    
    private static class NullTestObject {
        private String nullableField = null;
        private String initialField = "initial";
        
        public String getNullableField() { return nullableField; }
        public void setNullableField(String nullableField) { this.nullableField = nullableField; }
        public String getInitialField() { return initialField; }
        public void setInitialField(String initialField) { this.initialField = initialField; }
    }
    
    private static class SpecialCharObject {
        private String specialField = "normal";
        
        public String getSpecialField() { return specialField; }
        public void setSpecialField(String specialField) { this.specialField = specialField; }
    }
    
    private static class LongStringObject {
        private String longField = "short";
        
        public String getLongField() { return longField; }
        public void setLongField(String longField) { this.longField = longField; }
    }
    
    private static class EscapeTruncateObject {
        private String escapeField = "normal";
        
        public String getEscapeField() { return escapeField; }
        public void setEscapeField(String escapeField) { this.escapeField = escapeField; }
    }
    
    private static class ComplexPathObject {
        private String simpleField = "initial";
        private String camelCaseField = "initial";
        private String snake_case_field = "initial";
        private String $dollarField = "initial";
        private String field123 = "initial";
        
        // Getters and Setters
        public String getSimpleField() { return simpleField; }
        public void setSimpleField(String simpleField) { this.simpleField = simpleField; }
        public String getCamelCaseField() { return camelCaseField; }
        public void setCamelCaseField(String camelCaseField) { this.camelCaseField = camelCaseField; }
        public String getSnake_case_field() { return snake_case_field; }
        public void setSnake_case_field(String snake_case_field) { this.snake_case_field = snake_case_field; }
        public String get$dollarField() { return $dollarField; }
        public void set$dollarField(String $dollarField) { this.$dollarField = $dollarField; }
        public String getField123() { return field123; }
        public void setField123(String field123) { this.field123 = field123; }
    }
    
    private static class ConsistencyTestObject {
        private String testField = "initial";
        
        public String getTestField() { return testField; }
        public void setTestField(String testField) { this.testField = testField; }
    }
}
```

### 2. 消息格式工具测试
```java
package com.syy.taskflowinsight.core.format;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("消息格式工具测试")
class MessageFormatterTest {
    
    @Test
    @DisplayName("标准消息构造测试")
    void testStandardMessageConstruction() {
        String objectName = "TestObject";
        String fieldName = "fieldName";
        String oldValue = "oldValue";
        String newValue = "newValue";
        
        String message = MessageFormatter.formatChangeMessage(
            objectName, fieldName, oldValue, newValue);
        
        String expected = "TestObject.fieldName: oldValue \u2192 newValue";
        assertEquals(expected, message);
    }
    
    @ParameterizedTest
    @DisplayName("各种值类型格式化测试")
    @CsvSource({
        "null, 'test', 'null', 'test'",
        "'test', null, 'test', 'null'", 
        "null, null, 'null', 'null'",
        "'', 'value', '', 'value'",
        "'value', '', 'value', ''"
    })
    void testValueTypeFormatting(String oldValue, String newValue, 
                               String expectedOld, String expectedNew) {
        String message = MessageFormatter.formatChangeMessage(
            "Object", "field", oldValue, newValue);
        
        String expected = String.format("Object.field: %s \u2192 %s", 
            expectedOld, expectedNew);
        assertEquals(expected, message);
    }
    
    // MessageFormatter类的实现应该在实际项目中
    private static class MessageFormatter {
        public static String formatChangeMessage(String objectName, String fieldName, 
                                               String oldValue, String newValue) {
            String oldRepr = oldValue == null ? "null" : oldValue;
            String newRepr = newValue == null ? "null" : newValue;
            return String.format("%s.%s: %s \u2192 %s", 
                objectName, fieldName, oldRepr, newRepr);
        }
    }
}
```

## 验证步骤
- [ ] 标准格式正则匹配通过
- [ ] CHANGE标签包含验证
- [ ] null值显示为"null"
- [ ] 特殊字符正确转义
- [ ] 超长字符串正确截断
- [ ] 截断标记"... (truncated)"在正确位置
- [ ] 先转义后截断顺序正确
- [ ] Console和JSON展示一致

## 完成标准
- [ ] 所有消息格式测试用例通过
- [ ] 正则表达式验证通过
- [ ] 与Console/JSON导出展示一致
- [ ] 特殊情况处理正确
- [ ] 性能影响可接受