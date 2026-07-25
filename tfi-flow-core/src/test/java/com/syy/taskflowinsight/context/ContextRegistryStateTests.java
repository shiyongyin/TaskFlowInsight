package com.syy.taskflowinsight.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 验证 registry 计数只随身份状态迁移变化，不随重复回调或 map slot 数量变化。
 */
class ContextRegistryStateTests {

    private final SafeContextManager manager = SafeContextManager.getInstance();

    @AfterEach
    void tearDown() {
        manager.clearAllContextsForTesting();
    }

    @Test
    void emptyAndDuplicateBindUseIdentityDeltas() {
        ContextRegistryState state = new ContextRegistryState();
        ManagedThreadContext context = detachedContext("bind");

        assertTransition(state.bind(context), true, null, 1, 0);
        assertTransition(state.bind(context), false, null, 0, 0);
        assertCounts(state, 1, 1, 0);
    }

    @Test
    void liveReplacementClosesOnlyDisplacedIdentity() {
        ContextRegistryState state = new ContextRegistryState();
        ManagedThreadContext first = detachedContext("first");
        ManagedThreadContext second = detachedContext("second");
        state.bind(first);

        RegistryTransition transition = state.bind(second);

        assertTransition(transition, true, first, 1, 1);
        assertThat(state.lookup(second.getThreadId())).isSameAs(second);
        assertCounts(state, 1, 2, 1);
        assertTransition(state.terminalUnbind(first), false, null, 0, 0);
    }

    @Test
    void clearedReplacementAccountsStaleIdentityBeforeNewBind() throws Exception {
        ContextRegistryState state = new ContextRegistryState();
        ManagedThreadContext first = detachedContext("stale");
        ManagedThreadContext second = detachedContext("replacement");
        state.bind(first);
        clearRegistryReference(state, first.getThreadId());

        RegistryTransition transition = state.bind(second);

        assertTransition(transition, true, null, 1, 1);
        assertCounts(state, 1, 2, 1);
    }

    @Test
    void terminalUnbindRequiresExactIdentity() {
        ContextRegistryState state = new ContextRegistryState();
        ManagedThreadContext registered = detachedContext("registered");
        ManagedThreadContext other = detachedContext("other");
        state.bind(registered);

        assertTransition(state.terminalUnbind(other), false, null, 0, 0);
        assertTransition(state.terminalUnbind(registered), true, registered, 0, 1);
        assertTransition(state.terminalUnbind(registered), false, null, 0, 0);
        assertCounts(state, 0, 1, 1);
    }

    @Test
    void suspendAndResumeDoNotChangeLifetimeCounters() {
        ContextRegistryState state = new ContextRegistryState();
        ManagedThreadContext context = detachedContext("suspended");
        state.bind(context);
        RegistryCounts before = state.counts();

        SuspendedBinding token = state.suspend(context);
        assertCounts(state, 0, before.created(), before.closed());
        assertTransition(state.resume(token, null), true, null, 0, 0);

        assertThat(token.resolved()).isTrue();
        assertThat(state.lookup(context.getThreadId())).isSameAs(context);
        assertCounts(state, 1, before.created(), before.closed());
    }

    @Test
    void resumeOverExactFailedScopeClosesOnlyScopeIdentity() {
        ContextRegistryState state = new ContextRegistryState();
        ManagedThreadContext prior = detachedContext("prior");
        ManagedThreadContext failedScope = detachedContext("failed-scope");
        state.bind(prior);
        SuspendedBinding token = state.suspend(prior);
        state.bind(failedScope);

        RegistryTransition transition = state.resume(token, failedScope);

        assertTransition(transition, true, failedScope, 0, 1);
        assertThat(state.lookup(prior.getThreadId())).isSameAs(prior);
        assertCounts(state, 1, 2, 1);
    }

    @Test
    void abandonConsumesUnresolvedTokenExactlyOnce() {
        ContextRegistryState state = new ContextRegistryState();
        ManagedThreadContext context = detachedContext("abandoned");
        state.bind(context);
        SuspendedBinding token = state.suspend(context);

        assertTransition(state.abandon(token), true, context, 0, 1);
        assertTransition(state.abandon(token), false, null, 0, 0);

        assertThat(token.resolved()).isTrue();
        assertCounts(state, 0, 1, 1);
    }

    @Test
    void liveSnapshotPurgesClearedSlotOnce() throws Exception {
        ContextRegistryState state = new ContextRegistryState();
        ManagedThreadContext context = detachedContext("cleared");
        state.bind(context);
        clearRegistryReference(state, context.getThreadId());

        assertThat(state.liveContextsAndPurgeCleared()).isEmpty();
        assertThat(state.liveContextsAndPurgeCleared()).isEmpty();
        assertCounts(state, 0, 1, 1);
    }

    @Test
    void drainConsumesActiveAndUnresolvedSuspensionsExactlyOnce() {
        ContextRegistryState state = new ContextRegistryState();
        ManagedThreadContext suspended = detachedContext("suspended");
        ManagedThreadContext active = detachedContextOnWorker("active");
        state.bind(suspended);
        SuspendedBinding token = state.suspend(suspended);
        state.bind(active);

        assertThat(state.drainLiveContexts()).containsExactlyInAnyOrder(suspended, active);
        assertThat(state.drainLiveContexts()).isEmpty();

        assertThat(token.resolved()).isTrue();
        assertCounts(state, 0, 2, 2);
    }

    private static ManagedThreadContext detachedContext(String name) {
        ManagedThreadContext context = ManagedThreadContext.create(name);
        context.close();
        return context;
    }

    private static ManagedThreadContext detachedContextOnWorker(String name) {
        final ManagedThreadContext[] holder = new ManagedThreadContext[1];
        Thread worker = new Thread(() -> {
            holder[0] = ManagedThreadContext.create(name);
            holder[0].close();
        });
        try {
            worker.start();
            worker.join(5_000L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
        assertThat(worker.isAlive()).isFalse();
        return holder[0];
    }

    @SuppressWarnings("unchecked")
    private static void clearRegistryReference(ContextRegistryState state, long threadId) throws Exception {
        Field registryField = ContextRegistryState.class.getDeclaredField("registry");
        registryField.setAccessible(true);
        Map<Long, WeakReference<ManagedThreadContext>> registry =
                (Map<Long, WeakReference<ManagedThreadContext>>) registryField.get(state);
        registry.get(threadId).clear();
    }

    private static void assertTransition(
            RegistryTransition transition,
            boolean changed,
            ManagedThreadContext displaced,
            long createdDelta,
            long closedDelta) {
        assertThat(transition.changed()).isEqualTo(changed);
        assertThat(transition.displaced()).isSameAs(displaced);
        assertThat(transition.createdDelta()).isEqualTo(createdDelta);
        assertThat(transition.closedDelta()).isEqualTo(closedDelta);
    }

    private static void assertCounts(ContextRegistryState state, int active, long created, long closed) {
        assertThat(state.counts()).isEqualTo(new RegistryCounts(active, created, closed));
    }
}
