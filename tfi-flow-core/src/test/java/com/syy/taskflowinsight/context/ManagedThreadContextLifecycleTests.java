package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.enums.SessionStatus;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskTreeCaptureTestAccess;
import com.syy.taskflowinsight.spi.DefaultFlowProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * 验证 Context 的终止意图决定 Session 终态，同时所有路径都释放唯一的线程绑定和注册表条目。
 *
 * <p>测试始终在终止前保存 Session，因为终止完成后 Context 必须主动断开对整棵任务树的引用。
 */
class ManagedThreadContextLifecycleTests {

    private SafeContextManager manager;

    @BeforeEach
    void setUp() {
        manager = SafeContextManager.getInstance();
        manager.clearAllContextsForTesting();
        manager.apply(ContextManagerConfig.defaults());
    }

    @AfterEach
    void tearDown() {
        manager.clearAllContextsForTesting();
        manager.apply(ContextManagerConfig.defaults());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TerminalCase.class)
    void terminalOutcomeControlsSessionAndCleanup(TerminalCase terminalCase) throws Exception {
        int baseline = manager.getActiveContextCount();

        Observation observation = runCase(terminalCase);

        assertThat(observation.session().getStatus()).isEqualTo(terminalCase.expectedStatus);
        if (terminalCase.expectedMessage != null) {
            assertThat(messageContents(observation.session()))
                    .anyMatch(message -> message.contains(terminalCase.expectedMessage));
        }
        assertThat(observation.context().getCurrentSession()).isNull();
        assertThat(ManagedThreadContext.current()).isNull();
        assertThat(manager.getActiveContextCount()).isEqualTo(baseline);
    }

    @Test
    void endSessionCompletesAndUnregistersItsContext() {
        int baseline = manager.getActiveContextCount();
        ManagedThreadContext context = ManagedThreadContext.create("end-session");
        Session session = context.getCurrentSession();

        context.endSession();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(context.isClosed()).isTrue();
        assertThat(context.getCurrentSession()).isNull();
        assertThat(ManagedThreadContext.current()).isNull();
        assertThat(manager.getActiveContextCount()).isEqualTo(baseline);
    }

    @Test
    @SuppressWarnings("deprecation")
    void managedSessionIsVisibleThroughStatelessAdapter() {
        ManagedThreadContext context = ManagedThreadContext.create("managed-owner");
        Session session = context.getCurrentSession();

        assertThat(Session.getCurrent()).isSameAs(session);
        assertThat(Session.getCurrent()).isSameAs(ThreadContext.currentSession());

        context.close();
    }

    @Test
    void directOwningSessionCompleteReleasesContext() {
        int activeBefore = manager.getActiveContextCount();
        long closedBefore = manager.getContextClosedCount();
        ManagedThreadContext context = ManagedThreadContext.create("direct-complete");
        Session session = context.getCurrentSession();

        session.complete();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(context.isClosed()).isTrue();
        assertThat(context.getCurrentSession()).isNull();
        assertThat(ManagedThreadContext.current()).isNull();
        assertThat(manager.getActiveContextCount()).isEqualTo(activeBefore);
        assertThat(manager.getContextClosedCount()).isEqualTo(closedBefore + 1);
    }

