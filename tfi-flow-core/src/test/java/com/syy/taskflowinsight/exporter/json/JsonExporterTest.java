package com.syy.taskflowinsight.exporter.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.taskflowinsight.internal.FlowConfigDefaults;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.SessionExportSnapshot;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link JsonExporter} 的 canonical V2 主入口契约测试。 */
class JsonExporterTest {

    private static final String NULL_SESSION_JSON =
            "{\"error\":\"No session data available\"}";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JsonExporter exporter = new JsonExporter();

    @Test
    @DisplayName("export - null Session 保留 exact error JSON")
    void should_preserve_exact_null_session_json() throws Exception {
        StringWriter writer = new StringWriter();

        exporter.export(null, writer);

        assertThat(exporter.export(null)).isEqualTo(NULL_SESSION_JSON);
        assertThat(writer.toString()).isEqualTo(NULL_SESSION_JSON);
    }

    @Test
    @DisplayName("export - 只发布 exact canonical V2 顶层与 nullable/empty keys")
    void should_publish_exact_canonical_v2_shape() throws Exception {
        Session session = Session.create("json-root");
        session.getRootTask().addInfo("message");

        JsonNode document = MAPPER.readTree(exporter.export(session));

        assertThat(fieldNames(document)).containsExactly(
                "schemaVersion", "captureEpochMillis", "session",
                "statistics", "rootTask", "truncated");
        assertThat(document.path("schemaVersion").intValue()).isEqualTo(2);
        assertThat(fieldNames(document.path("session"))).containsExactly(
                "id", "name", "status", "threadId", "threadName",
                "startEpochMillis", "endEpochMillis", "durationNanos");
        assertThat(document.path("session").path("id").textValue())
                .isEqualTo(session.getSessionId());
        assertThat(document.path("session").path("name").textValue()).isEqualTo("json-root");
        assertThat(document.path("session").path("threadId").textValue())
                .isEqualTo(session.getThreadId());
        assertThat(document.path("session").path("threadName").textValue())
                .isEqualTo(session.getThreadName());
        assertThat(document.path("session").path("endEpochMillis").isNull()).isTrue();
        assertThat(document.path("session").path("durationNanos").isNull()).isTrue();
        assertThat(fieldNames(document.path("statistics"))).containsExactly(
                "totalTasks", "maxDepth", "totalMessages");
        assertThat(document.path("statistics").path("totalMessages").intValue()).isEqualTo(1);

        JsonNode root = document.path("rootTask");
        assertThat(fieldNames(root)).containsExactly(
                "id", "name", "path", "threadName", "depth", "sequence", "status",
                "startEpochMillis", "endEpochMillis", "durationNanos", "selfDurationNanos",
                "accumulatedDurationNanos", "messages", "attributes", "tags", "children",
                "childrenTruncated");
        assertThat(root.path("attributes").isEmpty()).isTrue();
        assertThat(root.path("tags").isEmpty()).isTrue();
        assertThat(root.path("children").isEmpty()).isTrue();
        assertThat(fieldNames(root.path("messages").get(0))).containsExactly(
                "type", "displayLabel", "severity", "customLabel",
                "content", "timestampEpochMillis", "threadName");
    }

