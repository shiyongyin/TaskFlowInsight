package com.syy.taskflowinsight.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.syy.taskflowinsight.model.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 验证 manager 只维护一个线程绑定，并把生命周期动作放在 registry 状态锁之外。
 */
class ContextRegistrationTests {

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

    @Test
    void duplicateBindAndTerminalUnbindChangeCountersOnce() {
        long createdBefore = manager.getContextCreatedCount();
        long closedBefore = manager.getContextClosedCount();
        ManagedThreadContext context = ManagedThreadContext.create("duplicate");

        manager.bindNewContext(context);
        context.close();
        context.close();

        assertThat(manager.getContextCreatedCount()).isEqualTo(createdBefore + 1);
        assertThat(manager.getContextClosedCount()).isEqualTo(closedBefore + 1);
        assertThat(manager.getActiveContextCount()).isZero();
    }

    @Test
    void replacementCreatesNewIdentityAndForceCleansDisplacedContext() {
        long createdBefore = manager.getContextCreatedCount();
        long closedBefore = manager.getContextClosedCount();
        ManagedThreadContext first = ManagedThreadContext.create("first");

        ManagedThreadContext second = ManagedThreadContext.create("second");

        assertThat(first.isClosed()).isTrue();
        assertThat(second.isClosed()).isFalse();
        assertThat(manager.getCurrentContext()).isSameAs(second);
        assertThat(manager.getContextCreatedCount()).isEqualTo(createdBefore + 2);
        assertThat(manager.getContextClosedCount()).isEqualTo(closedBefore + 1);
        second.close();
    }

