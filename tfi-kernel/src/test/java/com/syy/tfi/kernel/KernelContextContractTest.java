package com.syy.tfi.kernel;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.syy.tfi.kernel.context.ContextHandle;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.FlowStatus;
import com.syy.tfi.kernel.spi.KernelClock;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 从公共门面验证链接子 Session、线程封闭和宿主清理语义。
 */
class KernelContextContractTest {

    @AfterEach
    void restoreDefaults() {
        Thread.interrupted();
        Tfi.clear();
        Tfi.configure(KernelConfig.defaults());
        Tfi.setEnabled(true);
    }

    @Test
    void kvLc06OneHandleConcurrentlyCreatesIndependentLinkedSessions() throws Exception {
        List<FlowSession> sessions = new CopyOnWriteArrayList<>();
        configureSink(sessions);
        Stage parent = Tfi.begin("parent-flow");
        ContextHandle handle = Tfi.capture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(handle.wrap(concurrentAction("first", ready, start)));
            Future<?> second = executor.submit(handle.wrap(concurrentAction("second", ready, start)));
            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();
            first.get(5, SECONDS);
            second.get(5, SECONDS);
        }
        parent.close();

        FlowSession parentSession = sessions.stream()
                .filter(session -> session.parentSessionId() == null)
                .findFirst()
                .orElseThrow();
        List<FlowSession> children = sessions.stream()
                .filter(session -> session.parentSessionId() != null)
                .toList();
        assertThat(children).hasSize(2);
        assertThat(children).extracting(FlowSession::sessionId).doesNotHaveDuplicates();
        assertThat(children).allSatisfy(child -> {
            assertThat(child.parentSessionId()).isEqualTo(parentSession.sessionId());
            assertThat(child.root().children()).singleElement().satisfies(stage ->
                    assertThat(stage.records()).hasSize(1));
        });
        assertThat(children).flatExtracting(child -> child.root().children())
                .extracting(stage -> stage.name())
                .containsExactlyInAnyOrder("first", "second");
    }

    @Test
    void kvLc07AndKvDg01CrossThreadUseIsNoopAndDiagnosticsAreBounded() throws Exception {
        List<FlowSession> sessions = new CopyOnWriteArrayList<>();
        MutableClock clock = new MutableClock();
        KernelConfig defaults = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(
                true,
                List.of(sessions::add),
                defaults.sampler(),
                defaults.idGenerator(),
                clock,
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs()));
        Stage root = Tfi.begin("owner-flow");
        Stage child = Tfi.stage("owner-stage");

        String diagnostics = captureStandardError(() -> {
            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> {
                    for (int index = 0; index < 4; index++) {
                        child.message("cross-thread");
                    }
                    child.close();
                }).get(5, SECONDS);
                clock.nextDiagnosticWindow();
                executor.submit(() -> child.message("next-window")).get(5, SECONDS);
            }
        });
        child.message("owner-write");
        child.close();
        root.close();

        assertThat(countOccurrences(diagnostics, "code=CROSS_THREAD")).isEqualTo(4);
        assertThat(diagnostics).contains("suppressed=2");
        assertThat(sessions).singleElement().satisfies(session ->
                assertThat(session.root().children()).singleElement().satisfies(stage -> {
                    assertThat(stage.records()).singleElement().satisfies(record ->
                            assertThat(record.text()).isEqualTo("owner-write"));
                }));
    }

    @Test
    void invalidOptionalErrorTextIsDiagnosedWithoutLosingTheThrowableFact() throws Exception {
        List<FlowSession> sessions = new CopyOnWriteArrayList<>();
        MutableClock clock = new MutableClock();
        KernelConfig defaults = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(
                true,
                List.of(sessions::add),
                defaults.sampler(),
                defaults.idGenerator(),
                clock,
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs()));
        IllegalStateException failure = new IllegalStateException("boom");

        String diagnostics = captureStandardError(() -> {
            try (Stage ignored = Tfi.begin("invalid-error-text")) {
                Tfi.error("x".repeat(65_537), failure);
            }
        });

        assertThat(countOccurrences(diagnostics, "code=INVALID_INPUT")).isEqualTo(1);
        assertThat(sessions).singleElement().satisfies(session ->
                assertThat(session.root().records()).singleElement().satisfies(record -> {
                    assertThat(record.text()).isNull();
                    assertThat(record.data()).containsExactly(
                            Map.entry("errorType", IllegalStateException.class.getName()),
                            Map.entry("errorMessage", "boom"));
                }));
    }

    @Test
    void diagnosticsTreatDifferentClockInstancesAsDifferentWindowDomains() throws Exception {
        MutableClock firstClock = new MutableClock();
        MutableClock secondClock = new MutableClock();
        Diagnostics ownerDiagnostics = new Diagnostics();

        String diagnostics = captureStandardError(() -> {
            for (int index = 0; index < 4; index++) {
                ownerDiagnostics.warn(DiagnosticCode.SINK_FAILURE, null, null, firstClock);
            }
            ownerDiagnostics.warn(DiagnosticCode.SINK_FAILURE, null, null, secondClock);
        });

        assertThat(countOccurrences(diagnostics, "code=SINK_FAILURE")).isEqualTo(4);
    }

    @Test
    void kvLc08ClearPreventsDataLeakWhenAPoolThreadIsReused() throws Exception {
        List<FlowSession> sessions = new CopyOnWriteArrayList<>();
        configureSink(sessions);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                Tfi.begin("abandoned-request");
                Tfi.message("must-not-be-published");
                Tfi.clear();
            }).get(5, SECONDS);
            executor.submit(() -> {
                try (Stage ignored = Tfi.begin("next-request")) {
                    Tfi.message("new-request-only");
                }
            }).get(5, SECONDS);
        }

        assertThat(sessions).singleElement().satisfies(session -> {
            assertThat(session.name()).isEqualTo("next-request");
            assertThat(session.root().records()).singleElement().satisfies(record ->
                    assertThat(record.text()).isEqualTo("new-request-only"));
        });
    }

    @Test
    void kvLc09VirtualThreadsExitCleanlyAcrossNormalFailureAndInterruptPaths() throws Exception {
        List<FlowSession> sessions = new CopyOnWriteArrayList<>();
        configureSink(sessions);
        AtomicInteger cleanContextProbes = new AtomicInteger();
        RuntimeException expectedRuntime = new IllegalStateException("virtual failure");
        AtomicReference<Throwable> actualRuntime = new AtomicReference<>();
        AtomicReference<InterruptedException> callbackInterrupted = new AtomicReference<>();
        AtomicReference<Throwable> actualInterrupted = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        CountDownLatch interruptReady = new CountDownLatch(1);
        CountDownLatch interruptBlocker = new CountDownLatch(1);

        try (Stage ignored = Tfi.begin("virtual-parent")) {
            ContextHandle handle = Tfi.capture();
            Runnable normal = handle.wrap((Runnable) () -> Tfi.message("normal"));
            Thread normalThread = Thread.ofVirtual().start(() -> {
                normal.run();
                probeForEmptyContext(cleanContextProbes);
            });

            Runnable failing = handle.wrap((Runnable) () -> {
                throw expectedRuntime;
            });
            Thread failingThread = Thread.ofVirtual().start(() -> {
                try {
                    failing.run();
                } catch (Throwable failure) {
                    actualRuntime.set(failure);
                } finally {
                    probeForEmptyContext(cleanContextProbes);
                }
            });

            Callable<Void> interruptAction = () -> {
                interruptReady.countDown();
                try {
                    interruptBlocker.await();
                    return null;
                } catch (InterruptedException failure) {
                    callbackInterrupted.set(failure);
                    throw failure;
                }
            };
            Callable<Void> interrupted = handle.wrap(interruptAction);
            Thread interruptedThread = Thread.ofVirtual().start(() -> {
                try {
                    interrupted.call();
                } catch (Throwable failure) {
                    actualInterrupted.set(failure);
                    interruptRestored.set(Thread.currentThread().isInterrupted());
                } finally {
                    probeForEmptyContext(cleanContextProbes);
                }
            });

            assertThat(interruptReady.await(5, SECONDS)).isTrue();
            interruptedThread.interrupt();
            normalThread.join(SECONDS.toMillis(5));
            failingThread.join(SECONDS.toMillis(5));
            interruptedThread.join(SECONDS.toMillis(5));
            assertThat(normalThread.isAlive()).isFalse();
            assertThat(failingThread.isAlive()).isFalse();
            assertThat(interruptedThread.isAlive()).isFalse();
        }

        assertThat(actualRuntime.get()).isSameAs(expectedRuntime);
        assertThat(actualInterrupted.get()).isSameAs(callbackInterrupted.get());
        assertThat(interruptRestored).isTrue();
        assertThat(cleanContextProbes).hasValue(3);
        List<FlowSession> linked = sessions.stream()
                .filter(session -> session.parentSessionId() != null)
                .toList();
        assertThat(linked).hasSize(3);
        assertThat(linked).filteredOn(session -> session.status() == FlowStatus.OK).hasSize(1);
        assertThat(linked).filteredOn(session -> session.status() == FlowStatus.ERROR).hasSize(2);
        assertThat(sessions).filteredOn(session -> session.parentSessionId() == null).hasSize(1);
    }

    private static Runnable concurrentAction(String name, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            await(start);
            try (Stage ignored = Tfi.stage(name)) {
                Tfi.message("owned-by-" + name);
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, SECONDS)) {
                throw new IllegalStateException("timed out waiting for concurrent start");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while coordinating test", failure);
        }
    }

    private static void probeForEmptyContext(AtomicInteger probes) {
        Runnable probe = probes::incrementAndGet;
        Tfi.capture().wrap(probe).run();
    }

    private static void configureSink(List<FlowSession> sessions) {
        KernelConfig defaults = KernelConfig.defaults();
        Tfi.configure(new KernelConfig(
                true,
                List.of(sessions::add),
                defaults.sampler(),
                defaults.idGenerator(),
                defaults.clock(),
                defaults.maxStages(),
                defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(),
                defaults.maxAttrs()));
    }

    private static String captureStandardError(ThrowingAction action) throws Exception {
        synchronized (KernelContextContractTest.class) {
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

    private static final class MutableClock implements KernelClock {
        private long monotonicNanos;

        void nextDiagnosticWindow() {
            monotonicNanos += SECONDS.toNanos(60L);
        }

        @Override
        public long wallTimeMillis() {
            return 1_000L;
        }

        @Override
        public long monotonicNanos() {
            return monotonicNanos;
        }
    }
}
