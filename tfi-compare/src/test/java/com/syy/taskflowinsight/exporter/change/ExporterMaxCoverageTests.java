package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Maximum coverage tests for exporter/change package.
 * Covers all export methods, config options, streaming, and edge cases.
 *
 * @since 3.0.0
 */
@DisplayName("Exporter Max Coverage — 导出器全覆盖测试")
class ExporterMaxCoverageTests {

    private List<ChangeRecord> sampleChanges;

    @BeforeEach
    void setUp() {
        sampleChanges = new ArrayList<>();
        sampleChanges.add(createChange("Order", "status", "PENDING", "PAID", ChangeType.UPDATE));
        sampleChanges.add(createChange("Order", "amount", "100.00", "200.00", ChangeType.UPDATE));
        sampleChanges.add(createChange("Order", "note", null, "rush order", ChangeType.CREATE));
        sampleChanges.add(createChange("Order", "discount", "10%", null, ChangeType.DELETE));
    }

    private ChangeRecord createChange(String objectName, String fieldName,
                                      String oldValue, String newValue,
                                      ChangeType changeType) {
        return ChangeRecord.of(objectName, fieldName, oldValue, newValue, changeType);
    }

    private ChangeRecord createFullChange(String objectName, String fieldName,
                                         Object oldValue, Object newValue,
                                         ChangeType changeType) {
        return ChangeRecord.builder()
                .objectName(objectName)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .changeType(changeType)
                .sessionId("s1")
                .taskPath("Main/Sub")
                .valueType("java.lang.String")
                .valueKind("STRING")
                .valueRepr("old→new")
                .reprOld("old")
                .reprNew("new")
                .build();
    }

    // ── ChangeExporter.ExportConfig ──

    @Nested
    @DisplayName("ExportConfig")
    class ExportConfigTests {

        @Test
        @DisplayName("DEFAULT config exists")
        void defaultConfig_exists() {
            assertThat(ChangeExporter.ExportConfig.DEFAULT).isNotNull();
        }

        @Test
        @DisplayName("all getters and setters")
        void configGettersSetters() {
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setShowTimestamp(true);
            config.setPrettyPrint(false);
            config.setIncludeSensitiveInfo(true);
            config.setMaxValueLength(50);
            assertThat(config.isShowTimestamp()).isTrue();
            assertThat(config.isPrettyPrint()).isFalse();
            assertThat(config.isIncludeSensitiveInfo()).isTrue();
            assertThat(config.getMaxValueLength()).isEqualTo(50);
        }
    }

    // ── ChangeJsonExporter ──

    @Nested
    @DisplayName("ChangeJsonExporter — Deep")
    class ChangeJsonExporterDeepTests {

        @Test
        @DisplayName("null changes → empty JSON")
        void nullChanges_returnsEmptyJson() {
            ChangeJsonExporter exporter = new ChangeJsonExporter();
            String json = exporter.format(null);
            assertThat(json).isEqualTo("{\"changes\":[]}");
        }

        @Test
        @DisplayName("COMPAT mode with full ChangeRecord")
        void compatMode_fullRecord() {
            ChangeRecord full = createFullChange("User", "email", "a@b.com", "c@d.com", ChangeType.UPDATE);
            ChangeJsonExporter exporter = new ChangeJsonExporter(ChangeJsonExporter.ExportMode.COMPAT);
            String json = exporter.format(List.of(full));
            assertThat(json).contains("User").contains("email");
        }

        @Test
        @DisplayName("ENHANCED mode with metadata")
        void enhancedMode_metadata() {
            ChangeJsonExporter exporter = new ChangeJsonExporter(ChangeJsonExporter.ExportMode.ENHANCED);
            String json = exporter.format(sampleChanges);
            assertThat(json).contains("metadata").contains("timestamp").contains("version");
        }

        @Test
        @DisplayName("null mode → COMPAT")
        void nullMode_usesCompat() {
            ChangeJsonExporter exporter = new ChangeJsonExporter(null);
            String json = exporter.format(sampleChanges);
            assertThat(json).contains("changes");
        }

        @Test
        @DisplayName("sensitive field masking")
        void sensitiveField_masking() {
            List<ChangeRecord> changes = List.of(
                    createChange("User", "password", "old", "new", ChangeType.UPDATE)
            );
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setIncludeSensitiveInfo(false);
            ChangeJsonExporter exporter = new ChangeJsonExporter(ChangeJsonExporter.ExportMode.ENHANCED);
            String json = exporter.format(changes, config);
            assertThat(json).contains("[MASKED]");
        }

