package com.syy.taskflowinsight.exporter.json;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonExporterTests {

    @Test
    void export_compact_containsSessionThreadFieldsAndRoot() {
        // arrange
        Session session = Session.create("root").activate();
        TaskNode root = session.getRootTask();
        root.addInfo("hello");

        JsonExporter exporter = new JsonExporter(JsonExporter.ExportMode.COMPAT);

        // act
        String json = exporter.export(session);

        // assert
        assertThat(json).contains("\"sessionId\":\"" + session.getSessionId() + "\"");
        // threadId numeric if possible
        long expectedThreadId = Long.parseLong(session.getThreadId());
        assertThat(json).contains("\"threadId\":" + expectedThreadId);
        assertThat(json).contains("\"threadName\":\"" + session.getThreadName() + "\"");
        assertThat(json).contains("\"root\":");
        assertThat(json).contains("\"messages\":[");

        // durations
        assertThat(json).contains("\"selfDurationMs\":");
        assertThat(json).contains("\"accDurationMs\":");
    }
}
