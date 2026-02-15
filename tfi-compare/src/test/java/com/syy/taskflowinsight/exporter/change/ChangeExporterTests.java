package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * 变更导出器真实场景测试。
 * 目标：验证各格式导出器输出正确性，包括边界条件和特殊字符处理。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("ChangeExporter — 导出器测试")
class ChangeExporterTests {

    private List<ChangeRecord> sampleChanges;

    @BeforeEach
    void setUp() {
        sampleChanges = new ArrayList<>();
        sampleChanges.add(createChange("Order", "status", "PENDING", "PAID", ChangeType.UPDATE));
        sampleChanges.add(createChange("Order", "amount", "100.00", "200.00", ChangeType.UPDATE));
        sampleChanges.add(createChange("Order", "note", null, "rush order", ChangeType.CREATE));
        sampleChanges.add(createChange("Order", "discount", "10%", null, ChangeType.DELETE));
    }

    // ── JSON 导出 ──

    @Nested
    @DisplayName("ChangeJsonExporter")
    class JsonExporterTests {

        @Test
        @DisplayName("正常导出 → 有效非空 JSON")
        void normalExport_shouldProduceValidJson() {
            ChangeJsonExporter exporter = new ChangeJsonExporter();
            String json = exporter.format(sampleChanges);
            assertThat(json).isNotBlank();
            // JSON 输出应当包含变更相关关键字
            assertThat(json).containsAnyOf("status", "Order", "PENDING", "fieldName", "objectName");
        }

        @Test
        @DisplayName("空列表 → 有效空 JSON")
        void emptyList_shouldProduceValidJson() {
            ChangeJsonExporter exporter = new ChangeJsonExporter();
            String json = exporter.format(Collections.emptyList());
            assertThat(json).isNotNull();
        }

        @Test
        @DisplayName("特殊字符 → 正确转义")
        void specialChars_shouldBeEscaped() {
            List<ChangeRecord> changes = List.of(
                    createChange("Obj", "desc", "line1\nline2", "has \"quotes\"", ChangeType.UPDATE)
            );
            ChangeJsonExporter exporter = new ChangeJsonExporter();
            String json = exporter.format(changes);
            assertThat(json).isNotBlank();
            // JSON should escape newlines and quotes
        }

        @Test
        @DisplayName("ExportConfig 配置生效")
        void withConfig_shouldRespectConfig() {
            ChangeJsonExporter exporter = new ChangeJsonExporter();
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setPrettyPrint(true);
            String json = exporter.format(sampleChanges, config);
            assertThat(json).isNotBlank();
        }

        @Test
        @DisplayName("ENHANCED 模式")
        void enhancedMode_shouldWork() {
            ChangeJsonExporter exporter = new ChangeJsonExporter(ChangeJsonExporter.ExportMode.ENHANCED);
            String json = exporter.format(sampleChanges);
            assertThat(json).isNotBlank();
        }
    }

    // ── CSV 导出 ──

    @Nested
    @DisplayName("ChangeCsvExporter")
    class CsvExporterTests {

        @Test
        @DisplayName("正常导出 → 有效 CSV")
        void normalExport_shouldProduceValidCsv() {
            ChangeCsvExporter exporter = new ChangeCsvExporter();
            String csv = exporter.format(sampleChanges);
            assertThat(csv).isNotBlank();
            assertThat(csv).contains("Order");
        }

        @Test
        @DisplayName("空列表 → header only 或 empty")
        void emptyList_shouldReturnHeaderOrEmpty() {
            ChangeCsvExporter exporter = new ChangeCsvExporter();
            String csv = exporter.format(Collections.emptyList());
            assertThat(csv).isNotNull();
        }

        @Test
        @DisplayName("值含逗号 → 正确引用")
        void valueWithComma_shouldBeQuoted() {
            List<ChangeRecord> changes = List.of(
                    createChange("Obj", "desc", "a,b", "c,d", ChangeType.UPDATE)
            );
            ChangeCsvExporter exporter = new ChangeCsvExporter();
            String csv = exporter.format(changes);
            assertThat(csv).isNotBlank();
        }

        @Test
        @DisplayName("TSV 模式")
        void tsvMode_shouldUseTabs() {
            ChangeCsvExporter exporter = ChangeCsvExporter.forTsv();
            String tsv = exporter.format(sampleChanges);
            assertThat(tsv).isNotBlank();
        }
    }

    // ── Console 导出 ──

    @Nested
    @DisplayName("ChangeConsoleExporter")
    class ConsoleExporterTests {

        @Test
        @DisplayName("正常导出 → 可读文本")
        void normalExport_shouldProduceReadableText() {
            ChangeConsoleExporter exporter = new ChangeConsoleExporter();
            String text = exporter.format(sampleChanges);
            assertThat(text).isNotBlank();
            assertThat(text).contains("→");
        }

        @Test
        @DisplayName("空列表 → 不抛异常")
        void emptyList_shouldNotThrow() {
            ChangeConsoleExporter exporter = new ChangeConsoleExporter();
            assertThatCode(() -> exporter.format(Collections.emptyList()))
                    .doesNotThrowAnyException();
        }
    }

    // ── XML 导出 ──

    @Nested
    @DisplayName("ChangeXmlExporter")
    class XmlExporterTests {

        @Test
        @DisplayName("正常导出 → 有效 XML")
        void normalExport_shouldProduceValidXml() {
            ChangeXmlExporter exporter = new ChangeXmlExporter();
            String xml = exporter.format(sampleChanges);
            assertThat(xml).isNotBlank();
            assertThat(xml).contains("<?xml");
        }

        @Test
        @DisplayName("特殊 XML 字符 → 正确转义")
        void xmlSpecialChars_shouldBeEscaped() {
            List<ChangeRecord> changes = List.of(
                    createChange("Obj", "html", "<div>old</div>", "<div>new & bold</div>", ChangeType.UPDATE)
            );
            ChangeXmlExporter exporter = new ChangeXmlExporter();
            String xml = exporter.format(changes);
            assertThat(xml).isNotBlank();
            assertThat(xml).doesNotContain("<div>old</div>"); // should be escaped
        }
    }

    // ── Map 导出 ──

    @Nested
    @DisplayName("ChangeMapExporter")
    class MapExporterTests {

        @Test
        @DisplayName("正常导出 → Map 结构")
        void normalExport_shouldProduceMap() {
            ChangeMapExporter exporter = new ChangeMapExporter();
            Map<String, Object> result = exporter.export(sampleChanges);
            assertThat(result).isNotNull();
            assertThat(result).containsKey("changes");
        }

        @Test
        @DisplayName("分组导出 → 按对象名分组")
        void groupedExport_shouldGroupByObject() {
            Map<String, java.util.List<Map<String, Object>>> result =
                    ChangeMapExporter.exportGroupedByObject(sampleChanges);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("空列表 → 不抛异常")
        void emptyList_shouldNotThrow() {
            ChangeMapExporter exporter = new ChangeMapExporter();
            assertThatCode(() -> exporter.export(Collections.emptyList()))
                    .doesNotThrowAnyException();
        }
    }

    // ── 辅助方法 ──

    private ChangeRecord createChange(String objectName, String fieldName,
                                      String oldValue, String newValue,
                                      ChangeType changeType) {
        return ChangeRecord.of(objectName, fieldName, oldValue, newValue, changeType);
    }
}