    @Test
    void failedCreationKeepsCurrentContextBound() {
        ManagedThreadContext current = ManagedThreadContext.create("current");
        long created = manager.getContextCreatedCount();
        long closed = manager.getContextClosedCount();

        assertThatThrownBy(() -> ManagedThreadContext.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Root task name");

        assertThat(manager.getCurrentContext()).isSameAs(current);
        assertThat(current.isClosed()).isFalse();
        assertThat(manager.getContextCreatedCount()).isEqualTo(created);
        assertThat(manager.getContextClosedCount()).isEqualTo(closed);
        current.close();
    }

    @Test
    void suspendAndResumePreserveLifetimeCounters() {
        ManagedThreadContext context = ManagedThreadContext.create("prior");
        long created = manager.getContextCreatedCount();
        long closed = manager.getContextClosedCount();

        SuspendedBinding token = manager.suspendCurrentContext();
        assertThat(manager.getCurrentContext()).isNull();
        manager.resumeContext(token, null);

        assertThat(manager.getCurrentContext()).isSameAs(context);
        assertThat(manager.getContextCreatedCount()).isEqualTo(created);
        assertThat(manager.getContextClosedCount()).isEqualTo(closed);
        context.close();
    }

    @Test
    void terminalSessionWhileSuspendedIsReleasedInsteadOfResumed() {
        ManagedThreadContext context = ManagedThreadContext.create("suspended-terminal");
        Session session = context.getCurrentSession();
        long closedBefore = manager.getContextClosedCount();
        SuspendedBinding token = manager.suspendCurrentContext();

        session.complete();
        manager.resumeContext(token, null);

        assertThat(token.resolved()).isTrue();
        assertThat(session.getStatus().isTerminated()).isTrue();
        assertThat(context.isClosed()).isTrue();
        assertThat(manager.getCurrentContext()).isNull();
        assertThat(manager.getActiveContextCount()).isZero();
        assertThat(manager.getContextClosedCount()).isEqualTo(closedBefore + 1);
    }

    @Test
    void unrelatedOccupantDoesNotConsumeSuspension() throws Exception {
        ManagedThreadContext prior = ManagedThreadContext.create("prior");
        SuspendedBinding token = manager.suspendCurrentContext();
        ManagedThreadContext scope = ManagedThreadContext.create("scope");
        ManagedThreadContext unrelated = detachedContextOnWorker("unrelated");

        assertThatThrownBy(() -> manager.resumeContext(token, unrelated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not be resumed");
        assertThat(token.resolved()).isFalse();
        assertThat(manager.getCurrentContext()).isSameAs(scope);

        manager.resumeContext(token, scope);
        assertThat(token.resolved()).isTrue();
        assertThat(scope.isClosed()).isTrue();
        assertThat(manager.getCurrentContext()).isSameAs(prior);
        prior.close();
    }

    @Test
    void abandonConsumesTokenAndCleansPriorContext() {
        ManagedThreadContext context = ManagedThreadContext.create("abandon");
        SuspendedBinding token = manager.suspendCurrentContext();

        manager.abandonSuspendedContext(token, "scope abandoned");

        assertThat(token.resolved()).isTrue();
        assertThat(context.isClosed()).isTrue();
        assertThat(manager.getCurrentContext()).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyActivationUsesOneNonOwningContext() {
        long createdBefore = manager.getContextCreatedCount();
        long closedBefore = manager.getContextClosedCount();
        Session session = Session.create("legacy-adapter");

        session.activate();
        ManagedThreadContext wrapper = manager.getCurrentContext();
        session.activate();

        assertThat(wrapper).isNotNull();
        assertThat(wrapper.getCurrentSession()).isSameAs(session);
        assertThat(Session.getCurrent()).isSameAs(session);
        assertThat(Session.getActiveSessionCount()).isEqualTo(manager.getActiveContextCount());
        assertThat(manager.getContextCreatedCount()).isEqualTo(createdBefore + 1);

        session.deactivate();

        assertThat(session.getStatus().isActive()).isTrue();
        assertThat(wrapper.isClosed()).isTrue();
        assertThat(manager.getCurrentContext()).isNull();
        assertThat(manager.getContextClosedCount()).isEqualTo(closedBefore + 1);
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyActivationRejectsSessionCreatedOnAnotherThread() throws Exception {
        int activeBefore = manager.getActiveContextCount();
        Session session = Session.create("cross-thread-legacy-adapter");
        AtomicReference<Throwable> activationFailure = new AtomicReference<>();
        AtomicReference<LegacyActivationObservation> observation = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            ManagedThreadContext prior = ManagedThreadContext.create("worker-prior");
            try {
                long createdBeforeActivation = manager.getContextCreatedCount();
                long closedBeforeActivation = manager.getContextClosedCount();
                try {
                    session.activate();
                } catch (Throwable failure) {
                    activationFailure.set(failure);
                }
                observation.set(new LegacyActivationObservation(
                        prior,
                        manager.getCurrentContext(),
                        prior.isClosed(),
                        createdBeforeActivation,
                        manager.getContextCreatedCount(),
                        closedBeforeActivation,
                        manager.getContextClosedCount()));
            } finally {
                prior.close();
            }
        }, "cross-thread-session-activator");

        worker.start();
        worker.join(5_000L);

        assertThat(worker.isAlive()).isFalse();
        LegacyActivationObservation actual = observation.get();
        assertThat(actual).isNotNull();
        assertThat(activationFailure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session activation must occur on its creation thread");
        assertThat(actual.current()).isSameAs(actual.prior());
        assertThat(actual.priorClosed()).isFalse();
        assertThat(actual.createdAfter()).isEqualTo(actual.createdBefore());
        assertThat(actual.closedAfter()).isEqualTo(actual.closedBefore());
        assertThat(session.isActive()).isTrue();
        assertThat(manager.getActiveContextCount()).isEqualTo(activeBefore);
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyManagerBindingRejectsSessionCreatedOnAnotherThread() throws Exception {
        int activeBefore = manager.getActiveContextCount();
        Session session = Session.create("cross-thread-manager-adapter");
        AtomicReference<Throwable> bindingFailure = new AtomicReference<>();
        AtomicReference<LegacyActivationObservation> observation = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            ManagedThreadContext prior = ManagedThreadContext.create("manager-worker-prior");
            try {
                long createdBeforeBinding = manager.getContextCreatedCount();
                long closedBeforeBinding = manager.getContextClosedCount();
                try {
                    manager.bindLegacySession(session);
                } catch (Throwable failure) {
                    bindingFailure.set(failure);
                }
                observation.set(new LegacyActivationObservation(
                        prior,
                        manager.getCurrentContext(),
                        prior.isClosed(),
                        createdBeforeBinding,
                        manager.getContextCreatedCount(),
                        closedBeforeBinding,
                        manager.getContextClosedCount()));
            } finally {
                prior.close();
            }
        }, "cross-thread-manager-session-binder");

        worker.start();
        worker.join(5_000L);

        assertThat(worker.isAlive()).isFalse();
        LegacyActivationObservation actual = observation.get();
        assertThat(actual).isNotNull();
        assertThat(bindingFailure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session activation must occur on its creation thread");
        assertThat(actual.current()).isSameAs(actual.prior());
        assertThat(actual.priorClosed()).isFalse();
        assertThat(actual.createdAfter()).isEqualTo(actual.createdBefore());
        assertThat(actual.closedAfter()).isEqualTo(actual.closedBefore());
        assertThat(session.isActive()).isTrue();
        assertThat(manager.getActiveContextCount()).isEqualTo(activeBefore);
    }

    @Test
    @SuppressWarnings("deprecation")
    void lateTerminalDuringActivationRollsBackPublishedWrapper() throws Exception {
        long createdBefore = manager.getContextCreatedCount();
        long closedBefore = manager.getContextClosedCount();
        CountDownLatch wrapperPublished = new CountDownLatch(1);
        CountDownLatch allowReplacementCleanup = new CountDownLatch(1);
        AtomicReference<Session> sessionRef = new AtomicReference<>();
        AtomicReference<Throwable> activationFailure = new AtomicReference<>();
        AtomicReference<ManagedThreadContext> currentAfterFailure = new AtomicReference<>();

        Thread activator = new Thread(() -> {
            ManagedThreadContext displaced = new ManagedThreadContext((ignoredContext, ignoredSession) -> {
                wrapperPublished.countDown();
                await(allowReplacementCleanup);
            });
            displaced.startSession("displaced-owner");
            manager.bindNewContext(displaced);

            Session session = Session.create("late-terminal");
            sessionRef.set(session);
            try {
                session.activate();
            } catch (Throwable failure) {
                activationFailure.set(failure);
            }
            currentAfterFailure.set(manager.getCurrentContext());
        }, "legacy-session-activator");

        activator.start();
        assertThat(wrapperPublished.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            sessionRef.get().complete();
        } finally {
            allowReplacementCleanup.countDown();
        }
        activator.join(5_000L);

        assertThat(activator.isAlive()).isFalse();
        assertThat(activationFailure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot activate session that is not running. Current status: COMPLETED");
        assertThat(currentAfterFailure.get()).isNull();
        assertThat(manager.getActiveContextCount()).isZero();
        assertThat(manager.getContextCreatedCount()).isEqualTo(createdBefore + 2);
        assertThat(manager.getContextClosedCount()).isEqualTo(closedBefore + 2);
    }

    @Test
    void concurrentCyclesKeepClosedAtOrBelowCreated() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            int cycles = 40;
            ExecutorService executor = Executors.newFixedThreadPool(8);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            Set<String> contextIds = ConcurrentHashMap.newKeySet();
            try {
                for (int index = 0; index < cycles; index++) {
                    int taskIndex = index;
                    futures.add(executor.submit(() -> {
                        start.await();
                        ManagedThreadContext context = ManagedThreadContext.create("cycle-" + taskIndex);
                        assertThat(ManagedThreadContext.current()).isSameAs(context);
                        contextIds.add(context.getContextId());
                        context.close();
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    future.get();
                }
            } finally {
                executor.shutdownNow();
            }

            assertThat(contextIds).hasSize(cycles);
            assertThat(manager.getActiveContextCount()).isZero();
            assertThat(manager.getContextClosedCount()).isLessThanOrEqualTo(manager.getContextCreatedCount());
        });
    }

    @Test
    void managedLifecycleUsesIdentityOperationsWithoutThreeOneLedgerMetadata() throws Exception {
        String managedSource = Files.readString(Path.of(
                "src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java"));
        String managerSource = Files.readString(Path.of(
                "src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java"));

        assertThat(managedSource).contains("manager.bindNewContext(context)");
        assertThat(managerSource).contains("bindNewContext(restored)");
        assertThat(managedSource)
                .doesNotContain("SafeContextManager.getInstance().bindNewContext(context)");
        assertThat(managedSource).contains("SafeContextManager.getInstance().terminalUnbind(this)");
        assertThat(managedSource).doesNotContain(".registerContext(context)", ".unregisterContext(this)");
        assertThat(managerSource).doesNotContain("@Deprecated(since = \"3.1.0\"");
    }

    @Test
    void sessionAdapterHasFourZeroMetadataAndNoRegistryOwner() throws Exception {
        String sessionSource = Files.readString(Path.of(
                "src/main/java/com/syy/taskflowinsight/model/Session.java"));
        String managerSource = Files.readString(Path.of(
                "src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java"));

        assertThat(sessionSource).doesNotContain("THREAD_SESSIONS", "ThreadLocal<Session>", "3.1.0");
        assertThat(managerSource).doesNotContain("ThreadLocal<Session>", "3.1.0");
        assertDeprecatedSinceFour(Session.class.getMethod("getCurrent"));
        assertDeprecatedSinceFour(Session.class.getMethod("activate"));
        assertDeprecatedSinceFour(Session.class.getMethod("deactivate"));
        assertDeprecatedSinceFour(Session.class.getMethod("getActiveSessionCount"));
        assertDeprecatedSinceFour(Session.class.getMethod("cleanupInactiveSessions"));
        assertDeprecatedSinceFour(SafeContextManager.class.getMethod("bindLegacySession", Session.class));
        assertDeprecatedSinceFour(SafeContextManager.class.getMethod("unbindLegacySession", Session.class));
        assertThat(SafeContextManager.class
                .getMethod("releaseExternallyTerminatedSession", Session.class)
                .getAnnotation(Deprecated.class)).isNull();
    }

    private static ManagedThreadContext detachedContextOnWorker(String name) throws Exception {
        ManagedThreadContext[] holder = new ManagedThreadContext[1];
        Thread worker = new Thread(() -> {
            holder[0] = ManagedThreadContext.create(name);
            holder[0].close();
        });
        worker.start();
        worker.join(5_000L);
        assertThat(worker.isAlive()).isFalse();
        return holder[0];
    }

    private static void assertDeprecatedSinceFour(Method method) {
        Deprecated annotation = method.getAnnotation(Deprecated.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.since()).isEqualTo("4.0.0");
        assertThat(annotation.forRemoval()).isFalse();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for activation race");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for activation race", interrupted);
        }
    }

    private record LegacyActivationObservation(
            ManagedThreadContext prior,
            ManagedThreadContext current,
            boolean priorClosed,
            long createdBefore,
            long createdAfter,
            long closedBefore,
            long closedAfter) {
    }
}
