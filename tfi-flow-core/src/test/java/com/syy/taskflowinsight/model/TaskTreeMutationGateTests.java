package com.syy.taskflowinsight.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTreeMutationGateTests {

    @Test
    void concurrentMutationsShareTheReadSide() throws Exception {
        TaskTreeMutationGate gate = new TaskTreeMutationGate(Duration.ofSeconds(2));
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> gate.mutate(() -> await(entered, release)));
            Future<?> second = executor.submit(() -> gate.mutate(() -> await(entered, release)));

            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();

            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void inFlightMutationBlocksCaptureAction() throws Exception {
        TaskTreeMutationGate gate = new TaskTreeMutationGate(Duration.ofSeconds(2));
        CountDownLatch mutationEntered = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> mutation = executor.submit(
                    () -> gate.mutate(() -> await(mutationEntered, releaseMutation)));
            assertThat(mutationEntered.await(2, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> capture = executor.submit(() -> gate.capture(() -> true));
            assertPending(capture);
            releaseMutation.countDown();

            assertThat(capture.get(2, TimeUnit.SECONDS)).isTrue();
            mutation.get(2, TimeUnit.SECONDS);
        } finally {
            releaseMutation.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void captureActionBlocksNewMutation() throws Exception {
        TaskTreeMutationGate gate = new TaskTreeMutationGate(Duration.ofSeconds(2));
        CountDownLatch captureEntered = new CountDownLatch(1);
        CountDownLatch releaseCapture = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> capture = executor.submit(() -> gate.capture(() -> {
                await(captureEntered, releaseCapture);
                return null;
            }));
            assertThat(captureEntered.await(2, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> mutation = executor.submit(() -> gate.mutate(() -> true));
            assertPending(mutation);
            releaseCapture.countDown();

            assertThat(mutation.get(2, TimeUnit.SECONDS)).isTrue();
            capture.get(2, TimeUnit.SECONDS);
        } finally {
            releaseCapture.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void throwingCaptureActionAlwaysReleasesWriteLock() {
        TaskTreeMutationGate gate = new TaskTreeMutationGate(Duration.ofSeconds(2));
        IllegalStateException failure = new IllegalStateException("capture failed");

        assertThatThrownBy(() -> gate.capture(() -> {
            throw failure;
        })).isSameAs(failure);

        assertThat(gate.mutate(() -> "released")).isEqualTo("released");
    }

    @Test
    void captureAcquisitionFailsAfterTheConfiguredTimeout() throws Exception {
        TaskTreeMutationGate gate = new TaskTreeMutationGate(Duration.ofMillis(50));
        CountDownLatch mutationEntered = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> mutation = executor.submit(
                    () -> gate.mutate(() -> await(mutationEntered, releaseMutation)));
            assertThat(mutationEntered.await(2, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> gate.capture(() -> null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Timed out waiting for task tree capture lock after 50 ms");

            releaseMutation.countDown();
            mutation.get(2, TimeUnit.SECONDS);
        } finally {
            releaseMutation.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void interruptedCaptureAcquisitionRestoresInterruptStatusAndReleasesNoLock() throws Exception {
        TaskTreeMutationGate gate = new TaskTreeMutationGate(Duration.ofSeconds(2));
        CountDownLatch mutationEntered = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        CountDownLatch captureStarted = new CountDownLatch(1);
        AtomicReference<Thread> captureThread = new AtomicReference<>();
        AtomicReference<Throwable> captureFailure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> mutation = executor.submit(
                    () -> gate.mutate(() -> await(mutationEntered, releaseMutation)));
            assertThat(mutationEntered.await(2, TimeUnit.SECONDS)).isTrue();
            Future<?> capture = executor.submit(() -> {
                captureThread.set(Thread.currentThread());
                captureStarted.countDown();
                try {
                    gate.capture(() -> null);
                } catch (Throwable failure) {
                    captureFailure.set(failure);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });

            assertThat(captureStarted.await(2, TimeUnit.SECONDS)).isTrue();
            awaitBlocked(captureThread.get());
            captureThread.get().interrupt();
            capture.get(2, TimeUnit.SECONDS);

            assertThat(captureFailure.get())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Interrupted while waiting for task tree capture lock")
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(interrupted).isTrue();
            releaseMutation.countDown();
            mutation.get(2, TimeUnit.SECONDS);
            assertThat(gate.mutate(() -> true)).isTrue();
        } finally {
            releaseMutation.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void mutationWaitsPastCaptureTimeoutBudgetAndStillCompletes() throws Exception {
        TaskTreeMutationGate gate = new TaskTreeMutationGate(Duration.ofMillis(50));
        CountDownLatch captureEntered = new CountDownLatch(1);
        CountDownLatch releaseCapture = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> capture = executor.submit(() -> gate.capture(() -> {
                await(captureEntered, releaseCapture);
                return null;
            }));
            assertThat(captureEntered.await(2, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> mutation = executor.submit(() -> gate.mutate(() -> true));

            assertPending(mutation);
            releaseCapture.countDown();

            assertThat(mutation.get(2, TimeUnit.SECONDS)).isTrue();
            capture.get(2, TimeUnit.SECONDS);
        } finally {
            releaseCapture.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void lockTimeoutMustBePositive() {
        assertThatThrownBy(() -> new TaskTreeMutationGate(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Lock timeout must be positive");
        assertThatThrownBy(() -> new TaskTreeMutationGate(Duration.ofNanos(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Lock timeout must be positive");
    }

    private static void assertPending(Future<?> future) {
        assertThatThrownBy(() -> future.get(100, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    private static void await(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try {
            if (!release.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test release");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void awaitBlocked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.TIMED_WAITING || state == Thread.State.WAITING) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("Capture thread did not block on the write lock");
    }
}
