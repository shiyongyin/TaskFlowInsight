package com.syy.taskflowinsight.exporter.json;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link JsonExporter} 单元测试
 *
 * <p>覆盖 COMPAT/ENHANCED 模式、特殊字符转义和边界条件。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class JsonExporterTest {

    private final JsonExporter compatExporter = new JsonExporter();
    private final JsonExporter enhancedExporter = new JsonExporter(JsonExporter.ExportMode.ENHANCED);

    // ==================== 基本功能 ====================

    @Test
    @DisplayName("export - null session 返回 error JSON")
    void exportNullSession() {
        String json = compatExporter.export(null);
        assertThat(json).contains("error");
    }

    @Test
    @DisplayName("export - COMPAT 模式基本字段")
    void exportCompatMode() {
        Session session = createTestSession();
        String json = compatExporter.export(session);

        assertThat(json).contains("sessionId");
        assertThat(json).contains("status");
        assertThat(json).contains("createdAt");
        assertThat(json).contains("root");
        assertThat(json).doesNotContain("statistics");
    }

    @Test
    @DisplayName("export - ENHANCED 模式包含 statistics")
    void exportEnhancedMode() {
        Session session = createTestSession();
        String json = enhancedExporter.export(session);

        assertThat(json).contains("statistics");
        assertThat(json).contains("totalTasks");
        assertThat(json).contains("maxDepth");
        assertThat(json).contains("createdAtNanos");
    }

    @Test
    @DisplayName("export - 包含嵌套子任务")
    void exportNestedTasks() {
        Session session = Session.create("root");
        TaskNode root = session.getRootTask();
        TaskNode child = root.createChild("child");
        child.addInfo("子任务消息");
        child.complete();
        root.complete();
        session.activate();
        session.complete();

        String json = compatExporter.export(session);
        assertThat(json).contains("child");
        assertThat(json).contains("子任务消息");
        assertThat(json).contains("children");
    }

    @Test
    @DisplayName("export - 包含消息类型")
    void exportWithMessageTypes() {
        Session session = Session.create("root");
        TaskNode root = session.getRootTask();
        root.addMessage("info msg", MessageType.PROCESS);
        root.addMessage("alert msg", MessageType.ALERT);
        root.complete();
        session.activate();
        session.complete();

        String json = compatExporter.export(session);
        assertThat(json).contains("PROCESS");
        assertThat(json).contains("ALERT");
        assertThat(json).contains("info msg");
        assertThat(json).contains("alert msg");
    }

    // ==================== 特殊字符转义 ====================

    @Test
    @DisplayName("export - 双引号正确转义")
    void exportEscapesQuotes() {
        Session session = Session.create("test");
        session.getRootTask().addInfo("value with \"quotes\"");
        session.getRootTask().complete();
        session.activate();
        session.complete();

        String json = compatExporter.export(session);
        assertThat(json).contains("\\\"quotes\\\"");
    }

    @Test
    @DisplayName("export - 反斜杠正确转义")
    void exportEscapesBackslash() {
        Session session = Session.create("test");
        session.getRootTask().addInfo("path\\to\\file");
        session.getRootTask().complete();
        session.activate();
        session.complete();

        String json = compatExporter.export(session);
        assertThat(json).contains("path\\\\to\\\\file");
    }

    @Test
    @DisplayName("export - 换行符正确转义")
    void exportEscapesNewline() {
        Session session = Session.create("test");
        session.getRootTask().addInfo("line1\nline2");
        session.getRootTask().complete();
        session.activate();
        session.complete();

        String json = compatExporter.export(session);
        assertThat(json).contains("line1\\nline2");
    }

    @Test
    @DisplayName("export - Tab 正确转义")
    void exportEscapesTab() {
        Session session = Session.create("test");
        session.getRootTask().addInfo("col1\tcol2");
        session.getRootTask().complete();
        session.activate();
        session.complete();

        String json = compatExporter.export(session);
        assertThat(json).contains("col1\\tcol2");
    }

    @Test
    @DisplayName("export - Unicode 中文正常输出")
    void exportHandlesUnicode() {
        Session session = Session.create("测试会话");
        session.getRootTask().addInfo("中文消息");
        session.getRootTask().complete();
        session.activate();
        session.complete();

        String json = compatExporter.export(session);
        assertThat(json).contains("中文消息");
        assertThat(json).contains("测试会话");
    }

    // ==================== null 模式构造 ====================

    @Test
    @DisplayName("构造 - null 模式默认 COMPAT")
    void nullModeDefaultsToCompat() {
        JsonExporter exporter = new JsonExporter(null);
        Session session = createTestSession();
        String json = exporter.export(session);
        assertThat(json).contains("createdAt");
        assertThat(json).doesNotContain("statistics");
    }

    // ==================== 辅助方法 ====================

    private Session createTestSession() {
        Session session = Session.create("testRoot");
        session.getRootTask().addInfo("test message");
        session.getRootTask().complete();
        session.activate();
        session.complete();
        return session;
    }
}