    @Test
    void directOwningSessionErrorReleasesContext() {
        int activeBefore = manager.getActiveContextCount();
        long closedBefore = manager.getContextClosedCount();
        ManagedThreadContext context = ManagedThreadContext.create("direct-error");
        Session session = context.getCurrentSession();

        session.error("direct failure");

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ERROR);
        assertThat(messageContents(session)).contains("direct failure");
        assertThat(context.isClosed()).isTrue();
        assertThat(ManagedThreadContext.current()).isNull();
        assertThat(manager.getActiveContextCount()).isEqualTo(activeBefore);
        assertThat(manager.getContextClosedCount()).isEqualTo(closedBefore + 1);
    }

    @Test
    void directOwningTryErrorReleasesContext() {
        long closedBefore = manager.getContextClosedCount();
        ManagedThreadContext context = ManagedThreadContext.create("direct-try-error");
        Session session = context.getCurrentSession();

        assertThat(session.tryError("try failure")).isTrue();

        assertThat(context.isClosed()).isTrue();
        assertThat(ManagedThreadContext.current()).isNull();
        assertThat(manager.getContextClosedCount()).isEqualTo(closedBefore + 1);
    }

    @Test
    void externalReleaseRejectsInvalidSessionWithoutChangingContext() {
        ManagedThreadContext context = ManagedThreadContext.create("running-release");
        Session session = context.getCurrentSession();
        int active = manager.getActiveContextCount();
        long closed = manager.getContextClosedCount();

        assertThatThrownBy(() -> manager.releaseExternallyTerminatedSession(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> manager.releaseExternallyTerminatedSession(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session must be terminal before external release");

        assertThat(session.getStatus()).isEqualTo(SessionStatus.RUNNING);
        assertThat(context.getCurrentSession()).isSameAs(session);
        assertThat(ManagedThreadContext.current()).isSameAs(context);
        assertThat(manager.getActiveContextCount()).isEqualTo(active);
        assertThat(manager.getContextClosedCount()).isEqualTo(closed);
        context.close();
    }

    @Test
    void contextOwnedMarkerPreventsExternalBridgeFromWaitingForContextMonitor() throws Exception {
        CountDownLatch markerEntered = new CountDownLatch(1);
        CountDownLatch allowContextTerminal = new CountDownLatch(1);
        CountDownLatch directFinished = new CountDownLatch(1);
        AtomicReference<Throwable> cleanupFailure = new AtomicReference<>();
        AtomicReference<Throwable> directFailure = new AtomicReference<>();
        ManagedThreadContext context = new ManagedThreadContext((ignored, session) -> {
            markerEntered.countDown();
            await(allowContextTerminal);
        });
        Session session = context.startSession("marker-race");
        manager.bindNewContext(context);

        Thread cleanup = new Thread(() -> {
            try {
                context.forceCleanup("marker cleanup");
            } catch (Throwable failure) {
                cleanupFailure.set(failure);
            }
        }, "context-owned-terminal");
        Thread direct = new Thread(() -> {
            try {
                session.complete();
            } catch (Throwable failure) {
                directFailure.set(failure);
            } finally {
                directFinished.countDown();
            }
        }, "direct-session-terminal");

        cleanup.start();
        assertThat(markerEntered.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            direct.start();
            assertThat(directFinished.await(2, TimeUnit.SECONDS))
                    .as("external bridge must not wait for the held Context monitor")
                    .isTrue();
        } finally {
            allowContextTerminal.countDown();
        }
        cleanup.join(5_000L);
        direct.join(5_000L);

        assertThat(cleanup.isAlive()).isFalse();
        assertThat(direct.isAlive()).isFalse();
        assertThat(cleanupFailure.get()).isNull();
        assertThat(directFailure.get()).isNull();
        assertThat(context.isClosed()).isTrue();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(ManagedThreadContext.current()).isNull();
    }

    @Test
    void terminalProbeRunsOnlyBeforeAnActiveSessionTerminalHelper() {
        AtomicInteger probeCalls = new AtomicInteger();
        ManagedThreadContext context = new ManagedThreadContext(
                (ignoredContext, ignoredSession) -> probeCalls.incrementAndGet());
        Session session = context.startSession("already-terminal");
        session.complete();

        context.close();

        assertThat(probeCalls).hasValue(0);
        assertThat(context.isClosed()).isTrue();
    }

    @Test
    void idempotentDirectTerminalAndContextCloseAccountRegistryIdentityOnce() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            long closedBefore = manager.getContextClosedCount();
            ManagedThreadContext context = ManagedThreadContext.create("terminal-race");
            Session session = context.getCurrentSession();
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread direct = new Thread(() -> runAfter(start, session::tryComplete, failure));
            Thread contextClose = new Thread(() -> runAfter(start, context::close, failure));

            direct.start();
            contextClose.start();
            start.countDown();
            direct.join(5_000L);
            contextClose.join(5_000L);

            assertThat(direct.isAlive()).isFalse();
            assertThat(contextClose.isAlive()).isFalse();
            assertThat(failure.get()).isNull();
            assertThat(context.isClosed()).isTrue();
            assertThat(ManagedThreadContext.current()).isNull();
            assertThat(manager.getActiveContextCount()).isZero();
            assertThat(manager.getContextClosedCount()).isEqualTo(closedBefore + 1);
        });
    }

    @Test
    void contextTerminalMutationWaitsForLongCaptureAndClosesExactlyOnce() throws Exception {
        long closedBefore = manager.getContextClosedCount();
        ManagedThreadContext context = ManagedThreadContext.create("capture-blocked-context");
        Session session = context.getCurrentSession();
        CountDownLatch captureEntered = new CountDownLatch(1);
        CountDownLatch releaseCapture = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> capture = executor.submit(() -> TaskTreeCaptureTestAccess.capture(session, () -> {
                captureEntered.countDown();
                await(releaseCapture);
                return null;
            }));
            assertThat(captureEntered.await(2, TimeUnit.SECONDS)).isTrue();

            Future<?> terminal = executor.submit(context::close);
            assertThatThrownBy(() -> terminal.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.RUNNING);
            assertThat(context.isClosed()).isFalse();

            releaseCapture.countDown();
            terminal.get(2, TimeUnit.SECONDS);
            capture.get(2, TimeUnit.SECONDS);

            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(context.isClosed()).isTrue();
            assertThat(context.getCurrentSession()).isNull();
            assertThat(ManagedThreadContext.current()).isNull();
            assertThat(manager.getActiveContextCount()).isZero();
            assertThat(manager.getContextClosedCount()).isEqualTo(closedBefore + 1);
        } finally {
            releaseCapture.countDown();
            executor.shutdownNow();
            if (!context.isClosed()) {
                context.close();
            }
        }
    }

    @Test
    void queuedFairCaptureCannotDeadlockDirectAndContextOwnedTermination() throws Exception {
        long closedBefore = manager.getContextClosedCount();
        CountDownLatch contextProbeEntered = new CountDownLatch(1);
        CountDownLatch allowContextTerminal = new CountDownLatch(1);
        CountDownLatch captureEntered = new CountDownLatch(1);
        CountDownLatch releaseCapture = new CountDownLatch(1);
        ManagedThreadContext context = new ManagedThreadContext((ignored, session) -> {
            contextProbeEntered.countDown();
            await(allowContextTerminal);
        });
        Session session = context.startSession("fair-terminal-race");
        manager.bindNewContext(context);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> contextTerminal = executor.submit(context::close);
            assertThat(contextProbeEntered.await(2, TimeUnit.SECONDS)).isTrue();
            Future<?> capture = executor.submit(() -> TaskTreeCaptureTestAccess.capture(session, () -> {
                captureEntered.countDown();
                await(releaseCapture);
                return null;
            }));
            assertThat(captureEntered.await(2, TimeUnit.SECONDS)).isTrue();

            allowContextTerminal.countDown();
            Future<Boolean> directTerminal = executor.submit(() -> session.tryComplete());
            assertThatThrownBy(() -> contextTerminal.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> directTerminal.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseCapture.countDown();
            capture.get(5, TimeUnit.SECONDS);
            contextTerminal.get(5, TimeUnit.SECONDS);
            directTerminal.get(5, TimeUnit.SECONDS);

            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(context.isClosed()).isTrue();
            assertThat(ManagedThreadContext.current()).isNull();
            assertThat(manager.getActiveContextCount()).isZero();
            assertThat(manager.getContextClosedCount()).isEqualTo(closedBefore + 1);
        } finally {
            allowContextTerminal.countDown();
            releaseCapture.countDown();
            executor.shutdownNow();
            if (!context.isClosed()) {
                context.close();
            }
        }
    }

    private static void runAfter(
            CountDownLatch start, Runnable action, AtomicReference<Throwable> failure) {
        try {
            start.await();
            action.run();
        } catch (Throwable thrown) {
            failure.compareAndSet(null, thrown);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test latch");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    @Test
    void nextSessionUsesANewContext() {
        ManagedThreadContext first = ManagedThreadContext.create("first");
        String firstContextId = first.getContextId();
        first.endSession();

        ManagedThreadContext second = ManagedThreadContext.create("second");

        assertThat(second.getContextId()).isNotEqualTo(firstContextId);
        second.close();
    }

    @Test
    void activeSessionRejectsDuplicateStartWithoutChangingItsOwner() {
        ManagedThreadContext context = ManagedThreadContext.create("active-owner");
        Session session = context.getCurrentSession();

        assertThatThrownBy(() -> context.startSession("duplicate"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session already active: " + session.getSessionId());

        assertThat(context.getCurrentSession()).isSameAs(session);
        assertThat(context.getTaskDepth()).isOne();
        assertThat(ManagedThreadContext.current()).isSameAs(context);
        context.close();
    }

    @Test
    void terminalContextIsIdempotentButRejectsEveryReuseOperation() {
        ManagedThreadContext context = ManagedThreadContext.create("terminal-owner");
        String terminalMessage = "Context already closed: " + context.getContextId();
        context.endSession();

        assertThatNoException().isThrownBy(context::endSession);
        assertThatNoException().isThrownBy(context::close);

        List<Runnable> reuseOperations = List.of(
                () -> context.startSession("reused-session"),
                () -> context.startTask("reused-task"),
                context::endTask,
                context::createSnapshot,
                () -> context.setAttribute("reused-key", "reused-value"));
        for (Runnable operation : reuseOperations) {
            assertThatThrownBy(operation::run)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(terminalMessage);
        }
    }

    private Observation runCase(TerminalCase terminalCase) throws Exception {
        return switch (terminalCase) {
            case NORMAL_CLOSE -> closeNormally();
            case WRAPPER_SUCCESS -> executeSuccessfully();
            case WRAPPER_FAILURE -> executeWithFailure();
            case PROPAGATED_WRAPPER_FAILURE -> executePropagatedFailure();
            case FORCED_CLEAR -> clearExplicitly();
            case PROVIDER_CLEAR -> clearThroughProvider();
            case REPLACEMENT -> replaceActiveContext();
            case LEAK_CLEANUP -> cleanDeadThreadContext();
            case SHUTDOWN -> cleanForShutdown();
        };
    }

    private static Observation closeNormally() {
        ManagedThreadContext context = ManagedThreadContext.create("normal-close");
        Session session = context.getCurrentSession();
        context.close();
        return new Observation(context, session);
    }

    private static Observation executeSuccessfully() throws Exception {
        AtomicReference<ManagedThreadContext> contextRef = new AtomicReference<>();
        AtomicReference<Session> sessionRef = new AtomicReference<>();
        ThreadContext.execute("wrapper-success", context -> {
            contextRef.set(context);
            sessionRef.set(context.getCurrentSession());
            return null;
        });
        return new Observation(contextRef.get(), sessionRef.get());
    }

    private static Observation executeWithFailure() {
        AtomicReference<ManagedThreadContext> contextRef = new AtomicReference<>();
        AtomicReference<Session> sessionRef = new AtomicReference<>();
        RuntimeException failure = new RuntimeException("wrapper boom");

        assertThatThrownBy(() -> ThreadContext.execute("wrapper-failure", context -> {
            contextRef.set(context);
            sessionRef.set(context.getCurrentSession());
            throw failure;
        })).isSameAs(failure);

        return new Observation(contextRef.get(), sessionRef.get());
    }

    private Observation executePropagatedFailure() throws InterruptedException {
        ManagedThreadContext source = ManagedThreadContext.create("wrapper-source");
        IllegalStateException failure = new IllegalStateException("propagated boom");
        AtomicReference<ManagedThreadContext> contextRef = new AtomicReference<>();
        AtomicReference<Session> sessionRef = new AtomicReference<>();
        AtomicReference<Throwable> thrownRef = new AtomicReference<>();
        Callable<Void> wrapped = manager.wrapCallable(() -> {
            contextRef.set(ManagedThreadContext.current());
            sessionRef.set(ManagedThreadContext.current().getCurrentSession());
            throw failure;
        });
        source.close();
        Thread worker = new Thread(() -> {
            try {
                wrapped.call();
            } catch (Throwable thrown) {
                thrownRef.set(thrown);
            }
        }, "lfc-02-wrapper-owner");

        worker.start();
        worker.join(5_000L);
        assertThat(worker.isAlive()).isFalse();
        assertThat(thrownRef.get()).isSameAs(failure);
        return new Observation(contextRef.get(), sessionRef.get());
    }

    private static Observation clearExplicitly() {
        ManagedThreadContext context = ManagedThreadContext.create("forced-clear");
        Session session = context.getCurrentSession();
        ThreadContext.clear();
        return new Observation(context, session);
    }

    private static Observation clearThroughProvider() {
        ManagedThreadContext context = ManagedThreadContext.create("provider-clear");
        Session session = context.getCurrentSession();
        new DefaultFlowProvider().clear();
        return new Observation(context, session);
    }

    private static Observation replaceActiveContext() {
        ManagedThreadContext context = ManagedThreadContext.create("replaced");
        Session session = context.getCurrentSession();
        ManagedThreadContext replacement = ManagedThreadContext.create("replacement");
        replacement.close();
        return new Observation(context, session);
    }

    private Observation cleanDeadThreadContext() throws InterruptedException {
        AtomicReference<ManagedThreadContext> contextRef = new AtomicReference<>();
        AtomicReference<Session> sessionRef = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            ManagedThreadContext context = ManagedThreadContext.create("leaked");
            contextRef.set(context);
            sessionRef.set(context.getCurrentSession());
        }, "lfc-02-leaked-owner");

        worker.start();
        worker.join(5_000L);
        assertThat(worker.isAlive()).isFalse();
        manager.detectAndCleanLeaks();

        return new Observation(contextRef.get(), sessionRef.get());
    }

    private Observation cleanForShutdown() {
        ManagedThreadContext context = ManagedThreadContext.create("shutdown");
        Session session = context.getCurrentSession();
        manager.forceCleanupAll("manager shutdown");
        return new Observation(context, session);
    }

    private static List<String> messageContents(Session session) {
        return session.getRootTask().getMessages().stream()
                .map(Message::getContent)
                .toList();
    }

    private enum TerminalCase {
        NORMAL_CLOSE(SessionStatus.COMPLETED, null),
        WRAPPER_SUCCESS(SessionStatus.COMPLETED, null),
        WRAPPER_FAILURE(SessionStatus.ERROR, "wrapper boom"),
        PROPAGATED_WRAPPER_FAILURE(SessionStatus.ERROR, "propagated boom"),
        FORCED_CLEAR(SessionStatus.ERROR, "explicit clear"),
        PROVIDER_CLEAR(SessionStatus.ERROR, "explicit clear"),
        REPLACEMENT(SessionStatus.ERROR, "replaced by new context"),
        LEAK_CLEANUP(SessionStatus.ERROR, "dead thread"),
        SHUTDOWN(SessionStatus.ERROR, "manager shutdown");

        private final SessionStatus expectedStatus;
        private final String expectedMessage;

        TerminalCase(SessionStatus expectedStatus, String expectedMessage) {
            this.expectedStatus = expectedStatus;
            this.expectedMessage = expectedMessage;
        }
    }

    private record Observation(ManagedThreadContext context, Session session) {
    }
}
