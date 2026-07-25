package com.syy.taskflowinsight.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 验证临时传播只借用线程绑定，并在作用域结束后恢复原身份。
 */
class ContextScopeTests {

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
    void nullSnapshotWithoutPriorKeepsTheSlotEmpty() {
        ContextMetrics before = manager.metrics();

        ContextScope scope = ContextScope.open(null);

        assertThat(scope.context()).isNull();
        assertThat(manager.getCurrentContext()).isNull();
        scope.close();
        assertDeltas(before, 0, 0, 0);
    }

    @Test
    void nullSnapshotSuspendsAndRestoresPollutedWorkerWithoutPropagation() {
        ManagedThreadContext prior = ManagedThreadContext.create("polluted-worker");
        ContextMetrics before = manager.metrics();

        try (ContextScope scope = ContextScope.open(null)) {
            assertThat(scope.context()).isNull();
            assertThat(manager.getCurrentContext()).isNull();
        }

        assertThat(manager.getCurrentContext()).isSameAs(prior);
        assertThat(prior.isClosed()).isFalse();
        assertDeltas(before, 0, 0, 0);
        prior.close();
    }

    @Test
    void sameSourceCallerRunsReusesCurrentAndCountsOnePropagation() {
        ManagedThreadContext current = ManagedThreadContext.create("same-source");
        ContextSnapshot snapshot = current.createSnapshot();
        ContextMetrics before = manager.metrics();

        try (ContextScope scope = ContextScope.open(snapshot)) {
            assertThat(scope.context()).isSameAs(current);
            assertThat(manager.getCurrentContext()).isSameAs(current);
        }

        assertThat(current.isClosed()).isFalse();
        assertThat(manager.getCurrentContext()).isSameAs(current);
        assertDeltas(before, 0, 0, 1);
        current.close();
    }

    @Test
    void differentSourceCallerRunsRestoresExactPriorIdentity() {
        ContextSnapshot snapshot = closedSourceSnapshot("captured-source");
        ManagedThreadContext prior = ManagedThreadContext.create("caller-runs-prior");
        ContextMetrics before = manager.metrics();
        ManagedThreadContext restored;

        try (ContextScope scope = ContextScope.open(snapshot)) {
            restored = scope.context();
            assertThat(restored).isNotNull().isNotSameAs(prior);
            assertThat(manager.getCurrentContext()).isSameAs(restored);
            assertThat(restored.<String>getAttribute("parent.contextId"))
                    .isEqualTo(snapshot.getContextId());
        }

        assertThat(restored.isClosed()).isTrue();
        assertThat(manager.getCurrentContext()).isSameAs(prior);
        assertThat(prior.isClosed()).isFalse();
        assertDeltas(before, 1, 1, 1);
        prior.close();
    }

    @Test
    void repeatedCloseConsumesTheScopeOnlyOnce() {
        ContextSnapshot snapshot = closedSourceSnapshot("repeat-source");
        ManagedThreadContext prior = ManagedThreadContext.create("repeat-prior");
        ContextMetrics before = manager.metrics();
        ContextScope scope = ContextScope.open(snapshot);

        scope.close();
        ContextMetrics afterFirstClose = manager.metrics();
        scope.close();
        ContextMetrics afterSecondClose = manager.metrics();

        assertThat(manager.getCurrentContext()).isSameAs(prior);
        assertThat(afterSecondClose.createdContexts()).isEqualTo(afterFirstClose.createdContexts());
        assertThat(afterSecondClose.closedContexts()).isEqualTo(afterFirstClose.closedContexts());
        assertThat(afterSecondClose.propagations()).isEqualTo(afterFirstClose.propagations());
        assertDeltas(before, 1, 1, 1);
        prior.close();
    }