        @Test
        @DisplayName("includeSensitiveInfo bypasses masking")
        void includeSensitiveInfo_bypassesMasking() {
            List<ChangeRecord> changes = List.of(
                    createChange("User", "password", "secret123", "newSecret", ChangeType.UPDATE)
            );
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setIncludeSensitiveInfo(true);
            ChangeJsonExporter exporter = new ChangeJsonExporter(ChangeJsonExporter.ExportMode.ENHANCED);
            String json = exporter.format(changes, config);
            assertThat(json).contains("secret123");
        }

        @Test
        @DisplayName("showTimestamp in output")
        void showTimestamp_inOutput() {
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setShowTimestamp(true);
            ChangeJsonExporter exporter = new ChangeJsonExporter();
            String json = exporter.format(sampleChanges, config);
            assertThat(json).contains("timestamp");
        }

        @Test
        @DisplayName("escapeJsonString — control chars")
        void escapeJsonString_controlChars() {
            List<ChangeRecord> changes = List.of(
                    createChange("Obj", "x", "a\nb\tc\r", "d\"e\\f", ChangeType.UPDATE)
            );
            ChangeJsonExporter exporter = new ChangeJsonExporter();
            String json = exporter.format(changes);
            assertThat(json).isNotBlank();
        }
    }

    // ── ChangeCsvExporter ──

    @Nested
    @DisplayName("ChangeCsvExporter — Deep")
    class ChangeCsvExporterDeepTests {

        @Test
        @DisplayName("withSeparator")
        void withSeparator() {
            ChangeCsvExporter exporter = new ChangeCsvExporter().withSeparator(";");
            String csv = exporter.format(sampleChanges);
            assertThat(csv).contains(";");
        }

        @Test
        @DisplayName("withQuote")
        void withQuote() {
            ChangeCsvExporter exporter = new ChangeCsvExporter().withQuote("'");
            List<ChangeRecord> changes = List.of(
                    createChange("Obj", "desc", "a,b", "c,d", ChangeType.UPDATE)
            );
            String csv = exporter.format(changes);
            assertThat(csv).isNotBlank();
        }

        @Test
        @DisplayName("withLineSeparator")
        void withLineSeparator() {
            ChangeCsvExporter exporter = new ChangeCsvExporter().withLineSeparator("\r\n");
            String csv = exporter.format(sampleChanges);
            assertThat(csv).contains("\r\n");
        }

        @Test
        @DisplayName("withHeader false")
        void withHeaderFalse() {
            ChangeCsvExporter exporter = new ChangeCsvExporter().withHeader(false);
            String csv = exporter.format(sampleChanges);
            assertThat(csv).doesNotContain("Type");
        }

        @Test
        @DisplayName("forExcel")
        void forExcel() {
            ChangeCsvExporter exporter = ChangeCsvExporter.forExcel();
            String csv = exporter.format(sampleChanges);
            assertThat(csv).contains("\r\n");
        }

        @Test
        @DisplayName("maxValueLength truncation")
        void maxValueLength_truncation() {
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setMaxValueLength(5);
            List<ChangeRecord> changes = List.of(
                    createChange("Obj", "long", "1234567890", "abcdefghij", ChangeType.UPDATE)
            );
            ChangeCsvExporter exporter = new ChangeCsvExporter();
            String csv = exporter.format(changes, config);
            assertThat(csv).contains("...");
        }

        @Test
        @DisplayName("maskSensitiveInfo — credit card")
        void maskSensitiveInfo_creditCard() {
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setIncludeSensitiveInfo(false);
            List<ChangeRecord> changes = List.of(
                    createChange("Payment", "card", "1234-5678-9012-3456", "new", ChangeType.UPDATE)
            );
            ChangeCsvExporter exporter = new ChangeCsvExporter();
            String csv = exporter.format(changes, config);
            assertThat(csv).contains("****");
        }

        @Test
        @DisplayName("timestamp zero")
        void timestampZero() {
            ChangeRecord r = ChangeRecord.builder()
                    .objectName("X").fieldName("f").oldValue("a").newValue("b")
                    .changeType(ChangeType.UPDATE).timestamp(0).build();
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setShowTimestamp(true);
            ChangeCsvExporter exporter = new ChangeCsvExporter();
            String csv = exporter.format(List.of(r), config);
            assertThat(csv).isNotBlank();
        }
    }

    // ── ChangeConsoleExporter ──

    @Nested
    @DisplayName("ChangeConsoleExporter — Deep")
    class ChangeConsoleExporterDeepTests {

        @Test
        @DisplayName("MOVE change type")
        void moveChangeType() {
            ChangeRecord move = ChangeRecord.builder()
                    .objectName("List").fieldName("item").oldValue("a").newValue("a")
                    .changeType(ChangeType.MOVE).build();
            ChangeConsoleExporter exporter = new ChangeConsoleExporter();
            String text = exporter.format(List.of(move));
            assertThat(text).contains("MOVE");
        }

