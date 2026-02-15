package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 变更记录导出格式一致性测试
 * 
 * 测试覆盖：
 * - 三种导出器的一致性验证
 * - 敏感信息脱敏处理
 * - 导出失败时的格式稳定性
 * - 空列表和边界条件处理
 * - 结构断言，不依赖文案
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-10
 */
@DisplayName("OutputFormat一致性测试")
public class OutputFormatConsistencyTest {

    private List<ChangeRecord> testChanges;
    private ChangeConsoleExporter consoleExporter;
    private ChangeJsonExporter jsonExporter;
    private ChangeJsonExporter enhancedJsonExporter;
    
    @BeforeEach
    void setUp() {
        consoleExporter = new ChangeConsoleExporter();
        jsonExporter = new ChangeJsonExporter(ChangeJsonExporter.ExportMode.COMPAT);
        enhancedJsonExporter = new ChangeJsonExporter(ChangeJsonExporter.ExportMode.ENHANCED);
        
        // 创建测试数据
        testChanges = createTestChangeRecords();
    }
    
    private List<ChangeRecord> createTestChangeRecords() {
        List<ChangeRecord> changes = new ArrayList<>();
        
        // CREATE类型
        changes.add(ChangeRecord.builder()
            .objectName("User")
            .fieldName("name")
            .oldValue(null)
            .newValue("Alice")
            .changeType(ChangeType.CREATE)
            .valueRepr("Alice")
            .valueKind("STRING")
            .valueType("java.lang.String")
            .sessionId("test-session-1")
            .taskPath("MainTask")
            .build());
        
        // UPDATE类型
        changes.add(ChangeRecord.builder()
            .objectName("User")
            .fieldName("age")
            .oldValue(25)
            .newValue(26)
            .changeType(ChangeType.UPDATE)
            .valueRepr("26")
            .reprOld("25")
            .reprNew("26")
            .valueKind("NUMBER")
            .valueType("java.lang.Integer")
            .sessionId("test-session-1")
            .taskPath("MainTask")
            .build());
        
        // DELETE类型
        changes.add(ChangeRecord.builder()
            .objectName("User")
            .fieldName("email")
            .oldValue("alice@example.com")
            .newValue(null)
            .changeType(ChangeType.DELETE)
            .valueRepr("alice@example.com")
            .reprOld("alice@example.com")
            .valueKind("STRING")
            .valueType("java.lang.String")
            .sessionId("test-session-1")
            .taskPath("MainTask")
            .build());
        
        // 敏感字段
        changes.add(ChangeRecord.builder()
            .objectName("User")
            .fieldName("password")
            .oldValue("oldpass")
            .newValue("newpass")
            .changeType(ChangeType.UPDATE)
            .valueRepr("newpass")
            .reprOld("oldpass")
            .reprNew("newpass")
            .valueKind("STRING")
            .valueType("java.lang.String")
            .sessionId("test-session-1")
            .taskPath("MainTask")
            .build());
        
        return changes;
    }
    
    @Test
    @DisplayName("三种格式输出一致性-基本结构验证")
    void testOutputConsistency() {
        // Console格式验证
        String consoleOutput = consoleExporter.format(testChanges);
        verifyConsoleStructure(consoleOutput, testChanges);
        
        // JSON兼容格式验证
        String jsonOutput = jsonExporter.format(testChanges);
        verifyJsonStructure(jsonOutput, testChanges, false);
        
        // JSON增强格式验证
        String enhancedJsonOutput = enhancedJsonExporter.format(testChanges);
        verifyJsonStructure(enhancedJsonOutput, testChanges, true);
        
        // Map格式验证
        Map<String, Object> mapOutput = ChangeMapExporter.export(testChanges);
        verifyMapStructure(mapOutput, testChanges);
    }
    
    @Test
    @DisplayName("敏感信息脱敏-三种格式一致性")
    void testSensitiveInfoMasking() {
        ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
        config.setIncludeSensitiveInfo(false); // 启用脱敏
        
        // Console格式脱敏验证
        String consoleOutput = consoleExporter.format(testChanges, config);
        assertFalse(consoleOutput.contains("oldpass"));
        assertFalse(consoleOutput.contains("newpass"));
        assertTrue(consoleOutput.contains("[MASKED]"));
        
        // JSON格式脱敏验证
        String jsonOutput = jsonExporter.format(testChanges, config);
        assertFalse(jsonOutput.contains("oldpass"));
        assertFalse(jsonOutput.contains("newpass"));
        assertTrue(jsonOutput.contains("[MASKED]"));
        
        // Map格式脱敏验证
        Map<String, Object> mapOutput = ChangeMapExporter.export(testChanges, config);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) mapOutput.get("changes");
        
