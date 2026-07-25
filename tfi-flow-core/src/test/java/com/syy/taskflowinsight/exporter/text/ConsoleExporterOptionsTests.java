package com.syy.taskflowinsight.exporter.text;

import com.syy.taskflowinsight.internal.FlowConfigDefaults;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.SessionExportSnapshot;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static com.syy.taskflowinsight.exporter.text.ConsoleExportOptions.ConsoleStyle.SIMPLE;
import static com.syy.taskflowinsight.exporter.text.ConsoleExportOptions.ConsoleStyle.TREE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsoleExporterOptionsTests {

    @Test
    void optionsMatrixKeepsStyleAndTimestampOrthogonal() {
        Session session = Session.create("matrix-root");
        session.getRootTask().addInfo("matrix-process");
        session.getRootTask().addWarn("matrix-warn");
        session.getRootTask().addError("matrix-error");
        ConsoleExporter exporter = new ConsoleExporter();

        String treeWithout = exporter.export(session, new ConsoleExportOptions(TREE, false));
        String treeWith = exporter.export(session, new ConsoleExportOptions(TREE, true));
        String simpleWithout = exporter.export(session, new ConsoleExportOptions(SIMPLE, false));
        String simpleWith = exporter.export(session, new ConsoleExportOptions(SIMPLE, true));

        assertMessagesWithoutTimestamp(treeWithout)
                .contains("\uD83D\uDCCB ")
                .doesNotContain(", self ");
        assertMessagesWithTimestamp(treeWith)
                .contains("\uD83D\uDCCB ")
                .doesNotContain(", self ");
        assertMessagesWithoutTimestamp(simpleWithout)
                .contains(", self ")
                .doesNotContain("\uD83D\uDCCB ");
        assertMessagesWithTimestamp(simpleWith)
                .contains(", self ")
                .doesNotContain("\uD83D\uDCCB ");
    }

    @Test
    void printAdaptersKeepTheirDeclaredStyleAndTimestampSemantics() {
        Session session = sessionWithMessage("print-matrix-message");
        ConsoleExporter exporter = new ConsoleExporter();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream original = System.out;

        try (PrintStream captured = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            exporter.print(session, captured);
            String treeToStream = bytes.toString(StandardCharsets.UTF_8);
            bytes.reset();

            exporter.print(session, captured, new ConsoleExportOptions(SIMPLE, true));
            String simpleWithTimestamp = bytes.toString(StandardCharsets.UTF_8);
            bytes.reset();

            System.setOut(captured);
            exporter.print(session);
            String treeToSystem = bytes.toString(StandardCharsets.UTF_8);
            bytes.reset();

            exporter.printSimple(session);
            String simpleToSystem = bytes.toString(StandardCharsets.UTF_8);

            assertThat(treeToStream)
                    .contains("\uD83D\uDCCB ", "[业务流程] print-matrix-message")
                    .doesNotContain(", self ", "[业务流程 @");
            assertThat(treeToSystem)
                    .contains("\uD83D\uDCCB ", "[业务流程] print-matrix-message")
                    .doesNotContain(", self ", "[业务流程 @");
            assertThat(simpleWithTimestamp)
                    .contains(", self ", "[业务流程 @")
                    .doesNotContain("\uD83D\uDCCB ");
            assertThat(simpleToSystem)
                    .contains(", self ", "[业务流程] print-matrix-message")
                    .doesNotContain("\uD83D\uDCCB ", "[业务流程 @");
        } finally {
            System.setOut(original);
        }
    }

    @Test
    void nullStyleIsRejectedByTheValueType() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ConsoleExportOptions(null, false))
                .withMessage("style");
    }

    @Test
    void optionsValidationPrecedesNullSessionAndCapture() {
        AtomicInteger captures = new AtomicInteger();
        ConsoleExporter exporter = new ConsoleExporter();

        assertThatNullPointerException()
                .isThrownBy(() -> exporter.export(null, null, ignored -> {
                    captures.incrementAndGet();
                    return null;
                }))
                .withMessage("options");
        assertThatNullPointerException()
                .isThrownBy(() -> exporter.export(null, (ConsoleExportOptions) null))
                .withMessage("options");
        assertThat(captures).hasValue(0);
    }

    @Test
    void validOptionsAndNullSessionSkipCapture() {
        AtomicInteger captures = new AtomicInteger();
        String output = new ConsoleExporter().export(
                null, new ConsoleExportOptions(TREE, false), ignored -> {
                    captures.incrementAndGet();
                    return null;
                });

        assertThat(output).isEmpty();
        assertThat(captures).hasValue(0);
    }

    @Test
    void nonNullSessionIsCapturedOnceAndLaterMutationDoesNotLeak() {
        Session session = Session.create("frozen-root");
        session.getRootTask().addInfo("before-capture");
        SessionExportSnapshot snapshot = SessionExportSnapshot.capture(session);
        session.getRootTask().addInfo("after-capture");
        AtomicInteger captures = new AtomicInteger();

        String output = new ConsoleExporter().export(
                session, new ConsoleExportOptions(TREE, false), ignored -> {
                    captures.incrementAndGet();
                    return snapshot;
                });

        assertThat(captures).hasValue(1);
        assertThat(output).contains("before-capture").doesNotContain("after-capture");
    }

    @Test
    void nonNullSessionRejectsInvalidCapturerResults() {
        Session session = Session.create("capturer-root");
        ConsoleExporter exporter = new ConsoleExporter();
        AtomicInteger captures = new AtomicInteger();

        assertThatNullPointerException()
                .isThrownBy(() -> exporter.export(
                        session, new ConsoleExportOptions(TREE, false), null))
                .withMessage("capturer");
        assertThatNullPointerException()
                .isThrownBy(() -> exporter.export(
                        session, new ConsoleExportOptions(TREE, false), ignored -> {
                            captures.incrementAndGet();
                            return null;
                        }))
                .withMessage("capturer returned null snapshot");
        assertThat(captures).hasValue(1);
    }

    @Test
    void nullSinkStillExecutesTheRealCaptureBoundary() {
        Session session = Session.create("oversized-root");
        int limit = Math.toIntExact(FlowConfigDefaults.MAX_EXPORT_TEXT_CHARS);
        session.getRootTask().addInfo("x".repeat(limit + 1));

        assertThatThrownBy(() -> new ConsoleExporter().print(
                session, null, new ConsoleExportOptions(TREE, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Export text character limit exceeded: " + limit);
    }

    @Test
    void optionsPrintWritesNoBytesWhenCaptureFails() {
        Session session = Session.create("oversized-root");
        int limit = Math.toIntExact(FlowConfigDefaults.MAX_EXPORT_TEXT_CHARS);
        session.getRootTask().addInfo("x".repeat(limit + 1));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (PrintStream captured = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            assertThatThrownBy(() -> new ConsoleExporter().print(
                    session, captured, new ConsoleExportOptions(TREE, false)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Export text character limit exceeded: " + limit);
        }

        assertThat(bytes.toByteArray()).isEmpty();
    }

    private Session sessionWithMessage(String content) {
        Session session = Session.create("matrix-root");
        session.getRootTask().addInfo(content);
        return session;
    }

    private org.assertj.core.api.AbstractStringAssert<?> assertMessagesWithoutTimestamp(
            String output) {
        return assertThat(output)
                .contains(
                        "[业务流程] matrix-process",
                        "[⚠️异常提示] matrix-warn",
                        "[⚠️异常提示] matrix-error")
                .doesNotContain("[业务流程 @", "[⚠️异常提示 @");
    }

    private org.assertj.core.api.AbstractStringAssert<?> assertMessagesWithTimestamp(
            String output) {
        return assertThat(output)
                .containsPattern("\\[业务流程 @[^\\]\\r\\n]+\\] matrix-process")
                .containsPattern("\\[⚠️异常提示 @[^\\]\\r\\n]+\\] matrix-warn")
                .containsPattern("\\[⚠️异常提示 @[^\\]\\r\\n]+\\] matrix-error")
                .doesNotContain(
                        "[业务流程] matrix-process",
                        "[⚠️异常提示] matrix-warn",
                        "[⚠️异常提示] matrix-error");
    }
}