    @Test
    @DisplayName("export - String 与 Writer 都生成 parser 可解析 JSON")
    void should_generate_parser_readable_json_for_both_public_paths() throws Exception {
        Session session = Session.create("escaped-\"root\"");
        session.getRootTask().addInfo("line1\nline2\t\\tail");
        StringWriter writer = new StringWriter();

        exporter.export(session, writer);

        assertThatCode(() -> MAPPER.readTree(exporter.export(session))).doesNotThrowAnyException();
        assertThatCode(() -> MAPPER.readTree(writer.toString())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("export - non-finite 与 unsupported 使用 canonical tagged maps")
    void should_encode_tagged_special_values_without_user_callbacks() throws Exception {
        Session session = Session.create("tagged-root");
        TaskNode root = session.getRootTask();
        CallbackTrap trap = new CallbackTrap();
        root.addAttribute("floatNaN", Float.NaN);
        root.addAttribute("doubleInfinity", Double.POSITIVE_INFINITY);
        root.addAttribute("unsupported", trap);

        JsonNode attributes = MAPPER.readTree(exporter.export(session))
                .path("rootTask").path("attributes");

        assertThat(attributes.path("floatNaN").toString())
                .isEqualTo("{\"kind\":\"nonFiniteNumber\",\"numberType\":\"FLOAT\",\"value\":\"NaN\"}");
        assertThat(attributes.path("doubleInfinity").toString())
                .isEqualTo("{\"kind\":\"nonFiniteNumber\",\"numberType\":\"DOUBLE\",\"value\":\"Infinity\"}");
        assertThat(attributes.path("unsupported").toString())
                .isEqualTo("{\"kind\":\"unsupported\",\"className\":\""
                        + CallbackTrap.class.getName() + "\"}");
        assertThat(trap.callbackCount).isZero();
    }

    @Test
    @DisplayName("export - Character 按 ADR-008 编码为单字符 JSON string")
    void should_encode_character_as_single_character_json_string() throws Exception {
        Session session = Session.create("character-root");
        session.getRootTask().addAttribute("character", 'x');

        JsonNode character = MAPPER.readTree(exporter.export(session))
                .path("rootTask").path("attributes").path("character");

        assertThat(character.isTextual()).isTrue();
        assertThat(character.textValue()).isEqualTo("x");
    }

    @Test
    @DisplayName("export - capture 预算失败不被 String fallback 吞掉")
    void should_propagate_capture_limit_failure_before_rendering() {
        Session session = Session.create("oversized-root");
        int limit = Math.toIntExact(FlowConfigDefaults.MAX_EXPORT_TEXT_CHARS);
        session.getRootTask().addAttribute("oversized", "x".repeat(limit + 1));

        assertThatThrownBy(() -> exporter.export(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Export text character limit exceeded: " + limit);
    }

    @Test
    @DisplayName("API - 4.0 只保留 public no-arg constructor")
    void should_expose_only_the_no_arg_public_constructor() {
        assertThat(JsonExporter.class.getClasses()).isEmpty();
        assertThat(JsonExporter.class.getDeclaredClasses())
                .extracting(Class::getSimpleName)
                .doesNotContain("ExportMode");
        assertThat(List.of(JsonExporter.class.getConstructors()))
                .extracting(Constructor::getParameterCount)
                .containsExactly(0);
    }

    @Test
    @DisplayName("export - 1000 层合法树不依赖递归编码")
    void should_encode_maximum_depth_without_stack_overflow() {
        Session session = Session.create("deep-root");
        TaskNode current = session.getRootTask();
        for (int depth = 1; depth <= FlowConfigDefaults.MAX_EXPORT_DEPTH; depth++) {
            current = current.createChild("level-" + depth);
        }

        String json = exporter.export(session);

        assertThat(json).contains("\"schemaVersion\":2");
        assertThat(json).contains("\"name\":\"level-1000\"");
    }

    @Test
    @DisplayName("capture seam - String 与 Writer 各捕获一次并编码同一 projection")
    void should_capture_once_per_path_and_encode_the_same_snapshot() throws Exception {
        Session session = Session.create("same-snapshot-root");
        session.getRootTask().addInfo("message");
        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
        AtomicInteger captures = new AtomicInteger();
        Function<Session, SessionExportSnapshot> capturer = ignored -> {
            captures.incrementAndGet();
            return snapshot;
        };

        String stringJson = exporter.export(session, capturer);
        StringWriter writer = new StringWriter();
        exporter.export(session, writer, capturer);

        assertThat(captures).hasValue(2);
        assertThat(writer.toString()).isEqualTo(stringJson);
        assertThat(stringJson)
                .isEqualTo(MAPPER.writeValueAsString(snapshot.toCanonicalV2()));
    }

    @Test
    @DisplayName("capture seam - null Session 不调用 capturer")
    void should_skip_capture_for_both_null_paths() throws Exception {
        AtomicInteger captures = new AtomicInteger();
        Function<Session, SessionExportSnapshot> capturer = ignored -> {
            captures.incrementAndGet();
            throw new AssertionError("null Session must not reach capturer");
        };
        StringWriter writer = new StringWriter();

        assertThat(exporter.export(null, capturer)).isEqualTo(NULL_SESSION_JSON);
        exporter.export(null, writer, capturer);

        assertThat(writer.toString()).isEqualTo(NULL_SESSION_JSON);
        assertThat(captures).hasValue(0);
    }

    @Test
    @DisplayName("capture seam - 失败 identity 透传且 Writer 保持空")
    void should_propagate_capture_failure_without_partial_writer_output() {
        Session session = Session.create("failure-root");
        IllegalStateException failure = new IllegalStateException("capture failed");
        Function<Session, SessionExportSnapshot> capturer = ignored -> {
            throw failure;
        };
        StringWriter writer = new StringWriter();

        assertThatThrownBy(() -> exporter.export(session, capturer)).isSameAs(failure);
        assertThatThrownBy(() -> exporter.export(session, writer, capturer)).isSameAs(failure);
        assertThat(writer.toString()).isEmpty();
    }

    @Test
    @DisplayName("capture seam - capture 后的 mutation 不进入输出")
    void should_not_read_mutable_session_after_capture() throws Exception {
        Session session = Session.create("frozen-root");
        TaskNode root = session.getRootTask();
        root.createChild("before-capture");
        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
        root.createChild("after-capture");

        String json = exporter.export(session, ignored -> snapshot);

        assertThat(json).contains("before-capture").doesNotContain("after-capture");
    }

    @Test
    @DisplayName("Writer - I/O failure 原实例传播")
    void should_propagate_writer_io_failure() {
        Session session = Session.create("io-root");
        IOException failure = new IOException("write failed");
        Writer writer = new FailingWriter(failure);

        assertThatThrownBy(() -> exporter.export(session, writer)).isSameAs(failure);
    }

    private static List<String> fieldNames(JsonNode node) {
        return node.properties().stream().map(entry -> entry.getKey()).toList();
    }

    private static final class CallbackTrap {

        private int callbackCount;

        @Override
        public String toString() {
            callbackCount++;
            throw new AssertionError("JSON export must not invoke user callbacks");
        }
    }

    private static final class FailingWriter extends Writer {

        private final IOException failure;

        private FailingWriter(IOException failure) {
            this.failure = failure;
        }

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            throw failure;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
