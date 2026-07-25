package com.syy.tfi.kernel;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.syy.tfi.kernel.context.ContextHandle;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.FlowStatus;
import com.syy.tfi.kernel.spi.FlowSink;
import com.syy.tfi.kernel.spi.KernelClock;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 发布前的有界压力合同，验证长期复用和虚拟线程 fan-out 不串 Session。 */
class KernelStabilityContractTest {
    private static final int SEQUENTIAL_SESSIONS = 10_000;
    private static final int VIRTUAL_CHILDREN = 1_000;

    @AfterEach
    void restoreDefaults() {
        Tfi.clear();
        Tfi.configure(KernelConfig.defaults());
        Tfi.setEnabled(true);
    }

    @Test
    void reusedWorkerCompletesTenThousandBoundedSessionsDespiteSinkFailures() throws Exception {
        AtomicInteger ids = new AtomicInteger();
        AtomicInteger firstSinkCalls = new AtomicInteger();
        AtomicInteger injectedSinkFailures = new AtomicInteger();
        AtomicInteger delivered = new AtomicInteger();
        AtomicInteger errorSessions = new AtomicInteger();
        AtomicInteger invalidSessions = new AtomicInteger();
        FlowSink occasionallyFailing = session -> {
            int call = firstSinkCalls.incrementAndGet();
            if (call % 1_000 == 0) {
                injectedSinkFailures.incrementAndGet();
                throw new IllegalStateException("injected sink failure");
            }
        };
        FlowSink validator = session -> {
            delivered.incrementAndGet();
            if (session.status() == FlowStatus.ERROR) {
                errorSessions.incrementAndGet();
            }
            if (session.root().children().size() != 1 || session.truncated()) {
                invalidSessions.incrementAndGet();
            }
        };
        configure(List.of(occasionallyFailing, validator), ids);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                for (int index = 0; index < SEQUENTIAL_SESSIONS; index++) {
                    try (Stage ignored = Tfi.begin("reused-worker")) {
                        Tfi.stage("nested", () -> Tfi.message("accepted"));
                        if (index % 100 == 0) {
                            Tfi.error("expected business error");
                        }
                    }
                    if (!Tfi.currentToJson().isEmpty()) {
                        invalidSessions.incrementAndGet();
                    }
                }
            }).get(30, SECONDS);
            assertThat(executor.submit(Tfi::currentToJson).get(5, SECONDS)).isEmpty();
        }

        assertThat(firstSinkCalls).hasValue(SEQUENTIAL_SESSIONS);
        assertThat(injectedSinkFailures).hasValue(10);
        assertThat(delivered).hasValue(SEQUENTIAL_SESSIONS);
        assertThat(errorSessions).hasValue(100);
        assertThat(invalidSessions).hasValue(0);
    }

    @Test
    void virtualThreadFanOutCreatesIndependentLinkedSessionsAndCleansEveryContext() throws Exception {
        Queue<FlowSession> sessions = new ConcurrentLinkedQueue<>();
        AtomicInteger ids = new AtomicInteger();
        AtomicInteger cleanContextProbes = new AtomicInteger();
        configure(List.of(sessions::add), ids);
        Stage parentScope = Tfi.begin("virtual-fan-out");
        ContextHandle handle = Tfi.capture();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(VIRTUAL_CHILDREN);
            for (int index = 0; index < VIRTUAL_CHILDREN; index++) {
                int child = index;
                Runnable linked = handle.wrap((Runnable) () -> Tfi.message("child-" + child));
                futures.add(executor.submit(() -> {
                    linked.run();
                    if (Tfi.currentToJson().isEmpty()) {
                        cleanContextProbes.incrementAndGet();
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, SECONDS);
            }
        }
        parentScope.close();

        FlowSession parent = sessions.stream()
                .filter(session -> session.parentSessionId() == null)
                .findFirst()
                .orElseThrow();
        List<FlowSession> children = sessions.stream()
                .filter(session -> session.parentSessionId() != null)
                .toList();
        assertThat(sessions).hasSize(VIRTUAL_CHILDREN + 1);
        assertThat(children).hasSize(VIRTUAL_CHILDREN);
        assertThat(children).extracting(FlowSession::sessionId).doesNotHaveDuplicates();
        assertThat(children).allSatisfy(session -> {
            assertThat(session.parentSessionId()).isEqualTo(parent.sessionId());
            assertThat(session.status()).isEqualTo(FlowStatus.OK);
            assertThat(session.root().records()).hasSize(1);
        });
        assertThat(cleanContextProbes).hasValue(VIRTUAL_CHILDREN);
        assertThat(Tfi.currentToJson()).isEmpty();
    }

    @Test
    void virtualThreadFailureAndInterruptionRestoreContextWithoutReplacingFailures() throws Exception {
        Queue<FlowSession> sessions = new ConcurrentLinkedQueue<>();
        AtomicInteger ids = new AtomicInteger();
        configure(List.of(sessions::add), ids);
        RuntimeException expectedFailure = new RuntimeException("expected virtual-thread failure");
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        AtomicBoolean failureContextClean = new AtomicBoolean();
        AtomicReference<Throwable> observedInterruption = new AtomicReference<>();
        AtomicBoolean interruptionContextClean = new AtomicBoolean();
        AtomicBoolean interruptFlagRestored = new AtomicBoolean();
        CountDownLatch interruptionStarted = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);

        try (Stage ignored = Tfi.begin("virtual-failure-parent")) {
            ContextHandle handle = Tfi.capture();
            Thread failing = Thread.ofVirtual().start(() -> {
                try {
                    handle.wrap((Runnable) () -> {
                        throw expectedFailure;
                    }).run();
                } catch (Throwable failure) {
                    observedFailure.set(failure);
                } finally {
                    failureContextClean.set(Tfi.currentToJson().isEmpty());
                }
            });
            failing.join(5_000L);
            assertThat(failing.isAlive()).isFalse();

            Thread interrupted = Thread.ofVirtual().start(() -> {
                try {
                    handle.wrap(() -> {
                        interruptionStarted.countDown();
                        blocked.await();
                        return null;
                    }).call();
                } catch (Throwable failure) {
                    observedInterruption.set(failure);
                    interruptFlagRestored.set(Thread.currentThread().isInterrupted());
                } finally {
                    interruptionContextClean.set(Tfi.currentToJson().isEmpty());
                }
            });
            assertThat(interruptionStarted.await(5, SECONDS)).isTrue();
            interrupted.interrupt();
            interrupted.join(5_000L);
            assertThat(interrupted.isAlive()).isFalse();
        }

        assertThat(observedFailure.get()).isSameAs(expectedFailure);
        assertThat(observedInterruption.get()).isInstanceOf(InterruptedException.class);
        assertThat(failureContextClean).isTrue();
        assertThat(interruptionContextClean).isTrue();
        assertThat(interruptFlagRestored).isTrue();
        assertThat(sessions).hasSize(3);
        assertThat(sessions.stream().filter(session -> session.parentSessionId() != null).toList())
                .hasSize(2)
                .allSatisfy(session -> assertThat(session.status()).isEqualTo(FlowStatus.ERROR));
        assertThat(Tfi.currentToJson()).isEmpty();
    }

    private static void configure(List<FlowSink> sinks, AtomicInteger ids) {
        Tfi.configure(new KernelConfig(
                true, sinks, name -> true, () -> "session-" + ids.incrementAndGet(),
                ConstantClock.INSTANCE, 64, 12_288, 2_048, 32));
    }

    private enum ConstantClock implements KernelClock {
        INSTANCE;

        @Override
        public long wallTimeMillis() {
            return 1_000L;
        }

        @Override
        public long monotonicNanos() {
            return 0L;
        }
    }
}
