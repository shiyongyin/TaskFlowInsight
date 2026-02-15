package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompareReportGenerator 单元测试
 *
 * <p>验证各格式报告和补丁的正确性、边界条件处理。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("CompareReportGenerator — 报告与补丁生成测试")
class CompareReportGeneratorTests {

    private static final List<FieldChange> SAMPLE_CHANGES = List.of(
            FieldChange.builder()
                    .fieldName("name")
                    .oldValue("Alice")
                    .newValue("Bob")
                    .changeType(ChangeType.UPDATE)
                    .build(),
            FieldChange.builder()
                    .fieldName("email")
                    .oldValue(null)
                    .newValue("bob@example.com")
                    .changeType(ChangeType.CREATE)
                    .build()
    );

    // ──────────────────────────────────────────────────────────────
    //  报告生成
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("报告生成")
    class ReportTests {

        @Test
        @DisplayName("RG-001: 空变更 → 'No changes detected.'")
        void emptyChanges_shouldReturnNoChanges() {
            CompareOptions opts = CompareOptions.builder()
                    .reportFormat(ReportFormat.TEXT).build();
            String report = CompareReportGenerator.generateReport(Collections.emptyList(), opts);
            assertThat(report).isEqualTo("No changes detected.");
        }

        @Test
        @DisplayName("RG-002: TEXT 格式包含字段名和值")
        void textFormat_shouldContainFieldsAndValues() {
            String report = CompareReportGenerator.generateTextReport(SAMPLE_CHANGES);
            assertThat(report).contains("name", "Alice", "Bob", "UPDATE");
            assertThat(report).contains("email", "CREATE");
            assertThat(report).contains("Total changes: 2");
        }

        @Test
        @DisplayName("RG-003: MARKDOWN 格式包含表头")
        void markdownFormat_shouldContainTableHeader() {
            String report = CompareReportGenerator.generateMarkdownReport(SAMPLE_CHANGES);
            assertThat(report).contains("# Change Report");
            assertThat(report).contains("| Field | Old Value | New Value | Type |");
            assertThat(report).contains("| name |");
            assertThat(report).contains("**Total changes:** 2");
        }

        @Test
        @DisplayName("RG-004: JSON 格式可解析")
        void jsonFormat_shouldBeValidJson() {
            String report = CompareReportGenerator.generateJsonReport(SAMPLE_CHANGES);
            assertThat(report).contains("\"changes\"");
            assertThat(report).contains("\"field\": \"name\"");
            assertThat(report).contains("\"total\": 2");
        }

        @Test
        @DisplayName("RG-005: HTML 格式包含表格结构")
        void htmlFormat_shouldContainTableStructure() {
            String report = CompareReportGenerator.generateHtmlReport(SAMPLE_CHANGES);
            assertThat(report).contains("<table>");
            assertThat(report).contains("<th>Field</th>");
            assertThat(report).contains("<td>name</td>");
            assertThat(report).contains("Total changes: 2");
        }

        @Test
        @DisplayName("RG-006: 通过 options 路由正确格式")
        void generateReport_shouldRouteByFormat() {
            CompareOptions mdOpts = CompareOptions.builder()
                    .reportFormat(ReportFormat.MARKDOWN).build();
            String mdReport = CompareReportGenerator.generateReport(SAMPLE_CHANGES, mdOpts);
            assertThat(mdReport).contains("# Change Report");

            CompareOptions jsonOpts = CompareOptions.builder()
                    .reportFormat(ReportFormat.JSON).build();
            String jsonReport = CompareReportGenerator.generateReport(SAMPLE_CHANGES, jsonOpts);
            assertThat(jsonReport).contains("\"changes\"");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  补丁生成
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("补丁生成")
    class PatchTests {

        @Test
        @DisplayName("RG-007: JSON Patch 包含正确操作")
        void jsonPatch_shouldContainCorrectOps() {
            List<FieldChange> changes = List.of(
                    FieldChange.builder()
                            .fieldName("name")
                            .oldValue("Alice")
                            .newValue("Bob")
                            .changeType(ChangeType.UPDATE)
                            .build(),
                    FieldChange.builder()
                            .fieldName("deleted")
                            .oldValue("gone")
                            .changeType(ChangeType.DELETE)
                            .build(),
                    FieldChange.builder()
                            .fieldName("created")
                            .newValue("fresh")
                            .changeType(ChangeType.CREATE)
                            .build()
            );
            String patch = CompareReportGenerator.generateJsonPatch(changes);
            assertThat(patch).contains("\"op\":\"replace\"");
            assertThat(patch).contains("\"op\":\"remove\"");
            assertThat(patch).contains("\"op\":\"add\"");
        }

        @Test
        @DisplayName("RG-008: Merge Patch 包含字段值")
        void mergePatch_shouldContainFieldValues() {
            String patch = CompareReportGenerator.generateMergePatch(SAMPLE_CHANGES);
            assertThat(patch).contains("\"name\"");
            assertThat(patch).contains("\"Bob\"");
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  JSON 工具方法
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("JSON 序列化工具")
    class JsonUtilTests {

        @Test
        @DisplayName("RG-009: null 值序列化为 'null'")
        void nullValue_shouldSerializeAsNull() {
            assertThat(CompareReportGenerator.toJsonValue(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("RG-010: 数字直接序列化")
        void number_shouldSerializeDirectly() {
            assertThat(CompareReportGenerator.toJsonValue(42)).isEqualTo("42");
            assertThat(CompareReportGenerator.toJsonValue(3.14)).isEqualTo("3.14");
        }

        @Test
        @DisplayName("RG-011: 字符串加引号并转义")
        void string_shouldBeQuotedAndEscaped() {
            assertThat(CompareReportGenerator.toJsonValue("hello")).isEqualTo("\"hello\"");
            assertThat(CompareReportGenerator.toJsonValue("say \"hi\"")).isEqualTo("\"say \\\"hi\\\"\"");
        }

        @Test
        @DisplayName("RG-012: Boolean 直接序列化")
        void boolean_shouldSerializeDirectly() {
            assertThat(CompareReportGenerator.toJsonValue(true)).isEqualTo("true");
            assertThat(CompareReportGenerator.toJsonValue(false)).isEqualTo("false");
        }
    }
}
