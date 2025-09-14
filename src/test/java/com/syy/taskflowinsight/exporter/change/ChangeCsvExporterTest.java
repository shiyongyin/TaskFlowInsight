package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV导出器测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("CSV导出器测试")
public class ChangeCsvExporterTest {
    
    private ChangeCsvExporter exporter;
    private List<ChangeRecord> testChanges;
    
    @BeforeEach
    void setUp() {
        exporter = new ChangeCsvExporter();
        testChanges = new ArrayList<>();
    }
    
    @Test
    @DisplayName("基础功能-空列表导出")
    void testEmptyList() {
        String csv = exporter.format(testChanges);
        
        // 应该只有头部
        assertThat(csv).isEqualTo("Type,Object,Field,Old Value,New Value\n");
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
        
        String csv = exporter.format(testChanges);
        
        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(2); // 头部 + 1行数据
        assertThat(lines[0]).isEqualTo("Type,Object,Field,Old Value,New Value");
        assertThat(lines[1]).isEqualTo("UPDATE,User,name,Alice,Bob");
    }
    
    @Test
    @DisplayName("基础功能-多个变更导出")
    void testMultipleChanges() {
        testChanges.add(ChangeRecord.of("User", "name", "Alice", "Bob", ChangeType.UPDATE));
        testChanges.add(ChangeRecord.of("User", "age", null, 25, ChangeType.CREATE));
        testChanges.add(ChangeRecord.of("User", "email", "alice@example.com", null, ChangeType.DELETE));
        
        String csv = exporter.format(testChanges);
        
        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(4); // 头部 + 3行数据
        assertThat(lines[1]).contains("UPDATE");
        assertThat(lines[2]).contains("CREATE");
        assertThat(lines[3]).contains("DELETE");
    }
    
    @Test
    @DisplayName("特殊字符转义-逗号")
    void testCommaEscaping() {
        ChangeRecord change = ChangeRecord.of(
            "User", "address", 
            "123 Main St, Apt 4", "456 Oak Ave, Suite 200", 
            ChangeType.UPDATE
        );
        testChanges.add(change);
        
        String csv = exporter.format(testChanges);
        
        String[] lines = csv.split("\n");
        // 包含逗号的字段应该被引号包围
        assertThat(lines[1]).contains("\"123 Main St, Apt 4\"");
        assertThat(lines[1]).contains("\"456 Oak Ave, Suite 200\"");
    }
    
    @Test
    @DisplayName("特殊字符转义-引号")
    void testQuoteEscaping() {
        ChangeRecord change = ChangeRecord.of(
            "User", "nickname", 
            "John \"The Boss\" Doe", "Jane \"The Chief\" Smith", 
            ChangeType.UPDATE
        );
        testChanges.add(change);
        
        String csv = exporter.format(testChanges);
        
        String[] lines = csv.split("\n");
        // 引号应该被转义为双引号
        assertThat(lines[1]).contains("\"John \"\"The Boss\"\" Doe\"");
        assertThat(lines[1]).contains("\"Jane \"\"The Chief\"\" Smith\"");
    }
    
    @Test
    @DisplayName("特殊字符转义-换行符")
    void testNewlineEscaping() {
        ChangeRecord change = ChangeRecord.of(
            "User", "bio", 
            "Line 1\nLine 2", "Single line", 
            ChangeType.UPDATE
        );
        testChanges.add(change);
        
        String csv = exporter.format(testChanges);
        
        // 包含换行的字段应该被引号包围
        assertThat(csv).contains("\"Line 1\nLine 2\"");
    }
    
    @Test
    @DisplayName("null值处理")
    void testNullValues() {
        testChanges.add(ChangeRecord.of("User", "field1", null, "value", ChangeType.CREATE));
        testChanges.add(ChangeRecord.of("User", "field2", "value", null, ChangeType.DELETE));
        
        String csv = exporter.format(testChanges);
        
        String[] lines = csv.split("\n");
        // null值应该显示为空字段
        assertThat(lines[1]).isEqualTo("CREATE,User,field1,,value");
        assertThat(lines[2]).isEqualTo("DELETE,User,field2,value,");
    }
    
    @Test
    @DisplayName("配置选项-无头部")
    void testWithoutHeader() {
        exporter.withHeader(false);
        
        testChanges.add(ChangeRecord.of("User", "name", "A", "B", ChangeType.UPDATE));
        
        String csv = exporter.format(testChanges);
        
        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(1); // 只有数据行
        assertThat(lines[0]).doesNotContain("Type");
        assertThat(lines[0]).startsWith("UPDATE");
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
        
        String csv = exporter.format(testChanges, config);
        
        String[] lines = csv.split("\n");
        assertThat(lines[0]).endsWith(",Timestamp");
        assertThat(lines[1]).contains("2009-02-13"); // 时间戳对应的日期
    }
    
    @Test
    @DisplayName("Excel兼容模式")
    void testExcelCompatible() {
        ChangeCsvExporter excelExporter = ChangeCsvExporter.forExcel();
        
        testChanges.add(ChangeRecord.of("User", "name", "Alice", "Bob", ChangeType.UPDATE));
        
        String csv = excelExporter.format(testChanges);
        
        // Excel格式使用\r\n作为行分隔符
        assertThat(csv).contains("\r\n");
    }
    
    @Test
    @DisplayName("TSV格式")
    void testTsvFormat() {
        ChangeCsvExporter tsvExporter = ChangeCsvExporter.forTsv();
        
        testChanges.add(ChangeRecord.of("User", "name", "Alice", "Bob", ChangeType.UPDATE));
        
        String csv = tsvExporter.format(testChanges);
        
        // TSV使用tab作为分隔符
        assertThat(csv).contains("\t");
        assertThat(csv).doesNotContain(",");
        
        String[] lines = csv.split("\n");
        String[] fields = lines[1].split("\t");
        assertThat(fields).hasSize(5);
        assertThat(fields[0]).isEqualTo("UPDATE");
        assertThat(fields[3]).isEqualTo("Alice");
        assertThat(fields[4]).isEqualTo("Bob");
    }
    
    @Test
    @DisplayName("自定义分隔符")
    void testCustomSeparator() {
        exporter.withSeparator("|");
        
        testChanges.add(ChangeRecord.of("User", "name", "Alice", "Bob", ChangeType.UPDATE));
        
        String csv = exporter.format(testChanges);
        
        String[] lines = csv.split("\n");
        assertThat(lines[0]).isEqualTo("Type|Object|Field|Old Value|New Value");
        assertThat(lines[1]).isEqualTo("UPDATE|User|name|Alice|Bob");
    }
    
    @Test
    @DisplayName("性能测试-大量数据")
    void testLargeDataset() {
        // 生成1000条记录
        for (int i = 0; i < 1000; i++) {
            testChanges.add(ChangeRecord.of(
                "Object" + i,
                "field" + i,
                "old" + i,
                "new" + i,
                ChangeType.UPDATE
            ));
        }
        
        long startTime = System.nanoTime();
        String csv = exporter.format(testChanges);
        long duration = System.nanoTime() - startTime;
        
        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(1001); // 头部 + 1000行数据
        
        // 性能应该在合理范围内（< 100ms）
        assertThat(duration / 1_000_000).isLessThan(100);
    }
}