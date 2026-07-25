package com.syy.taskflowinsight.exporter.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** All-in-One 消费者对 Core canonical V2 JSON 公共入口的集成契约。 */
class JsonExporterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NULL_SESSION_JSON =
            "{\"error\":\"No session data available\"}";
    private final JsonExporter exporter = new JsonExporter();

    @Test
    @DisplayName("无参 exporter 发布 exact canonical V2 顶层")
    void shouldPublishCanonicalV2() throws Exception {
        Session session = Session.create("consumer-root");
        session.getRootTask().addInfo("message");

        JsonNode document = MAPPER.readTree(exporter.export(session));

        assertThat(fieldNames(document)).containsExactly(
                "schemaVersion", "captureEpochMillis", "session",
                "statistics", "rootTask", "truncated");
        assertThat(document.path("schemaVersion").intValue()).isEqualTo(2);
        assertThat(document.path("session").path("id").textValue())
                .isEqualTo(session.getSessionId());
        assertThat(document.path("session").path("threadId").textValue())
                .isEqualTo(session.getThreadId());
        assertThat(document.path("rootTask").path("name").textValue())
                .isEqualTo("consumer-root");
        assertThat(document.path("statistics").path("totalMessages").intValue())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Message 使用 canonical display label、severity 与 nullable key")
    void shouldPublishCanonicalMessages() throws Exception {
        Session session = Session.create("message-root");
        TaskNode root = session.getRootTask();
        root.addInfo("Info message");
        root.addError("Error message");

        JsonNode messages = MAPPER.readTree(exporter.export(session))
                .path("rootTask").path("messages");

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).path("type").textValue()).isEqualTo("业务流程");
        assertThat(messages.get(0).path("severity").textValue()).isEqualTo("INFO");
        assertThat(messages.get(0).path("customLabel").isNull()).isTrue();
        assertThat(messages.get(1).path("displayLabel").textValue()).contains("异常提示");
        assertThat(messages.get(1).path("severity").textValue()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("特殊字符与 Unicode 通过真实 parser")
    void shouldEscapeSpecialCharacters() {
        Session session = Session.create("任务 \"root\" \\ 😀");
        session.getRootTask().addInfo("line1\nline2\tmiddle\bback\fnext");

        String json = exporter.export(session);

        assertThatCode(() -> MAPPER.readTree(json)).doesNotThrowAnyException();
        assertThat(json)
                .contains("\\\"root\\\"")
                .contains("line1\\nline2\\tmiddle\\bback\\fnext")
                .contains("任务");
    }

    @Test
    @DisplayName("String 与 Writer 对同一完成态 Session 发布同一 canonical tree")
    void shouldKeepStringAndWriterTreesEquivalent() throws Exception {
        Session session = completedSession();
        String stringJson = exporter.export(session);
        StringWriter writer = new StringWriter();

        exporter.export(session, writer);

        ObjectNode stringTree = (ObjectNode) MAPPER.readTree(stringJson);
        ObjectNode writerTree = (ObjectNode) MAPPER.readTree(writer.toString());
        stringTree.remove("captureEpochMillis");
        writerTree.remove("captureEpochMillis");
        assertThat(writerTree).isEqualTo(stringTree);
    }

    @Test
    @DisplayName("null Session 的 String 与 Writer fallback 完全一致")
    void shouldPreserveNullSessionFallback() throws Exception {
        StringWriter writer = new StringWriter();

        exporter.export(null, writer);

        assertThat(exporter.export(null)).isEqualTo(NULL_SESSION_JSON);
        assertThat(writer.toString()).isEqualTo(NULL_SESSION_JSON);
    }

    @Test
    @DisplayName("运行态 nullable 与 empty keys 不省略")
    void shouldKeepNullableAndEmptyKeys() throws Exception {
        JsonNode document = MAPPER.readTree(exporter.export(Session.create("running-root")));

        assertThat(document.path("session").path("endEpochMillis").isNull()).isTrue();
        assertThat(document.path("session").path("durationNanos").isNull()).isTrue();
        JsonNode root = document.path("rootTask");
        assertThat(root.path("endEpochMillis").isNull()).isTrue();
        assertThat(root.path("durationNanos").isNull()).isTrue();
        assertThat(root.path("messages").isEmpty()).isTrue();
        assertThat(root.path("attributes").isEmpty()).isTrue();
        assertThat(root.path("tags").isEmpty()).isTrue();
        assertThat(root.path("children").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("1000 节点 consumer 输出保持可解析")
    void shouldSerializeOneThousandNodes() {
        Session session = Session.create("wide-root");
        for (int index = 1; index < 1000; index++) {
            session.getRootTask().createChild("node-" + index);
        }

        assertThatCode(() -> MAPPER.readTree(exporter.export(session)))
                .doesNotThrowAnyException();
    }

    private static Session completedSession() {
        Session session = Session.create("completed-root");
        TaskNode child = session.getRootTask().createChild("child");
        child.addInfo("done");
        child.complete();
        session.getRootTask().complete();
        session.activate();
        session.complete();
        return session;
    }

    private static List<String> fieldNames(JsonNode node) {
        return node.properties().stream().map(entry -> entry.getKey()).toList();
    }
}
