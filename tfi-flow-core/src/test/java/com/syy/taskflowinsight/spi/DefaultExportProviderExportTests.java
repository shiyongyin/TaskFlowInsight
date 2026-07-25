package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultExportProviderExportTests {

    @BeforeEach
    void setUp() {
        clearGlobalState();
    }

    @AfterEach
    void tearDown() {
        clearGlobalState();
    }

    @Test
    void showTimestampFlagKeepsTreeStyleAndOnlyChangesTimestamp() {
        Session session = Session.create("provider-root");
        session.getRootTask().addInfo("provider-message");
        ProviderRegistry.register(FlowProvider.class, new FixedSessionFlowProvider(session));
        DefaultExportProvider provider = new DefaultExportProvider();

        CapturedConsole withoutTimestamp = capture(provider, false);
        CapturedConsole withTimestamp = capture(provider, true);

        assertThat(withoutTimestamp.exported()).isTrue();
        assertThat(withTimestamp.exported()).isTrue();
        assertThat(withoutTimestamp.text())
                .contains("\uD83D\uDCCB provider-root", "[业务流程] provider-message")
                .doesNotContain("[业务流程 @", ", self ");
        assertThat(withTimestamp.text())
                .contains("\uD83D\uDCCB provider-root", "[业务流程 @")
                .doesNotContain(", self ");
    }

    @Test
    void noArgExportUsesTreeStyleWithoutTimestamp() {
        Session session = Session.create("provider-default-root");
        session.getRootTask().addInfo("provider-default-message");
        ProviderRegistry.register(FlowProvider.class, new FixedSessionFlowProvider(session));
        DefaultExportProvider provider = new DefaultExportProvider();
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (PrintStream captured = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            provider.exportToConsole();
        } finally {
            System.setOut(original);
        }

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("\uD83D\uDCCB provider-default-root",
                        "[业务流程] provider-default-message")
                .doesNotContain("[业务流程 @", ", self ");
    }

    @Test
    void noSessionReturnsFalseWithoutOutput() {
        CapturedConsole captured = capture(new DefaultExportProvider(), false);

        assertThat(captured.exported()).isFalse();
        assertThat(captured.text()).isEmpty();
    }

    private CapturedConsole capture(DefaultExportProvider provider, boolean showTimestamp) {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            return new CapturedConsole(
                    provider.exportToConsole(showTimestamp),
                    bytes.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(original);
        }
    }

    private void clearGlobalState() {
        ProviderRegistry.clearAll();
        ProviderRegistry.setAllowedProviders(null);
        ManagedThreadContext context = ManagedThreadContext.current();
        if (context != null) {
            context.close();
        }
    }

    private record CapturedConsole(boolean exported, String text) {
    }

    /**
     * 固定 currentSession 的测试替身，让断言经过真实 ProviderRegistry 与 DefaultExportProvider。
     */
    private static final class FixedSessionFlowProvider implements FlowProvider {

        private final Session session;

        private FixedSessionFlowProvider(Session session) {
            this.session = session;
        }

        @Override
        public String startSession(String name) {
            return session.getSessionId();
        }

        @Override
        public void endSession() {
        }

        @Override
        public TaskNode startTask(String name) {
            return null;
        }

        @Override
        public void endTask() {
        }

        @Override
        public Session currentSession() {
            return session;
        }

        @Override
        public TaskNode currentTask() {
            return null;
        }

        @Override
        public void message(String content, String label) {
        }

        @Override
        public int priority() {
            return Integer.MAX_VALUE;
        }
    }
}
