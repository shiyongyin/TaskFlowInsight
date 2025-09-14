package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XML导出器测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("XML导出器测试")
public class ChangeXmlExporterTest {
    
    private ChangeXmlExporter exporter;
    private List<ChangeRecord> testChanges;
    
    @BeforeEach
    void setUp() {
        exporter = new ChangeXmlExporter();
        testChanges = new ArrayList<>();
    }
    
    @Test
    @DisplayName("基础功能-空列表导出")
    void testEmptyList() {
        String xml = exporter.format(testChanges);
        
        assertThat(xml).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("<changeRecords");
        assertThat(xml).contains("count=\"0\"");
        assertThat(xml).contains("</changeRecords>");
    }
    
    @Test
    @DisplayName("基础功能-单个变更导出")
    void testSingleChange() {
        ChangeRecord change = ChangeRecord.builder()
            .objectName("User")
            .fieldName("name")
            .changeType(ChangeType.UPDATE)
            .oldValue("Alice")
            .newValue("Bob")
            .timestamp(System.currentTimeMillis())
            .build();
        testChanges.add(change);
        
        String xml = exporter.format(testChanges);
        
        assertThat(xml).contains("count=\"1\"");
        assertThat(xml).contains("<changeRecord type=\"UPDATE\"");
        assertThat(xml).contains("object=\"User\"");
        assertThat(xml).contains("field=\"name\"");
        assertThat(xml).contains("<oldValue>Alice</oldValue>");
        assertThat(xml).contains("<newValue>Bob</newValue>");
    }
    
    @Test
    @DisplayName("基础功能-多个变更导出")
    void testMultipleChanges() {
        testChanges.add(ChangeRecord.of("User", "name", "Alice", "Bob", ChangeType.UPDATE));
        testChanges.add(ChangeRecord.of("User", "age", null, 25, ChangeType.CREATE));
        testChanges.add(ChangeRecord.of("User", "email", "alice@example.com", null, ChangeType.DELETE));
        
        String xml = exporter.format(testChanges);
        
        assertThat(xml).contains("count=\"3\"");
        // 验证包含3个changeRecord标签（排除changeRecords）
        int count = 0;
        int index = 0;
        while ((index = xml.indexOf("<changeRecord ", index)) != -1) {
            count++;
            index++;
        }
        assertThat(count).isEqualTo(3);
    }
    
    @Test
    @DisplayName("特殊字符转义")
    void testXmlEscaping() {
        ChangeRecord change = ChangeRecord.of(
            "User", "description", 
            "Hello <world>", "Goodbye & \"farewell\"", 
            ChangeType.UPDATE
        );
        testChanges.add(change);
        
        String xml = exporter.format(testChanges);
        
        assertThat(xml).contains("<oldValue>Hello &lt;world&gt;</oldValue>");
        assertThat(xml).contains("<newValue>Goodbye &amp; &quot;farewell&quot;</newValue>");
    }
    
    @Test
    @DisplayName("包含会话和任务信息")
    void testWithSessionAndTask() {
        ChangeRecord change = ChangeRecord.builder()
            .objectName("User")
            .fieldName("status")
            .changeType(ChangeType.UPDATE)
            .oldValue("active")
            .newValue("inactive")
            .sessionId("session-123")
            .taskPath("MainTask/SubTask")
            .timestamp(0)
            .build();
        testChanges.add(change);
        
        String xml = exporter.format(testChanges);
        
        assertThat(xml).contains("<sessionId>session-123</sessionId>");
        assertThat(xml).contains("<taskPath>MainTask/SubTask</taskPath>");
    }
    
    @Test
    @DisplayName("配置选项-显示时间戳")
    void testWithTimestamp() {
        ChangeRecord change = ChangeRecord.builder()
            .objectName("User")
            .fieldName("name")
            .changeType(ChangeType.UPDATE)
            .oldValue("Alice")
            .newValue("Bob")
            .timestamp(1234567890000L)
            .build();
        testChanges.add(change);
        
        ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
        config.setShowTimestamp(true);
        
        String xml = exporter.format(testChanges, config);
        
        assertThat(xml).contains("timestamp=\"");
        assertThat(xml).contains("1234567890000");
    }
    
    @Test
    @DisplayName("配置选项-值长度限制")
    void testMaxValueLength() {
        String longValue = "This is a very long value that should be truncated";
        ChangeRecord change = ChangeRecord.of("User", "bio", longValue, "Short", ChangeType.UPDATE);
        testChanges.add(change);
        
        ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
        config.setMaxValueLength(10);
        
        String xml = exporter.format(testChanges, config);
        
        assertThat(xml).contains("<oldValue>This is a ...</oldValue>");
        assertThat(xml).contains("<newValue>Short</newValue>");
    }
    
    @Test
    @DisplayName("null值处理")
    void testNullValues() {
        testChanges.add(ChangeRecord.of("User", "field1", null, "value", ChangeType.CREATE));
        testChanges.add(ChangeRecord.of("User", "field2", "value", null, ChangeType.DELETE));
        
        String xml = exporter.format(testChanges);
        
        // null值不应该生成对应的元素
        assertThat(xml).doesNotContain("<oldValue></oldValue>");
        assertThat(xml).contains("<newValue>value</newValue>");
    }
    
    @Test
    @DisplayName("Unicode字符处理")
    void testUnicodeCharacters() {
        ChangeRecord change = ChangeRecord.of(
            "User", "name", 
            "测试用户", "Test User 🎉", 
            ChangeType.UPDATE
        );
        testChanges.add(change);
        
        String xml = exporter.format(testChanges);
        
        // Unicode字符应该被正确处理
        assertThat(xml).contains("测试用户");
        assertThat(xml).contains("Test User");
    }
    
    @Test
    @DisplayName("XML格式验证")
    void testXmlStructure() {
        testChanges.add(ChangeRecord.of("User", "name", "A", "B", ChangeType.UPDATE));
        
        String xml = exporter.format(testChanges);
        
        // 验证基本的XML结构
        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("xmlns=\"http://taskflowinsight.syy.com/changes\"");
        
        // 验证标签配对（只查找changeRecord，不包括changeRecords）
        int openTags = 0;
        int closeTags = 0;
        int idx = 0;
        while ((idx = xml.indexOf("<changeRecord ", idx)) != -1) {
            openTags++;
            idx++;
        }
        idx = 0;
        while ((idx = xml.indexOf("</changeRecord>", idx)) != -1) {
            closeTags++;
            idx++;
        }
        assertThat(openTags).isEqualTo(closeTags).isEqualTo(1);
    }
}