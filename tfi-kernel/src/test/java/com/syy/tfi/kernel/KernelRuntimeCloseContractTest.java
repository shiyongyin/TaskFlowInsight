package com.syy.tfi.kernel;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.syy.tfi.kernel.spi.FlowSink;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** 用确定性竞争顺序验证 Runtime close 是同步发布的 terminal barrier。 */
class KernelRuntimeCloseContractTest {
    private static final String REENTRANT_CLOSE_MESSAGE = "sink close";

    @Test
    void allCloseCallersWaitForAdmittedPublishAndRejectLaterPublish() throws Exception {
        CountDownLatch sinkEntered = new CountDownLatch(1);
        CountDownLatch releaseSink = new CountDownLatch(1);
        AtomicInteger sinkCalls = new AtomicInteger();
        AtomicInteger closeReturns = new AtomicInteger();
        AtomicBoolean sinkExited = new AtomicBoolean();
        KernelRuntime runtime = runtime(List.of(session -> {
            sinkCalls.incrementAndGet();
            sinkEntered.countDown();
            await(releaseSink);
            sinkExited.set(true);
        }));
        Thread publisher = Thread.ofVirtual().start(() -> runFlow(runtime, "admitted"));
        assertThat(sinkEntered.await(5, SECONDS)).isTrue();

        CountDownLatch closeStarted = new CountDownLatch(2);
        Thread firstClose = closeThread(runtime, closeStarted, closeReturns);
        Thread secondClose = closeThread(runtime, closeStarted, closeReturns);
        assertThat(closeStarted.await(5, SECONDS)).isTrue();
        awaitClosed(runtime);
        awaitWaiting(firstClose, secondClose);

        Thread latePublisher = Thread.ofVirtual().start(() -> runFlow(runtime, "late"));
        latePublisher.join(SECONDS.toMillis(5));
        assertThat(latePublisher.isAlive()).isFalse();
        assertThat(closeReturns).hasValue(0);

        releaseSink.countDown();
        join(publisher, firstClose, secondClose);
        runFlow(runtime, "after-close");
        runtime.close();

        assertThat(sinkExited).isTrue();
        assertThat(closeReturns).hasValue(2);
        assertThat(sinkCalls).hasValue(1);
    }

    @Test
    void interruptedCloseCompletesBarrierAndRestoresInterruptFlag() throws Exception {
        CountDownLatch sinkEntered = new CountDownLatch(1);
        CountDownLatch releaseSink = new CountDownLatch(1);
        KernelRuntime runtime = runtime(List.of(session -> {
            sinkEntered.countDown();
            await(releaseSink);
        }));
        Thread publisher = Thread.ofVirtual().start(() -> runFlow(runtime, "admitted"));
        assertThat(sinkEntered.await(5, SECONDS)).isTrue();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        Thread closer = Thread.ofVirtual().start(() -> {
            runtime.close();
            interruptRestored.set(Thread.currentThread().isInterrupted());
        });

        try {
            awaitClosed(runtime);
            closer.interrupt();
            Thread.yield();
            assertThat(closer.isAlive()).isTrue();
        } finally {
            releaseSink.countDown();
        }
        join(publisher, closer);

        assertThat(interruptRestored).isTrue();
    }

    @Test
    void lateStageAndCapturedWrapperFinishLocallyWithoutPublishing() {
        AtomicInteger sinkCalls = new AtomicInteger();
        AtomicInteger actions = new AtomicInteger();
        KernelRuntime runtime = runtime(List.of(session -> sinkCalls.incrementAndGet()));
        Stage root = runtime.begin("retiring");
        var captured = runtime.capture();

        runtime.close();
        assertThat(runtime.currentToJson()).contains("\"name\":\"retiring\"");
        runtime.message("rejected");
        root.close();
        captured.wrap((Runnable) actions::incrementAndGet).run();
        runtime.stage("late-action", actions::incrementAndGet);

        assertThat(runtime.currentToJson()).isEmpty();
        assertThat(runtime.isEnabled()).isFalse();
        assertThat(actions).hasValue(2);
        assertThat(sinkCalls).hasValue(0);
    }

    @Test
    void sinkFailureAndReentrantCloseAlwaysReleasePublicationRegistration() throws Exception {
        AtomicReference<KernelRuntime> runtimeRef = new AtomicReference<>();
        AtomicReference<Throwable> reentrantFailure = new AtomicReference<>();
        KernelRuntime reentrant = runtime(List.of(session ->
                reentrantFailure.set(catchThrowable(runtimeRef.get()::close))));
        runtimeRef.set(reentrant);

        runFlow(reentrant, "reentrant-close");
        assertThat(reentrantFailure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(REENTRANT_CLOSE_MESSAGE);
        assertThat(reentrant.isEnabled()).isTrue();
        assertCloseReturns(reentrant);

        KernelRuntime ordinary = runtime(List.of(session -> {
            throw new IllegalStateException("ordinary sink failure");
        }));
        runFlow(ordinary, "ordinary-failure");
        assertCloseReturns(ordinary);

        TestFatalError fatalFailure = new TestFatalError();
        KernelRuntime fatal = runtime(List.of(session -> {
            throw fatalFailure;
        }));
        assertThat(catchThrowable(() -> runFlow(fatal, "fatal-failure"))).isSameAs(fatalFailure);
        assertCloseReturns(fatal);
    }

    private static KernelRuntime runtime(List<FlowSink> sinks) {
        KernelConfig defaults = KernelConfig.defaults();
        return KernelRuntime.create(new KernelConfig(
                true, sinks, defaults.sampler(), defaults.idGenerator(), defaults.clock(),
                defaults.maxStages(), defaults.maxSessionEncodedBytes(),
                defaults.maxRecordEncodedBytes(), defaults.maxAttrs()));
    }

    private static Thread closeThread(
            KernelRuntime runtime, CountDownLatch started, AtomicInteger returns) {
        return Thread.ofVirtual().start(() -> {
            started.countDown();
            runtime.close();
            returns.incrementAndGet();
        });
    }

    private static void runFlow(KernelRuntime runtime, String name) {
        try (Stage ignored = runtime.begin(name)) {
            runtime.message("accepted");
        }
    }

    private static void assertCloseReturns(KernelRuntime runtime) throws InterruptedException {
        Thread closer = Thread.ofVirtual().start(runtime::close);
        closer.join(SECONDS.toMillis(5));
        assertThat(closer.isAlive()).isFalse();
    }

    private static void awaitClosed(KernelRuntime runtime) {
        long deadline = System.nanoTime() + SECONDS.toNanos(5);
        while (runtime.isEnabled() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(runtime.isEnabled()).isFalse();
    }

    private static void awaitWaiting(Thread... threads) {
        long deadline = System.nanoTime() + SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (allWaiting(threads)) {
                return;
            }
            Thread.onSpinWait();
        }
        assertThat(allWaiting(threads)).isTrue();
    }

    private static boolean allWaiting(Thread[] threads) {
        for (Thread thread : threads) {
            if (thread.getState() != Thread.State.WAITING) {
                return false;
            }
        }
        return true;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, SECONDS)) {
                throw new IllegalStateException("timed out waiting for test latch");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for test latch", failure);
        }
    }

    private static void join(Thread... threads) throws InterruptedException {
        for (Thread thread : threads) {
            thread.join(SECONDS.toMillis(5));
            assertThat(thread.isAlive()).isFalse();
        }
    }

    private static final class TestFatalError extends VirtualMachineError {
        /** Java 序列化兼容标识；测试异常不跨进程传输。 */
        private static final long serialVersionUID = 1L;
    }
}
