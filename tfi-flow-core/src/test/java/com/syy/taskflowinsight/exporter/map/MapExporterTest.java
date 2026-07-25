package com.syy.taskflowinsight.exporter.map;

import com.syy.taskflowinsight.internal.FlowConfigDefaults;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.SessionExportSnapshot;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link MapExporter} 的 canonical V2 主入口测试。 */
class MapExporterTest {

    @Test
    @DisplayName("export - null session 返回空 Map")
    void should_return_empty_map_when_session_is_null() {
        assertThat(MapExporter.export(null)).isEmpty();
    }

    @Test
    @DisplayName("export - 主入口直接发布 canonical V2")
    void should_publish_canonical_v2_when_session_exists() {
        Session session = Session.create("map-root");
        session.getRootTask().addInfo("message");

        Map<String, Object> result = MapExporter.export(session);

        assertThat(result.keySet()).containsExactly(
                "schemaVersion", "captureEpochMillis", "session", "statistics", "rootTask", "truncated");
        assertThat(result.get("schemaVersion")).isEqualTo(2).isExactlyInstanceOf(Integer.class);
        assertThat(map(result.get("session")))
                .containsEntry("name", "map-root")
                .containsEntry("status", "RUNNING");
        assertThat(map(result.get("statistics"))).containsEntry("totalMessages", 1);
        assertThat(map(result.get("rootTask"))).containsEntry("name", "map-root");
    }

    @Test
    @DisplayName("capture seam - 非空 session 恰好捕获一次")
    void should_capture_once_when_session_exists() {
        Session session = Session.create("capture-root");
        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
        AtomicInteger captures = new AtomicInteger();

        Map<String, Object> result = invokeExport(session, ignored -> {
            captures.incrementAndGet();
            return snapshot;
        });

        assertThat(captures).as("Map capture count").hasValue(1);
        assertThat(result.get("schemaVersion")).isEqualTo(2);
    }

    @Test
    @DisplayName("capture seam - null session 在 capturer 前返回")
    void should_skip_capture_when_session_is_null() {
        AtomicInteger captures = new AtomicInteger();

        Map<String, Object> result = invokeExport(null, ignored -> {
            captures.incrementAndGet();
            throw new AssertionError("null session must not reach capturer");
        });

        assertThat(result).isEmpty();
        assertThat(captures).as("null Map capture count").hasValue(0);
    }

    @Test
    @DisplayName("capture seam - 只投影 capturer 返回的冻结快照")
    void should_project_frozen_snapshot_when_model_changes_after_capture() {
        Session session = Session.create("frozen-root");
        TaskNode root = session.getRootTask();
        root.createChild("before-capture");
        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
        root.createChild("after-capture");

        Map<String, Object> result = invokeExport(session, ignored -> snapshot);
        List<?> children = list(map(result.get("rootTask")).get("children"));

        assertThat(children).singleElement().satisfies(child ->
                assertThat(map(child)).containsEntry("name", "before-capture"));
        assertThat(result.toString()).doesNotContain("after-capture");
    }

    @Test
    @DisplayName("capture seam - capture failure 保持异常 identity")
    void should_propagate_same_failure_when_capture_fails() {
        Session session = Session.create("failure-root");
        IllegalStateException failure = new IllegalStateException("capture failed");

        assertThatThrownBy(() -> invokeExport(session, ignored -> {
            throw failure;
        })).isSameAs(failure);
    }

    @Test
    @DisplayName("export - text limit+1 在 projection 前失败")
    void should_fail_before_projection_when_text_limit_is_exceeded() {
        Session session = Session.create("oversized-root");
        int limit = Math.toIntExact(FlowConfigDefaults.MAX_EXPORT_TEXT_CHARS);
        session.getRootTask().addAttribute("oversized", "x".repeat(limit + 1));

        assertThatThrownBy(() -> MapExporter.export(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Export text character limit exceeded: " + limit);
    }

    @Test
    @DisplayName("export - message 使用 frozen wire type、severity 与 sibling sequence")
    void should_publish_frozen_message_and_sequence_fields() {
        Session session = Session.create("message-root");
        TaskNode root = session.getRootTask();
        root.addDebug("debug-message");
        root.createChild("first");
        root.createChild("second");

        Map<String, Object> result = MapExporter.export(session);
        Map<String, Object> rootTask = map(result.get("rootTask"));
        Map<String, Object> message = map(list(rootTask.get("messages")).get(0));
        List<?> children = list(rootTask.get("children"));

        assertThat(message)
                .containsEntry("type", "核心指标")
                .containsEntry("displayLabel", "核心指标")
                .containsEntry("severity", "DEBUG")
                .containsEntry("customLabel", null);
        assertThat(map(children.get(0))).containsEntry("sequence", 0);
        assertThat(map(children.get(1))).containsEntry("sequence", 1);
    }

    private static Map<String, Object> invokeExport(
            Session session, Function<Session, SessionExportSnapshot> capturer) {
        return MapExporter.export(session, capturer);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static List<?> list(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<?>) value;
    }
}
