package com.syy.taskflowinsight.exporter.text;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleExporterTests {

    @Test
    void export_includesSessionAndThreadInfo_andMessageTimestamps() {
        // arrange
        Session session = Session.create("root-task").activate();
        TaskNode root = session.getRootTask();
        root.addInfo("hello-info");
        TaskNode child = root.createChild("child-task");
        child.addError("oops-error");

        ConsoleExporter exporter = new ConsoleExporter();

        // act
        String out = exporter.export(session);

        // assert header contains session id and session thread info
        assertThat(out).contains("Session: " + session.getSessionId());
        assertThat(out).contains("Thread:  " + session.getThreadId());
        assertThat(out).contains("(" + session.getThreadName() + ")");

        // assert messages with timestamps (no tree glyphs in simplified format)
        assertThat(out).contains("root-task");
        assertThat(out).contains("[业务流程 @");
        assertThat(out).contains("hello-info");
        assertThat(out).contains("异常提示 @");
        assertThat(out).contains("oops-error");
        // assert self/acc durations are printed
        assertThat(out).contains("self ");
    }
}