    @Test
    void restoreFailureRestoresPriorWithoutCountingPropagation() {
        ManagedThreadContext prior = ManagedThreadContext.create("restore-failure-prior");
        ContextSnapshot invalid = new ContextSnapshot("source", "session", "/child", System.nanoTime());
        ContextMetrics before = manager.metrics();

        assertThatThrownBy(() -> ContextScope.open(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Root task name");

        assertThat(manager.getCurrentContext()).isSameAs(prior);
        assertThat(prior.isClosed()).isFalse();
        assertDeltas(before, 0, 0, 0);
        prior.close();
    }

    @Test
    void sessionTerminatingWhileSuspendedIsNotRebound() {
        ContextSnapshot snapshot = closedSourceSnapshot("terminal-source");
        ManagedThreadContext prior = ManagedThreadContext.create("terminal-prior");
        Session priorSession = prior.getCurrentSession();
        ContextMetrics before = manager.metrics();

        try (ContextScope ignored = ContextScope.open(snapshot)) {
            priorSession.complete();
            assertThat(prior.isClosed()).isFalse();
        }

        assertThat(priorSession.getStatus().isTerminated()).isTrue();
        assertThat(prior.isClosed()).isTrue();
        assertThat(manager.getCurrentContext()).isNull();
        assertDeltas(before, 1, 2, 1);
    }

    @Test
    void snapshotMatchesOnlyItsLiveSourceIdentity() {
        ManagedThreadContext source = ManagedThreadContext.create("match-source");
        ContextSnapshot snapshot = source.createSnapshot();

        assertThat(snapshot.matches(source)).isTrue();
        source.close();
        assertThat(snapshot.matches(source)).isFalse();

        ManagedThreadContext other = ManagedThreadContext.create("match-other");
        assertThat(snapshot.matches(other)).isFalse();
        other.close();
    }

    @Test
    void nullSnapshotWrapperHidesAndRestoresPollutedWorker() {
        AtomicReference<ManagedThreadContext> observed = new AtomicReference<>();
        Runnable wrapped = manager.wrapRunnable(() -> observed.set(ManagedThreadContext.current()));
        ManagedThreadContext prior = ManagedThreadContext.create("null-wrapper-prior");
        ContextMetrics before = manager.metrics();

        wrapped.run();

        assertThat(observed.get()).isNull();
        assertThat(manager.getCurrentContext()).isSameAs(prior);
        assertDeltas(before, 0, 0, 0);
        prior.close();
    }

    @Test
    void differentSourceWrapperTemporarilyReplacesAndRestoresCallerRunsContext() {
        ManagedThreadContext source = ManagedThreadContext.create("wrapper-source");
        String sourceId = source.getContextId();
        AtomicReference<ManagedThreadContext> observed = new AtomicReference<>();
        AtomicReference<String> linkedParentId = new AtomicReference<>();
        Runnable wrapped = manager.wrapRunnable(() -> {
            ManagedThreadContext active = ManagedThreadContext.current();
            observed.set(active);
            linkedParentId.set(active.getAttribute("parent.contextId"));
        });
        source.close();
        ManagedThreadContext prior = ManagedThreadContext.create("wrapper-prior");
        ContextMetrics before = manager.metrics();

        wrapped.run();

        assertThat(observed.get()).isNotNull().isNotSameAs(prior);
        assertThat(linkedParentId.get()).isEqualTo(sourceId);
        assertThat(observed.get().isClosed()).isTrue();
        assertThat(manager.getCurrentContext()).isSameAs(prior);
        assertDeltas(before, 1, 1, 1);
        prior.close();
    }

    @Test
    void sameSourceAndNestedWrappersReuseContextAndCountEachApplication() {
        ManagedThreadContext current = ManagedThreadContext.create("nested-source");
        AtomicReference<ManagedThreadContext> outerObserved = new AtomicReference<>();
        AtomicReference<ManagedThreadContext> innerObserved = new AtomicReference<>();
        Runnable outer = manager.wrapRunnable(() -> {
            outerObserved.set(ManagedThreadContext.current());
            manager.wrapRunnable(() -> innerObserved.set(ManagedThreadContext.current())).run();
        });
        ContextMetrics before = manager.metrics();

        outer.run();

        assertThat(outerObserved.get()).isSameAs(current);
        assertThat(innerObserved.get()).isSameAs(current);
        assertThat(manager.getCurrentContext()).isSameAs(current);
        assertDeltas(before, 0, 0, 2);
        current.close();
    }

    @Test
    void callableFailureKeepsBusinessThrowableAndRestoresPrior() {
        ManagedThreadContext source = ManagedThreadContext.create("callable-source");
        AtomicReference<ManagedThreadContext> child = new AtomicReference<>();
        Exception businessFailure = new Exception("callable failed");
        Callable<Void> wrapped = manager.wrapCallable(() -> {
            child.set(ManagedThreadContext.current());
            throw businessFailure;
        });
        source.close();
        ManagedThreadContext prior = ManagedThreadContext.create("callable-prior");

        assertThatThrownBy(wrapped::call).isSameAs(businessFailure);

        assertThat(child.get()).isNotNull().isNotSameAs(prior);
        assertThat(child.get().isClosed()).isTrue();
        assertThat(manager.getCurrentContext()).isSameAs(prior);
        assertThat(prior.isClosed()).isFalse();
        prior.close();
    }

    @Test
    void delegateFailureRemainsPrimaryWhenScopeFailFails() {
        ManagedThreadContext source = ManagedThreadContext.create("failure-source");
        RuntimeException businessFailure = new RuntimeException("business failed");
        RuntimeException signalFailure = new RuntimeException("failure signal failed");
        AtomicReference<ManagedThreadContext> child = new AtomicReference<>();
        Runnable wrapped = manager.wrapRunnable(() -> {
            ManagedThreadContext active = ManagedThreadContext.current();
            child.set(active);
            installTaskFailure(active, signalFailure);
            throw businessFailure;
        });
        source.close();
        ManagedThreadContext prior = ManagedThreadContext.create("failure-prior");

        assertThatThrownBy(wrapped::run)
                .isSameAs(businessFailure)
                .satisfies(thrown -> assertThat(thrown.getSuppressed())
                        .containsExactly(signalFailure));

        assertThat(child.get().isClosed()).isTrue();
        assertThat(manager.getCurrentContext()).isSameAs(prior);
        assertThat(prior.isClosed()).isFalse();
        prior.close();
    }

    @Test
    void closeFailureRemainsPrimaryWhenResumeAlsoFails() {
        ContextSnapshot snapshot = closedSourceSnapshot("close-failure-source");
        ManagedThreadContext prior = ManagedThreadContext.create("close-failure-prior");
        ContextScope scope = ContextScope.open(snapshot);
        RuntimeException closeFailure = new RuntimeException("active close failed");
        installTerminalFailure(scope.context(), closeFailure);

        manager.clearCurrentBinding(scope.context());
        ManagedThreadContext unrelated = ManagedThreadContext.create("unrelated-occupant");

        assertThatThrownBy(scope::close)
                .isSameAs(closeFailure)
                .satisfies(thrown -> {
                    assertThat(thrown.getSuppressed()).hasSize(1);
                    assertThat(thrown.getSuppressed()[0])
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("could not be resumed");
                });

        assertThat(prior.isClosed()).isTrue();
        assertThat(manager.getCurrentContext()).isSameAs(unrelated);
        unrelated.close();
    }

    @Test
    void resolvedSuspensionCannotBeConsumedAgain() {
        ManagedThreadContext prior = ManagedThreadContext.create("resolved-prior");
        SuspendedBinding token = manager.suspendCurrentContext();
        manager.resumeContext(token, null);

        assertThatThrownBy(() -> manager.resumeContext(token, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been resolved");

        assertThat(manager.getCurrentContext()).isSameAs(prior);
        prior.close();
    }

    @Test
    void unregisteredCurrentBindingCannotBeMistakenForAnEmptySlot() {
        ManagedThreadContext orphan = new ManagedThreadContext(ContextTerminalProbe.NO_OP);
        orphan.startSession("orphan-current");
        installCurrentBinding(orphan);

        assertThatThrownBy(() -> ContextScope.open(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not be suspended");

        assertThat(manager.getCurrentContext()).isSameAs(orphan);
        orphan.forceCleanup("test orphan cleanup");
        assertThat(manager.getCurrentContext()).isNull();
    }

    private ContextSnapshot closedSourceSnapshot(String taskName) {
        ManagedThreadContext source = ManagedThreadContext.create(taskName);
        ContextSnapshot snapshot = source.createSnapshot();
        source.close();
        return snapshot;
    }

    private static void installTerminalFailure(
            ManagedThreadContext context, RuntimeException terminalFailure) {
        try {
            Field field = ManagedThreadContext.class.getDeclaredField("terminalProbe");
            field.setAccessible(true);
            field.set(context, (ContextTerminalProbe) (ignoredContext, ignoredSession) -> {
                throw terminalFailure;
            });
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new AssertionError("Unable to install deterministic terminal failure", reflectionFailure);
        }
    }

    private static void installTaskFailure(
            ManagedThreadContext context, RuntimeException taskFailure) {
        TaskNode task = context.startTask("failure-probe");
        try {
            Field field = TaskNode.class.getDeclaredField("messages");
            field.setAccessible(true);
            field.set(task, new AbstractList<Message>() {
                @Override
                public Message get(int index) {
                    throw new IndexOutOfBoundsException(index);
                }

                @Override
                public int size() {
                    return 0;
                }

                @Override
                public boolean add(Message message) {
                    throw taskFailure;
                }
            });
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new AssertionError("Unable to install deterministic task failure", reflectionFailure);
        }
    }

    @SuppressWarnings("unchecked")
    private static void installCurrentBinding(ManagedThreadContext context) {
        try {
            Field field = SafeContextManager.class.getDeclaredField("CONTEXT_LOCAL");
            field.setAccessible(true);
            ThreadLocal<ManagedThreadContext> current =
                    (ThreadLocal<ManagedThreadContext>) field.get(null);
            current.set(context);
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new AssertionError("Unable to install deterministic orphan binding", reflectionFailure);
        }
    }

    private void assertDeltas(
            ContextMetrics before, long createdDelta, long closedDelta, long propagationDelta) {
        ContextMetrics after = manager.metrics();
        assertThat(after.createdContexts()).isEqualTo(before.createdContexts() + createdDelta);
        assertThat(after.closedContexts()).isEqualTo(before.closedContexts() + closedDelta);
        assertThat(after.propagations()).isEqualTo(before.propagations() + propagationDelta);
    }

}