        @Test
        @DisplayName("sensitive field masking")
        void sensitiveField_masking() {
            List<ChangeRecord> changes = List.of(
                    createChange("User", "secret", "x", "y", ChangeType.UPDATE)
            );
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setIncludeSensitiveInfo(false);
            ChangeConsoleExporter exporter = new ChangeConsoleExporter();
            String text = exporter.format(changes, config);
            assertThat(text).contains("[MASKED]");
        }

        @Test
        @DisplayName("reprOld/reprNew used when available")
        void reprUsedWhenAvailable() {
            ChangeRecord r = createFullChange("X", "f", "old", "new", ChangeType.UPDATE);
            ChangeConsoleExporter exporter = new ChangeConsoleExporter();
            String text = exporter.format(List.of(r));
            assertThat(text).contains("old").contains("new");
        }

        @Test
        @DisplayName("maxValueLength truncation")
        void maxValueLength_truncation() {
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setMaxValueLength(3);
            List<ChangeRecord> changes = List.of(
                    createChange("X", "f", "abcdef", "ghijkl", ChangeType.UPDATE)
            );
            ChangeConsoleExporter exporter = new ChangeConsoleExporter();
            String text = exporter.format(changes, config);
            assertThat(text).contains("...");
        }
    }

    // ── ChangeXmlExporter ──

    @Nested
    @DisplayName("ChangeXmlExporter — Deep")
    class ChangeXmlExporterDeepTests {

        @Test
        @DisplayName("sessionId and taskPath")
        void sessionIdTaskPath() {
            ChangeRecord r = createFullChange("X", "f", "a", "b", ChangeType.UPDATE);
            ChangeXmlExporter exporter = new ChangeXmlExporter();
            String xml = exporter.format(List.of(r));
            assertThat(xml).contains("sessionId").contains("taskPath");
        }

        @Test
        @DisplayName("control char escape")
        void controlCharEscape() {
            List<ChangeRecord> changes = List.of(
                    createChange("Obj", "x", "a\u0001b", "c\u001fd", ChangeType.UPDATE)
            );
            ChangeXmlExporter exporter = new ChangeXmlExporter();
            String xml = exporter.format(changes);
            assertThat(xml).doesNotContain("\u0001");
        }

        @Test
        @DisplayName("empty changes")
        void emptyChanges() {
            ChangeXmlExporter exporter = new ChangeXmlExporter();
            String xml = exporter.format(Collections.emptyList());
            assertThat(xml).contains("<?xml").contains("count=\"0\"");
        }
    }

    // ── ChangeMapExporter ──

    @Nested
    @DisplayName("ChangeMapExporter — Deep")
    class ChangeMapExporterDeepTests {

        @Test
        @DisplayName("export static with config")
        void exportWithConfig() {
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setIncludeSensitiveInfo(true);
            Map<String, Object> result = ChangeMapExporter.export(sampleChanges, config);
            assertThat(result).containsKey("changes").containsKey("statistics").containsKey("count");
        }

        @Test
        @DisplayName("exportGroupedByObject with config")
        void exportGroupedByObjectWithConfig() {
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setShowTimestamp(true);
            Map<String, List<Map<String, Object>>> result =
                    ChangeMapExporter.exportGroupedByObject(sampleChanges, config);
            assertThat(result).containsKey("Order");
        }

        @Test
        @DisplayName("statistics byType")
        void statisticsByType() {
            Map<String, Object> result = ChangeMapExporter.export(sampleChanges);
            @SuppressWarnings("unchecked")
            Map<String, Integer> byType = (Map<String, Integer>)
                    ((Map<String, Object>) result.get("statistics")).get("byType");
            assertThat(byType).containsKey("UPDATE").containsKey("CREATE").containsKey("DELETE");
        }

        @Test
        @DisplayName("sensitive masking in export")
        void sensitiveMaskingInExport() {
            List<ChangeRecord> changes = List.of(
                    createChange("User", "password", "p1", "p2", ChangeType.UPDATE)
            );
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setIncludeSensitiveInfo(false);
            Map<String, Object> result = ChangeMapExporter.export(changes, config);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> changeList = (List<Map<String, Object>>) result.get("changes");
            assertThat(changeList.get(0)).containsValue("[MASKED]");
        }

        @Test
        @DisplayName("objectName null → unknown")
        void objectNameNull_unknown() {
            ChangeRecord r = ChangeRecord.of(null, "f", "a", "b", ChangeType.UPDATE);
            Map<String, List<Map<String, Object>>> result =
                    ChangeMapExporter.exportGroupedByObject(List.of(r));
            assertThat(result).containsKey("unknown");
        }
    }

    // ── StreamingChangeExporter ──

    @Nested
    @DisplayName("StreamingChangeExporter — Streaming")
    class StreamingExporterTests {

