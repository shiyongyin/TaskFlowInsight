package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * StreamingChangeExporter综合测试
 * 覆盖流式导出器及其内部类的功能
 */
class StreamingChangeExporterComprehensiveTest {

    @TempDir
    Path tempDir;

    private List<ChangeRecord> testRecords;
    private ChangeExporter.ExportConfig defaultConfig;
    private ChangeExporter.ExportConfig customConfig;

    @BeforeEach
    void setUp() {
        // 创建测试数据
        testRecords = Arrays.asList(
            ChangeRecord.builder()
                .objectName("User")
                .fieldName("name")
                .oldValue("oldName")
                .newValue("newName")
                .changeType(ChangeType.UPDATE)
                .timestamp(1000000000L)
                .sessionId("session1")
                .taskPath("task1")
                .valueType("java.lang.String")
                .valueKind("STRING")
                .build(),
            ChangeRecord.builder()
                .objectName("Order")
                .fieldName("amount")
                .oldValue(100.50)
                .newValue(200.75)
                .changeType(ChangeType.UPDATE)
                .timestamp(1000000001L)
                .build(),
            ChangeRecord.builder()
                .objectName("Product")
                .fieldName("description")
                .oldValue(null)
                .newValue("New product with \"quotes\" and\nline breaks")
                .changeType(ChangeType.CREATE)
                .timestamp(1000000002L)
                .build()
        );

        defaultConfig = new ChangeExporter.ExportConfig();
        
        customConfig = new ChangeExporter.ExportConfig();
        customConfig.setShowTimestamp(true);
        customConfig.setPrettyPrint(false);
        customConfig.setIncludeSensitiveInfo(false);
        customConfig.setMaxValueLength(50);
    }

