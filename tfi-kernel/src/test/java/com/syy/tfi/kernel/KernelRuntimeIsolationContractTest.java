package com.syy.tfi.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.spi.KernelClock;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 验证同线程多个 Runtime 的上下文、配置、开关、诊断和生命周期互不串线。 */
class KernelRuntimeIsolationContractTest {

    @Test
    void sameThreadRuntimesKeepConfigContextEnableClearAndCloseIndependent() {
        List<FlowSession> firstSessions = new ArrayList<>();
        List<FlowSession> secondSessions = new ArrayList<>();
        KernelRuntime first = KernelRuntime.create(config("FIRST", 100L, firstSessions));
        KernelRuntime second = KernelRuntime.create(config("SECOND", 200L, secondSessions));

        Stage abandonedFirst = first.begin("first-active");
        Stage secondRoot = second.begin("second-active");
        assertThat(first.currentToJson()).contains("\"sessionId\":\"FIRST-1\"");
        assertThat(second.currentToJson()).contains("\"sessionId\":\"SECOND-1\"");

        first.setEnabled(false);
        assertThat(first.isEnabled()).isFalse();
        assertThat(second.isEnabled()).isTrue();
        first.clear();
        assertThat(first.currentToJson()).isEmpty();
        assertThat(second.currentToJson()).contains("\"name\":\"second-active\"");

        second.message("second-prefix");
        secondRoot.close();
        first.setEnabled(true);
        try (Stage ignored = first.begin("first-recovered")) {
            first.message("first-prefix");
        }
        first.close();
        try (Stage ignored = second.begin("second-after-first-close")) {
            second.message("still-open");
        }
        second.close();
        abandonedFirst.close();

        assertThat(firstSessions).singleElement().satisfies(session -> {
            assertThat(session.sessionId()).isEqualTo("FIRST-2");
            assertThat(session.startMs()).isEqualTo(100L);
            assertThat(session.root().records()).singleElement().satisfies(record ->
                    assertThat(record.text()).isEqualTo("first-prefix"));
        });
        assertThat(secondSessions).hasSize(2).allSatisfy(session -> {
            assertThat(session.sessionId()).startsWith("SECOND-");
            assertThat(session.startMs()).isEqualTo(200L);
        });
    }

    @Test
    void capturedContextReturnsToItsRuntimeAndRestoresBothParentContexts() {
        List<FlowSession> firstSessions = new ArrayList<>();
        List<FlowSession> secondSessions = new ArrayList<>();
        KernelRuntime first = KernelRuntime.create(config("FIRST", 100L, firstSessions));
        KernelRuntime second = KernelRuntime.create(config("SECOND", 200L, secondSessions));
        Stage firstRoot = first.begin("first-parent");
        Stage secondRoot = second.begin("second-parent");

        first.capture().wrap((Runnable) () -> first.message("first-child")).run();
        first.message("first-parent-restored");
        second.message("second-parent-untouched");
        firstRoot.close();
        secondRoot.close();
        first.close();
        second.close();

        assertThat(firstSessions).hasSize(2);
        assertThat(firstSessions.getFirst().parentSessionId()).isEqualTo("FIRST-1");
        assertThat(firstSessions.getFirst().root().records()).singleElement().satisfies(record ->
                assertThat(record.text()).isEqualTo("first-child"));
        assertThat(firstSessions.getLast().root().records()).singleElement().satisfies(record ->
                assertThat(record.text()).isEqualTo("first-parent-restored"));
        assertThat(secondSessions).singleElement().satisfies(session ->
                assertThat(session.root().records()).singleElement().satisfies(record ->
                        assertThat(record.text()).isEqualTo("second-parent-untouched")));
    }

    @Test
    void diagnosticsWindowsAreOwnedByEachRuntime() throws Exception {
        KernelRuntime first = KernelRuntime.create(config("FIRST", 100L, new ArrayList<>()));
        KernelRuntime second = KernelRuntime.create(config("SECOND", 200L, new ArrayList<>()));

        String diagnostics = captureStandardError(() -> {
            for (int index = 0; index < 4; index++) {
                first.begin(" ");
            }
            second.begin(" ");
            first.begin(" ");
        });
        first.close();
        second.close();

        assertThat(countOccurrences(diagnostics, "code=INVALID_INPUT")).isEqualTo(4);
    }

    private static KernelConfig config(String idPrefix, long wallTime, List<FlowSession> sessions) {
        AtomicInteger ids = new AtomicInteger();
        KernelConfig defaults = KernelConfig.defaults();
        return new KernelConfig(
                true,
                List.of(sessions::add),
                defaults.sampler(),
                () -> idPrefix + "-" + ids.incrementAndGet(),
                new FixedClock(wallTime),
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs());
    }

    private static String captureStandardError(ThrowingAction action) throws Exception {
        synchronized (KernelRuntimeIsolationContractTest.class) {
            PrintStream original = System.err;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
                System.setErr(capture);
                action.run();
            } finally {
                System.setErr(original);
            }
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }

    private static int countOccurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    /** 为每个 Runtime 提供独立且确定的时间域。 */
    private static final class FixedClock implements KernelClock {
        /** 当前 Runtime 所有 Session 的固定墙钟时间，单位为毫秒。 */
        private final long wallTime;

        private FixedClock(long wallTime) {
            this.wallTime = wallTime;
        }

        @Override
        public long wallTimeMillis() {
            return wallTime;
        }

        @Override
        public long monotonicNanos() {
            return 0L;
        }
    }
}