        @Test
        @DisplayName("StreamingXmlExporter — exportToStream")
        void streamingXml_exportToStream() throws Exception {
            Iterator<ChangeRecord> it = sampleChanges.iterator();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            StreamingChangeExporter.StreamingXmlExporter exporter =
                    new StreamingChangeExporter.StreamingXmlExporter();
            exporter.exportToStream(it, out, ChangeExporter.ExportConfig.DEFAULT);
            String xml = out.toString(StandardCharsets.UTF_8);
            assertThat(xml).contains("<?xml").contains("changeRecords").contains("Order");
        }

        @Test
        @DisplayName("StreamingXmlExporter — showTimestamp")
        void streamingXml_showTimestamp() throws Exception {
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setShowTimestamp(true);
            Iterator<ChangeRecord> it = sampleChanges.iterator();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new StreamingChangeExporter.StreamingXmlExporter()
                    .exportToStream(it, out, config);
            String xml = out.toString(StandardCharsets.UTF_8);
            assertThat(xml).contains("timestamp");
        }

        @Test
        @DisplayName("StreamingXmlExporter — exportToFile")
        void streamingXml_exportToFile(@TempDir File dir) throws Exception {
            File file = new File(dir, "changes.xml");
            new StreamingChangeExporter.StreamingXmlExporter()
                    .exportToFile(sampleChanges.iterator(), file, ChangeExporter.ExportConfig.DEFAULT);
            assertThat(file).exists();
            assertThat(file.length()).isGreaterThan(0);
        }

        @Test
        @DisplayName("StreamingCsvExporter — default")
        void streamingCsv_default() throws Exception {
            Iterator<ChangeRecord> it = sampleChanges.iterator();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new StreamingChangeExporter.StreamingCsvExporter()
                    .exportToStream(it, out, ChangeExporter.ExportConfig.DEFAULT);
            String csv = out.toString(StandardCharsets.UTF_8);
            assertThat(csv).contains("ChangeType").contains("ObjectName").contains("Order");
        }

        @Test
        @DisplayName("StreamingCsvExporter — no header")
        void streamingCsv_noHeader() throws Exception {
            StreamingChangeExporter.StreamingCsvExporter exporter =
                    new StreamingChangeExporter.StreamingCsvExporter(",", false);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exporter.exportToStream(sampleChanges.iterator(), out,
                    ChangeExporter.ExportConfig.DEFAULT);
            String csv = out.toString(StandardCharsets.UTF_8);
            assertThat(csv).doesNotContain("ChangeType");
        }

        @Test
        @DisplayName("StreamingCsvExporter — value with comma")
        void streamingCsv_valueWithComma() throws Exception {
            List<ChangeRecord> changes = List.of(
                    createChange("Obj", "desc", "a,b,c", "x,y,z", ChangeType.UPDATE)
            );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new StreamingChangeExporter.StreamingCsvExporter()
                    .exportToStream(changes.iterator(), out, ChangeExporter.ExportConfig.DEFAULT);
            String csv = out.toString(StandardCharsets.UTF_8);
            assertThat(csv).contains("\"");
        }

        @Test
        @DisplayName("StreamingJsonExporter — JSON Lines")
        void streamingJson_jsonLines() throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new StreamingChangeExporter.StreamingJsonExporter()
                    .exportToStream(sampleChanges.iterator(), out,
                            ChangeExporter.ExportConfig.DEFAULT);
            String json = out.toString(StandardCharsets.UTF_8);
            assertThat(json).contains("\"type\"").contains("\"object\"").contains("Order");
        }

        @Test
        @DisplayName("StreamingJsonExporter — maxValueLength")
        void streamingJson_maxValueLength() throws Exception {
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setMaxValueLength(3);
            List<ChangeRecord> changes = List.of(
                    createChange("X", "f", "longold", "longnew", ChangeType.UPDATE)
            );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new StreamingChangeExporter.StreamingJsonExporter()
                    .exportToStream(changes.iterator(), out, config);
            String json = out.toString(StandardCharsets.UTF_8);
            assertThat(json).contains("...");
        }

        @Test
        @DisplayName("processInBatches")
        void processInBatches() {
            List<ChangeRecord> collected = new ArrayList<>();
            new StreamingChangeExporter.StreamingXmlExporter()
                    .processInBatches(sampleChanges.iterator(), collected::add, 2);
            assertThat(collected).hasSize(4);
        }

        @Test
        @DisplayName("processInBatches — partial batch")
        void processInBatches_partial() {
            List<ChangeRecord> collected = new ArrayList<>();
            new StreamingChangeExporter.StreamingXmlExporter()
                    .processInBatches(List.of(sampleChanges.get(0)).iterator(), collected::add, 5);
            assertThat(collected).hasSize(1);
        }
    }
}