        boolean foundMaskedValue = false;
        for (Map<String, Object> change : changes) {
            if ("password".equals(change.get("field"))) {
                assertEquals("[MASKED]", change.get("oldValue"));
                assertEquals("[MASKED]", change.get("newValue"));
                foundMaskedValue = true;
            }
        }
        assertTrue(foundMaskedValue, "Should find masked password field");
    }
    
    @Test
    @DisplayName("敏感信息不脱敏-配置验证")
    void testSensitiveInfoNotMasking() {
        ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
        config.setIncludeSensitiveInfo(true); // 不脱敏
        
        String consoleOutput = consoleExporter.format(testChanges, config);
        assertTrue(consoleOutput.contains("oldpass"));
        assertTrue(consoleOutput.contains("newpass"));
        assertFalse(consoleOutput.contains("[MASKED]"));
    }
    
    @Test
    @DisplayName("空列表处理-三种格式稳定性")
    void testEmptyListHandling() {
        List<ChangeRecord> emptyList = Collections.emptyList();
        
        // Console格式
        String consoleOutput = consoleExporter.format(emptyList);
        assertTrue(consoleOutput.contains("Total changes: 0"));
        
        // JSON格式
        String jsonOutput = jsonExporter.format(emptyList);
        assertTrue(jsonOutput.contains("\"changes\":[]"));
        
        // JSON增强格式
        String enhancedJsonOutput = enhancedJsonExporter.format(emptyList);
        assertTrue(enhancedJsonOutput.contains("\"count\":0"));
        assertTrue(enhancedJsonOutput.contains("\"changes\":[]"));
        
        // Map格式
        Map<String, Object> mapOutput = ChangeMapExporter.export(emptyList);
        assertEquals(0, mapOutput.get("count"));
        @SuppressWarnings("unchecked")
        List<Object> changes = (List<Object>) mapOutput.get("changes");
        assertTrue(changes.isEmpty());
    }
    
    @Test
    @DisplayName("null输入处理-格式稳定性")
    void testNullInputHandling() {
        // Console格式
        String consoleOutput = consoleExporter.format(null);
        assertTrue(consoleOutput.contains("Total changes: 0"));
        
        // JSON格式
        String jsonOutput = jsonExporter.format(null);
        assertTrue(jsonOutput.contains("\"changes\":[]"));
        
        // Map格式
        Map<String, Object> mapOutput = ChangeMapExporter.export(null);
        assertEquals(0, mapOutput.get("count"));
    }
    
    @Test
    @DisplayName("特殊字符转义-JSON格式验证")
    void testSpecialCharacterEscaping() {
        ChangeRecord specialCharChange = ChangeRecord.builder()
            .objectName("Test\"Object")
            .fieldName("field\nwith\tspecial")
            .oldValue("old\rvalue")
            .newValue("new\\value")
            .changeType(ChangeType.UPDATE)
            .valueRepr("new\\value")
            .build();
        
        List<ChangeRecord> specialChanges = List.of(specialCharChange);
        
        String jsonOutput = jsonExporter.format(specialChanges);
        
        // 验证JSON格式正确（不会因特殊字符导致格式错误）
        assertTrue(jsonOutput.startsWith("{"));
        assertTrue(jsonOutput.endsWith("}"));
        
        // 验证特殊字符被正确转义（基于实际输出）
        assertTrue(jsonOutput.contains("Test\\\"Object"));  // 引号转义
        assertTrue(jsonOutput.contains("\\n"));             // 换行转义
        assertTrue(jsonOutput.contains("\\t"));             // 制表符转义
        assertTrue(jsonOutput.contains("\\\\"));            // 反斜杠转义
    }
    
    @Test
    @DisplayName("大数据量处理-性能稳定性")
    void testLargeDataHandling() {
        // 创建大量变更记录
        List<ChangeRecord> largeChanges = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeChanges.add(ChangeRecord.builder()
                .objectName("Object" + i)
                .fieldName("field" + i)
                .oldValue("old" + i)
                .newValue("new" + i)
                .changeType(ChangeType.UPDATE)
                .valueRepr("new" + i)
                .build());
        }
        
        // 测试各种格式都能正确处理大数据量
        long startTime = System.nanoTime();
        
        String consoleOutput = consoleExporter.format(largeChanges);
        assertTrue(consoleOutput.contains("Total changes: 1000"));
        
        String jsonOutput = jsonExporter.format(largeChanges);
        assertTrue(jsonOutput.contains("\"changes\":["));
        
        Map<String, Object> mapOutput = ChangeMapExporter.export(largeChanges);
        assertEquals(1000, mapOutput.get("count"));
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        
        // 性能要求：1000个记录处理时间应该在合理范围内（<1秒）
        assertTrue(durationMs < 1000, 
            String.format("Processing 1000 changes took too long: %dms", durationMs));
        
        System.out.printf("Large data processing time: %dms%n", durationMs);
    }
    
    @Test
    @DisplayName("Map导出分组功能-按对象分组")
    void testMapExporterGrouping() {
        Map<String, List<Map<String, Object>>> grouped = 
            ChangeMapExporter.exportGroupedByObject(testChanges);
        
        // 验证分组结构
        assertTrue(grouped.containsKey("User"));
        List<Map<String, Object>> userChanges = grouped.get("User");
        assertEquals(4, userChanges.size()); // 4个User相关的变更
        
        // 验证分组内容
        Set<String> fieldNames = new HashSet<>();
        for (Map<String, Object> change : userChanges) {
            fieldNames.add((String) change.get("field"));
        }
        assertTrue(fieldNames.contains("name"));
        assertTrue(fieldNames.contains("age"));
        assertTrue(fieldNames.contains("email"));
        assertTrue(fieldNames.contains("password"));
    }
    
    /**
     * 验证Console输出结构
     */
    private void verifyConsoleStructure(String output, List<ChangeRecord> expected) {
        assertNotNull(output);
        assertTrue(output.contains("=== Change Summary ==="));
        assertTrue(output.contains("Total changes: " + expected.size()));
        
        for (ChangeRecord change : expected) {
            String pattern = String.format("[%s].*%s\\.%s", 
                change.getChangeType(), 
                change.getObjectName(), 
                change.getFieldName());
            assertTrue(output.contains("[" + change.getChangeType() + "]"));
            assertTrue(output.contains(change.getObjectName() + "." + change.getFieldName()));
        }
    }
    
    /**
     * 验证JSON输出结构
     */
    private void verifyJsonStructure(String json, List<ChangeRecord> expected, boolean isEnhanced) {
        assertNotNull(json);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"changes\":["));
        
        if (isEnhanced) {
            assertTrue(json.contains("\"metadata\":"));
            assertTrue(json.contains("\"count\":" + expected.size()));
        }
        
        // 验证包含基本字段
        assertTrue(json.contains("\"type\":"));
        assertTrue(json.contains("\"object\":"));
        assertTrue(json.contains("\"field\":"));
        
        // 验证变更类型都存在
        assertTrue(json.contains("\"CREATE\""));
        assertTrue(json.contains("\"UPDATE\""));
        assertTrue(json.contains("\"DELETE\""));
    }
    
    /**
     * 验证Map输出结构
     */
    @SuppressWarnings("unchecked")
    private void verifyMapStructure(Map<String, Object> map, List<ChangeRecord> expected) {
        assertNotNull(map);
        assertEquals(expected.size(), map.get("count"));
        
        assertTrue(map.containsKey("changes"));
        List<Map<String, Object>> changes = (List<Map<String, Object>>) map.get("changes");
        assertEquals(expected.size(), changes.size());
        
        assertTrue(map.containsKey("statistics"));
        Map<String, Object> statistics = (Map<String, Object>) map.get("statistics");
        assertTrue(statistics.containsKey("byType"));
        assertTrue(statistics.containsKey("totalChanges"));
        
        // 验证每个变更记录都包含基本字段
        for (Map<String, Object> change : changes) {
            assertTrue(change.containsKey("type"));
            assertTrue(change.containsKey("object"));
            assertTrue(change.containsKey("field"));
            assertTrue(change.containsKey("timestamp"));
        }
    }
}