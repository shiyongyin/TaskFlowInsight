package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.SessionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTreeCaptureConcurrencyTests {

    @Test
    void captureBlocksEveryTreeMutationButNotAStandaloneTree() throws Exception {
        Session session = Session.create("capture-tree");
        TaskNode root = session.getRootTask();
        TaskNode messageNode = root.createChild("message");
        TaskNode attributeNode = root.createChild("attribute");
        TaskNode tagNode = root.createChild("tag");
        TaskNode completeNode = root.createChild("complete");
        TaskNode failNode = root.createChild("fail");
        TaskNode standalone = new TaskNode("standalone");
        CountDownLatch captureEntered = new CountDownLatch(1);
        CountDownLatch releaseCapture = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(9);
        try {
            Future<?> capture = executor.submit(() -> session.captureExport(() -> {
                captureEntered.countDown();
                await(releaseCapture);
                return null;
            }));
            assertThat(captureEntered.await(2, TimeUnit.SECONDS)).isTrue();

            List<Future<?>> blocked = List.of(
                    executor.submit(() -> {
                        root.createChild("factory-child");
                    }),
                    executor.submit(() -> {
                        new TaskNode(root, "constructor-child");
                    }),
                    executor.submit(() -> {
                        messageNode.addInfo("blocked");
                    }),
                    executor.submit(() -> {
                        attributeNode.addAttribute("key", "value");
                    }),
                    executor.submit(() -> {
                        tagNode.addTag("blocked");
                    }),
                    executor.submit(() -> {
                        completeNode.complete();
                    }),
                    executor.submit(() -> {
                        failNode.fail();
                    }));

            for (Future<?> mutation : blocked) {
                assertPending(mutation);
            }
            assertThat(executor.submit(() -> {
                        standalone.addInfo("independent");
                        return standalone;
                    })
                    .get(2, TimeUnit.SECONDS)).isNotNull();

            releaseCapture.countDown();
            for (Future<?> mutation : blocked) {
                mutation.get(2, TimeUnit.SECONDS);
            }
            capture.get(2, TimeUnit.SECONDS);

            assertThat(root.getChildren()).extracting(TaskNode::getTaskName)
                    .contains("factory-child", "constructor-child");
            assertThat(messageNode.getMessages()).hasSize(1);
            assertThat(attributeNode.getAttributes()).containsEntry("key", "value");
            assertThat(tagNode.getTags()).containsExactly("blocked");
            assertThat(completeNode.getStatus().name()).isEqualTo("COMPLETED");
            assertThat(failNode.getStatus().name()).isEqualTo("FAILED");
        } finally {
            releaseCapture.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void captureBlocksSessionCompleteAndError() throws Exception {
        assertTerminalBlocked(Session::complete, SessionStatus.COMPLETED);
        assertTerminalBlocked(session -> session.error("failure"), SessionStatus.ERROR);
    }

    @Test
    void terminalProbeRunsAfterSessionMonitorAndGateRelease() {
        AtomicBoolean sessionMonitorReleased = new AtomicBoolean();
        AtomicBoolean captureCompleted = new AtomicBoolean();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Session session = new Session("probe-order", transitioned -> {
                sessionMonitorReleased.set(!Thread.holdsLock(transitioned));
                try {
                    captureCompleted.set(executor.submit(
                                    () -> transitioned.captureExport(() -> true))
                            .get(2, TimeUnit.SECONDS));
                } catch (Exception failure) {
                    throw new AssertionError("Probe could not acquire capture after transition", failure);
                }
            });

            session.complete();

            assertThat(sessionMonitorReleased).isTrue();
            assertThat(captureCompleted).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void repeatedCaptureAndTerminalMutationDoNotDeadlock() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < 1_000; attempt++) {
                Session session = Session.create("repeated-race-" + attempt);
                CountDownLatch start = new CountDownLatch(1);
                Future<?> capture = executor.submit(() -> {
                    await(start);
                    return session.captureExport(() -> session.getRootTask().getStatus());
                });
                Future<?> terminal = executor.submit(() -> {
                    await(start);
                    session.tryComplete();
                });

                start.countDown();
                capture.get(10, TimeUnit.SECONDS);
                terminal.get(10, TimeUnit.SECONDS);
                assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertTerminalBlocked(
            Consumer<Session> terminalAction, SessionStatus expectedStatus) throws Exception {
        Session session = Session.create("session-terminal");
        CountDownLatch captureEntered = new CountDownLatch(1);
        CountDownLatch releaseCapture = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> capture = executor.submit(() -> session.captureExport(() -> {
                captureEntered.countDown();
                await(releaseCapture);
                return null;
            }));
            assertThat(captureEntered.await(2, TimeUnit.SECONDS)).isTrue();

            Future<?> terminal = executor.submit(() -> terminalAction.accept(session));
            assertPending(terminal);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.RUNNING);

            releaseCapture.countDown();
            terminal.get(2, TimeUnit.SECONDS);
            capture.get(2, TimeUnit.SECONDS);
            assertThat(session.getStatus()).isEqualTo(expectedStatus);
        } finally {
            releaseCapture.countDown();
            executor.shutdownNow();
        }
    }

    private static void assertPending(Future<?> future) {
        assertThatThrownBy(() -> future.get(100, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    private static void await(CountDownLatch release) {
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for capture release");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