    // StreamingXmlExporter测试
    @Test
    @DisplayName("StreamingXmlExporter基本导出功能")
    void streamingXmlExporter_basicExport() throws IOException {
        StreamingChangeExporter.StreamingXmlExporter exporter = 
            new StreamingChangeExporter.StreamingXmlExporter();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(testRecords.iterator(), output, defaultConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        assertThat(result).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(result).contains("<changeRecords xmlns=\"http://taskflowinsight.syy.com/changes\">");
        assertThat(result).contains("</changeRecords>");
        assertThat(result).contains("<changeRecord type=\"UPDATE\"");
        assertThat(result).contains("object=\"User\"");
        assertThat(result).contains("field=\"name\"");
        assertThat(result).contains("<oldValue>oldName</oldValue>");
        assertThat(result).contains("<newValue>newName</newValue>");
    }

    @Test
    @DisplayName("StreamingXmlExporter时间戳配置")
    void streamingXmlExporter_withTimestamp() throws IOException {
        StreamingChangeExporter.StreamingXmlExporter exporter = 
            new StreamingChangeExporter.StreamingXmlExporter();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(testRecords.iterator(), output, customConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        assertThat(result).contains("timestamp=\"1000000000\"");
        assertThat(result).contains("timestamp=\"1000000001\"");
    }

    @Test
    @DisplayName("StreamingXmlExporter XML转义")
    void streamingXmlExporter_xmlEscaping() throws IOException {
        StreamingChangeExporter.StreamingXmlExporter exporter = 
            new StreamingChangeExporter.StreamingXmlExporter();
        
        ChangeRecord recordWithSpecialChars = ChangeRecord.builder()
            .objectName("Test<>&\"'")
            .fieldName("field&<>\"'")
            .oldValue("old<>&\"'value")
            .newValue("new<>&\"'value")
            .changeType(ChangeType.UPDATE)
            .build();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(Arrays.asList(recordWithSpecialChars).iterator(), output, defaultConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        assertThat(result).contains("object=\"Test&lt;&gt;&amp;&quot;&apos;\"");
        assertThat(result).contains("field=\"field&amp;&lt;&gt;&quot;&apos;\"");
        assertThat(result).contains("<oldValue>old&lt;&gt;&amp;&quot;&apos;value</oldValue>");
        assertThat(result).contains("<newValue>new&lt;&gt;&amp;&quot;&apos;value</newValue>");
    }

    @Test
    @DisplayName("StreamingXmlExporter空值处理")
    void streamingXmlExporter_nullValues() throws IOException {
        StreamingChangeExporter.StreamingXmlExporter exporter = 
            new StreamingChangeExporter.StreamingXmlExporter();
        
        ChangeRecord recordWithNulls = ChangeRecord.builder()
            .objectName("Test")
            .fieldName("field")
            .oldValue(null)
            .newValue("newValue")
            .changeType(ChangeType.CREATE)
            .build();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(Arrays.asList(recordWithNulls).iterator(), output, defaultConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        assertThat(result).doesNotContain("<oldValue>");
        assertThat(result).contains("<newValue>newValue</newValue>");
    }

    // StreamingCsvExporter测试
    @Test
    @DisplayName("StreamingCsvExporter默认构造函数")
    void streamingCsvExporter_defaultConstructor() throws IOException {
        StreamingChangeExporter.StreamingCsvExporter exporter = 
            new StreamingChangeExporter.StreamingCsvExporter();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(testRecords.iterator(), output, defaultConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        // 检查CSV头部
        assertThat(result).startsWith("ChangeType,ObjectName,FieldName,OldValue,NewValue\n");
        assertThat(result).contains("UPDATE,User,name,oldName,newName\n");
        assertThat(result).contains("UPDATE,Order,amount,100.5,200.75\n");
    }

    @Test
    @DisplayName("StreamingCsvExporter自定义分隔符")
    void streamingCsvExporter_customSeparator() throws IOException {
        StreamingChangeExporter.StreamingCsvExporter exporter = 
            new StreamingChangeExporter.StreamingCsvExporter(";", true);
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(testRecords.iterator(), output, defaultConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        assertThat(result).startsWith("ChangeType;ObjectName;FieldName;OldValue;NewValue\n");
        assertThat(result).contains("UPDATE;User;name;oldName;newName\n");
    }

    @Test
    @DisplayName("StreamingCsvExporter无头部")
    void streamingCsvExporter_noHeader() throws IOException {
        StreamingChangeExporter.StreamingCsvExporter exporter = 
            new StreamingChangeExporter.StreamingCsvExporter(",", false);
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(testRecords.iterator(), output, defaultConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        assertThat(result).doesNotStartWith("ChangeType,");
        assertThat(result).startsWith("UPDATE,User,name,oldName,newName\n");
    }

    @Test
    @DisplayName("StreamingCsvExporter时间戳列")
    void streamingCsvExporter_withTimestamp() throws IOException {
        StreamingChangeExporter.StreamingCsvExporter exporter = 
            new StreamingChangeExporter.StreamingCsvExporter();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(testRecords.iterator(), output, customConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        assertThat(result).startsWith("ChangeType,ObjectName,FieldName,OldValue,NewValue,Timestamp\n");
        assertThat(result).contains(",1000000000\n");
        assertThat(result).contains(",1000000001\n");
    }

    @Test
    @DisplayName("StreamingCsvExporter CSV转义")
    void streamingCsvExporter_csvEscaping() throws IOException {
        StreamingChangeExporter.StreamingCsvExporter exporter = 
            new StreamingChangeExporter.StreamingCsvExporter();
        
        ChangeRecord recordWithSpecialChars = ChangeRecord.builder()
            .objectName("Test,With\"Comma")
            .fieldName("field\nwith\r\nlines")
            .oldValue("old,value\"with'quotes")
            .newValue("new value")
            .changeType(ChangeType.UPDATE)
            .build();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(Arrays.asList(recordWithSpecialChars).iterator(), output, defaultConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        assertThat(result).contains("\"Test,With\"\"Comma\"");
        assertThat(result).contains("\"field\nwith\r\nlines\"");
        assertThat(result).contains("\"old,value\"\"with'quotes\"");
    }

    @Test
    @DisplayName("StreamingCsvExporter null值处理")
    void streamingCsvExporter_nullValues() throws IOException {
        StreamingChangeExporter.StreamingCsvExporter exporter = 
            new StreamingChangeExporter.StreamingCsvExporter();
        
        ChangeRecord recordWithNulls = ChangeRecord.builder()
            .objectName("Test")
            .fieldName("field")
            .oldValue(null)
            .newValue(null)
            .changeType(ChangeType.CREATE)
            .build();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(Arrays.asList(recordWithNulls).iterator(), output, defaultConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        String[] lines = result.split("\n");
        assertThat(lines[1]).isEqualTo("CREATE,Test,field,null,null");
    }

    // StreamingJsonExporter测试
    @Test
    @DisplayName("StreamingJsonExporter基本导出")
    void streamingJsonExporter_basicExport() throws IOException {
        StreamingChangeExporter.StreamingJsonExporter exporter = 
            new StreamingChangeExporter.StreamingJsonExporter();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(testRecords.iterator(), output, defaultConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        String[] lines = result.trim().split("\n");
        assertThat(lines).hasSize(3);
        
        assertThat(lines[0]).contains("\"type\":\"UPDATE\"");
        assertThat(lines[0]).contains("\"object\":\"User\"");
        assertThat(lines[0]).contains("\"field\":\"name\"");
        assertThat(lines[0]).contains("\"oldValue\":\"oldName\"");
        assertThat(lines[0]).contains("\"newValue\":\"newName\"");
    }

    @Test
    @DisplayName("StreamingJsonExporter时间戳")
    void streamingJsonExporter_withTimestamp() throws IOException {
        StreamingChangeExporter.StreamingJsonExporter exporter = 
            new StreamingChangeExporter.StreamingJsonExporter();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(testRecords.iterator(), output, customConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        assertThat(result).contains("\"timestamp\":1000000000");
        assertThat(result).contains("\"timestamp\":1000000001");
    }

    @Test
    @DisplayName("StreamingJsonExporter JSON转义")
    void streamingJsonExporter_jsonEscaping() throws IOException {
        StreamingChangeExporter.StreamingJsonExporter exporter = 
            new StreamingChangeExporter.StreamingJsonExporter();
        
        ChangeRecord recordWithSpecialChars = ChangeRecord.builder()
            .objectName("Test\"\\Value")
            .fieldName("field\n\r\t\b\f")
            .oldValue("old\"value\\with/special")
            .newValue("new\nvalue")
            .changeType(ChangeType.UPDATE)
            .build();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(Arrays.asList(recordWithSpecialChars).iterator(), output, defaultConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        assertThat(result).contains("\"object\":\"Test\\\"\\\\Value\"");
        assertThat(result).contains("\"field\":\"field\\n\\r\\t\\b\\f\"");
        assertThat(result).contains("\"oldValue\":\"old\\\"value\\\\with/special\"");
        assertThat(result).contains("\"newValue\":\"new\\nvalue\"");
    }

    @Test
    @DisplayName("StreamingJsonExporter null值处理")
    void streamingJsonExporter_nullValues() throws IOException {
        StreamingChangeExporter.StreamingJsonExporter exporter = 
            new StreamingChangeExporter.StreamingJsonExporter();
        
        ChangeRecord recordWithNulls = ChangeRecord.builder()
            .objectName("Test")
            .fieldName("field")
            .oldValue(null)
            .newValue("newValue")
            .changeType(ChangeType.CREATE)
            .build();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(Arrays.asList(recordWithNulls).iterator(), output, defaultConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        
        assertThat(result).doesNotContain("\"oldValue\":");
        assertThat(result).contains("\"newValue\":\"newValue\"");
    }

    // StreamingChangeExporter基类测试
    @Test
    @DisplayName("StreamingChangeExporter导出到文件")
    void streamingChangeExporter_exportToFile() throws IOException {
        StreamingChangeExporter.StreamingCsvExporter exporter = 
            new StreamingChangeExporter.StreamingCsvExporter();
        
        File testFile = tempDir.resolve("test.csv").toFile();
        
        exporter.exportToFile(testRecords.iterator(), testFile, defaultConfig);
        
        assertThat(testFile).exists();
        
        String content = new String(java.nio.file.Files.readAllBytes(testFile.toPath()), StandardCharsets.UTF_8);
        assertThat(content).startsWith("ChangeType,ObjectName,FieldName,OldValue,NewValue\n");
        assertThat(content).contains("UPDATE,User,name,oldName,newName\n");
    }

    @Test
    @DisplayName("StreamingChangeExporter批处理")
    void streamingChangeExporter_processInBatches() {
        TestStreamingExporter exporter = new TestStreamingExporter();
        
        List<ChangeRecord> records = Arrays.asList(
            testRecords.get(0), testRecords.get(1), testRecords.get(2)
        );
        
        AtomicInteger processedCount = new AtomicInteger(0);
        
        exporter.processInBatches(
            records.iterator(),
            record -> processedCount.incrementAndGet(),
            2
        );
        
        assertThat(processedCount.get()).isEqualTo(3);
        assertThat(exporter.getBatchCallbacks()).containsExactly(2, 3);
    }

    @Test
    @DisplayName("StreamingChangeExporter空迭代器处理")
    void streamingChangeExporter_emptyIterator() throws IOException {
        StreamingChangeExporter.StreamingCsvExporter exporter = 
            new StreamingChangeExporter.StreamingCsvExporter();
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        exporter.exportToStream(Collections.emptyIterator(), output, defaultConfig);
        
        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).isEqualTo("ChangeType,ObjectName,FieldName,OldValue,NewValue\n");
    }

    // 静态方法测试
    @Test
    @DisplayName("formatValue基本功能")
    void formatValue_basic() {
        String result = StreamingChangeExporter.formatValue("test value", defaultConfig);
        assertThat(result).isEqualTo("test value");
    }

    @Test
    @DisplayName("formatValue null值")
    void formatValue_nullValue() {
        String result = StreamingChangeExporter.formatValue(null, defaultConfig);
        assertThat(result).isEqualTo("null");
    }

    @Test
    @DisplayName("formatValue长度限制")
    void formatValue_lengthLimit() {
        ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
        config.setMaxValueLength(5);
        
        String result = StreamingChangeExporter.formatValue("very long string", config);
        assertThat(result).isEqualTo("very ...");
    }

    @Test
    @DisplayName("formatValue敏感信息脱敏")
    void formatValue_sensitiveInfo() {
        ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
        config.setIncludeSensitiveInfo(false);
        
        String result1 = StreamingChangeExporter.formatValue("password123", config);
        assertThat(result1).isEqualTo("***MASKED***");
        
        String result2 = StreamingChangeExporter.formatValue("secret_key", config);
        assertThat(result2).isEqualTo("***MASKED***");
        
        String result3 = StreamingChangeExporter.formatValue("token_value", config);
        assertThat(result3).isEqualTo("***MASKED***");
        
        String result4 = StreamingChangeExporter.formatValue("normal_value", config);
        assertThat(result4).isEqualTo("normal_value");
    }

    @Test
    @DisplayName("formatValue敏感信息不脱敏")
    void formatValue_includeSensitiveInfo() {
        ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
        config.setIncludeSensitiveInfo(true);
        
        String result = StreamingChangeExporter.formatValue("password123", config);
        assertThat(result).isEqualTo("password123");
    }

    // 测试用的StreamingChangeExporter实现
    private static class TestStreamingExporter extends StreamingChangeExporter {
        private final List<Integer> batchCallbacks = new ArrayList<>();
        
        @Override
        public void exportToStream(Iterator<ChangeRecord> changes, OutputStream output, 
                                 ChangeExporter.ExportConfig config) throws IOException {
            // 简单实现，用于测试基类功能
        }
        
        @Override
        protected void onBatchProcessed(int totalProcessed) {
            batchCallbacks.add(totalProcessed);
        }
        
        public List<Integer> getBatchCallbacks() {
            return batchCallbacks;
        }
    }
}