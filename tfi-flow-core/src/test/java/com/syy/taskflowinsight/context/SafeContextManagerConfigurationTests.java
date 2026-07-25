package com.syy.taskflowinsight.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 验证配置快照与 detector runtime 作为同一个 generation 原子发布。
 */
class SafeContextManagerConfigurationTests {

    private TrackingSchedulerFactory schedulerFactory;
    private SafeContextManager manager;

    @BeforeEach
    void setUp() {
        schedulerFactory = new TrackingSchedulerFactory();
        manager = new SafeContextManager(schedulerFactory, false);
    }

    @AfterEach
    void tearDown() {
        manager.shutdownForTesting();
    }

    @Test
    void timeoutOnlyChangeReusesCurrentDetector() {
        ContextManagerConfig initial = new ContextManagerConfig(60_000L, true, 5_000L);
        ContextManagerConfig timeoutOnly = new ContextManagerConfig(120_000L, true, 5_000L);

        manager.apply(initial);
        TrackingScheduler detector = schedulerFactory.latest();
        manager.apply(timeoutOnly);

        assertThat(manager.currentConfigSnapshot()).isEqualTo(timeoutOnly);
        assertThat(schedulerFactory.createdCount()).isOne();
        assertThat(detector.isShutdown()).isFalse();
    }

    @Test
    void nullConfigurationLeavesPublishedConfigAndDetectorUnchanged() {
        ContextManagerConfig current = new ContextManagerConfig(60_000L, true, 5_000L);
        manager.apply(current);
        TrackingScheduler detector = schedulerFactory.latest();
        int createdBefore = schedulerFactory.createdCount();

        assertThatThrownBy(() -> manager.apply(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("ContextManagerConfig cannot be null");

        assertThat(manager.currentConfigSnapshot()).isSameAs(current);
        assertThat(schedulerFactory.latest()).isSameAs(detector);
        assertThat(schedulerFactory.createdCount()).isEqualTo(createdBefore);
        assertThat(detector.isShutdown()).isFalse();
    }

    @Test
    void enableDisableAndIntervalChangesReplaceDetectorOnce() {
        manager.apply(new ContextManagerConfig(60_000L, true, 5_000L));
        TrackingScheduler first = schedulerFactory.latest();

        manager.apply(new ContextManagerConfig(60_000L, true, 10_000L));
        TrackingScheduler second = schedulerFactory.latest();

        assertThat(schedulerFactory.createdCount()).isEqualTo(2);
        assertThat(first.isShutdown()).isTrue();
        assertThat(second.isShutdown()).isFalse();

        manager.apply(new ContextManagerConfig(60_000L, false, 10_000L));
        assertThat(second.isShutdown()).isTrue();

        manager.apply(new ContextManagerConfig(60_000L, true, 10_000L));
        assertThat(schedulerFactory.createdCount()).isEqualTo(3);
        assertThat(schedulerFactory.latest().isShutdown()).isFalse();
    }

    @Test
    void preparationFailurePreservesCurrentConfigAndDetector() {
        ContextManagerConfig current = new ContextManagerConfig(60_000L, true, 5_000L);
        manager.apply(current);
        TrackingScheduler detector = schedulerFactory.latest();
        schedulerFactory.failNextCreation(new IllegalStateException("factory boom"));

        assertThatThrownBy(() -> manager.apply(
                new ContextManagerConfig(120_000L, true, 10_000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("factory boom");

        assertThat(manager.currentConfigSnapshot()).isEqualTo(current);
        assertThat(detector.isShutdown()).isFalse();
        assertThat(schedulerFactory.createdCount()).isOne();
    }

    @Test
    void schedulingFailureStopsPreparedExecutorAndPreservesCurrentRuntime() {
        ContextManagerConfig current = new ContextManagerConfig(60_000L, true, 5_000L);
        manager.apply(current);
        TrackingScheduler currentDetector = schedulerFactory.latest();
        schedulerFactory.failNextScheduling(new IllegalArgumentException("schedule boom"));

        assertThatThrownBy(() -> manager.apply(
                new ContextManagerConfig(120_000L, true, 10_000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("schedule boom");

        assertThat(manager.currentConfigSnapshot()).isEqualTo(current);
        assertThat(currentDetector.isShutdown()).isFalse();
        assertThat(schedulerFactory.latest().isShutdown()).isTrue();
    }

    @Test
    void nullScheduledTaskStopsPreparedExecutorAndPreservesCurrentRuntime() {
        ContextManagerConfig current = new ContextManagerConfig(60_000L, true, 5_000L);
        manager.apply(current);
        TrackingScheduler currentDetector = schedulerFactory.latest();
        schedulerFactory.returnNullTaskOnNextScheduling();

        assertThatThrownBy(() -> manager.apply(
                new ContextManagerConfig(120_000L, true, 10_000L)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Scheduled leak detector task cannot be null");

        assertThat(manager.currentConfigSnapshot()).isEqualTo(current);
        assertThat(currentDetector.isShutdown()).isFalse();
        assertThat(schedulerFactory.latest().isShutdown()).isTrue();
    }

    @Test
    void shutdownIsTerminalAndDoesNotConstructAnotherDetector() {
        manager.apply(new ContextManagerConfig(60_000L, true, 5_000L));
        TrackingScheduler detector = schedulerFactory.latest();
        manager.shutdownForTesting();
        int createdAtShutdown = schedulerFactory.createdCount();

        assertThat(detector.isShutdown()).isTrue();
        assertThatThrownBy(() -> manager.apply(
                new ContextManagerConfig(60_000L, true, 10_000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SafeContextManager is shut down");
        assertThat(schedulerFactory.createdCount()).isEqualTo(createdAtShutdown);
    }

    @Test
    void retiredGenerationCallbackCannotSelectLeaks() throws Exception {
        manager.apply(new ContextManagerConfig(60_000L, true, 5_000L));
        TrackingScheduler retired = schedulerFactory.latest();
        ManagedThreadContext leaked = bindContextOnTerminatedThread(ContextTerminalProbe.NO_OP);

        manager.apply(new ContextManagerConfig(60_000L, true, 10_000L));
        retired.runCallback();

        assertThat(leaked.isClosed()).isFalse();
        assertThat(manager.getActiveContextCount()).isOne();

        schedulerFactory.latest().runCallback();
        assertThat(leaked.isClosed()).isTrue();
        assertThat(manager.getActiveContextCount()).isZero();
    }

    @Test
    void callbackRechecksGenerationAfterAcquiringScanLock() throws Exception {
        manager.apply(new ContextManagerConfig(60_000L, true, 5_000L));
        TrackingScheduler retired = schedulerFactory.latest();
        ManagedThreadContext leaked = bindContextOnTerminatedThread(ContextTerminalProbe.NO_OP);
        Object scanLock = fieldValue(manager, "leakScanLock");
        Thread callback = new Thread(retired::runCallback, "tfi-retired-generation-callback");

        synchronized (scanLock) {
            callback.start();
            awaitBlocked(callback);
            manager.apply(new ContextManagerConfig(60_000L, true, 10_000L));
        }
        callback.join(5_000L);

        assertThat(callback.isAlive()).isFalse();
        assertThat(leaked.isClosed()).isFalse();
        assertThat(manager.getActiveContextCount()).isOne();
        schedulerFactory.latest().runCallback();
        assertThat(leaked.isClosed()).isTrue();
    }

    @Test
    void configurationPublicationCannotOvertakeInFlightGenerationSelection() throws Exception {
        manager.apply(new ContextManagerConfig(60_000L, true, 5_000L));
        TrackingScheduler currentDetector = schedulerFactory.latest();
        CountDownLatch selectionEntered = new CountDownLatch(1);
        CountDownLatch allowSelection = new CountDownLatch(1);
        BlockingLeakContext leaked = bindContextOnTerminatedThread(
                () -> new BlockingLeakContext(selectionEntered, allowSelection));
        AtomicReference<Throwable> applyFailure = new AtomicReference<>();
        Thread callback = new Thread(currentDetector::runCallback, "tfi-selection-linearization-callback");
        Thread applying = new Thread(() -> {
            try {
                manager.apply(new ContextManagerConfig(60_000L, true, 10_000L));
            } catch (Throwable failure) {
                applyFailure.set(failure);
            }
        }, "tfi-selection-linearization-apply");

        callback.start();
        assertThat(selectionEntered.await(5, TimeUnit.SECONDS)).isTrue();
        applying.start();
        boolean publicationWaitedForSelection = waitUntilBlockedOrTerminated(applying);
        allowSelection.countDown();
        callback.join(5_000L);
        applying.join(5_000L);

        assertThat(publicationWaitedForSelection).isTrue();
        assertThat(callback.isAlive() || applying.isAlive()).isFalse();
        assertThat(applyFailure.get()).isNull();
        assertThat(leaked.isClosed()).isTrue();
    }

    @Test
    void observersSeeOnlyCompleteConfigurationTriples() throws Exception {
        ContextManagerConfig first = new ContextManagerConfig(10_000L, false, 1_000L);
        ContextManagerConfig second = new ContextManagerConfig(20_000L, false, 2_000L);
        Set<ContextManagerConfig> allowed = Set.of(ContextManagerConfig.defaults(), first, second);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread firstWriter = new Thread(() -> applyRepeatedly(first, start, failure));
        Thread secondWriter = new Thread(() -> applyRepeatedly(second, start, failure));
        Thread observer = new Thread(() -> {
            await(start);
            for (int index = 0; index < 1_000; index++) {
                ContextManagerConfig observed = manager.currentConfigSnapshot();
                if (!allowed.contains(observed)) {
                    failure.compareAndSet(null, new AssertionError("mixed config: " + observed));
                    return;
                }
            }
        });

        firstWriter.start();
        secondWriter.start();
        observer.start();
        start.countDown();
        firstWriter.join(5_000L);
        secondWriter.join(5_000L);
        observer.join(5_000L);

        assertThat(failure.get()).isNull();
        assertThat(firstWriter.isAlive() || secondWriter.isAlive() || observer.isAlive()).isFalse();
    }

    @Test
    void shutdownLinearizesAfterInFlightPreparationAndRemainsTerminal() throws Exception {
        CountDownLatch factoryEntered = new CountDownLatch(1);
        CountDownLatch allowFactory = new CountDownLatch(1);
        schedulerFactory.blockNextCreation(factoryEntered, allowFactory);
        AtomicReference<Throwable> applyFailure = new AtomicReference<>();
        Thread applying = new Thread(() -> {
            try {
                manager.apply(new ContextManagerConfig(60_000L, true, 5_000L));
            } catch (Throwable failure) {
                applyFailure.set(failure);
            }
        });
        Thread shuttingDown = new Thread(manager::shutdownForTesting);

        applying.start();
        assertThat(factoryEntered.await(5, TimeUnit.SECONDS)).isTrue();
        shuttingDown.start();
        allowFactory.countDown();
        applying.join(5_000L);
        shuttingDown.join(5_000L);

        assertThat(applyFailure.get()).isNull();
        assertThat(schedulerFactory.latest().isShutdown()).isTrue();
        assertThatThrownBy(() -> manager.apply(ContextManagerConfig.defaults()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SafeContextManager is shut down");
    }

    @Test
    void leakIdentityIsUnboundBeforeContextCleanupCanBlock() throws Exception {
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch allowCleanup = new CountDownLatch(1);
        ManagedThreadContext leaked = bindContextOnTerminatedThread(
                (ignoredContext, ignoredSession) -> {
                    cleanupEntered.countDown();
                    await(allowCleanup);
                });
        manager.apply(new ContextManagerConfig(60_000L, true, 5_000L));

        Thread callback = new Thread(
                schedulerFactory.latest()::runCallback, "tfi-config-test-callback");
        callback.start();
        assertThat(cleanupEntered.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(manager.getActiveContextCount()).isZero();
            assertThat(leaked.isClosed()).isFalse();
        } finally {
            allowCleanup.countDown();
        }
        callback.join(5_000L);

        assertThat(callback.isAlive()).isFalse();
        assertThat(leaked.isClosed()).isTrue();
    }

    private ManagedThreadContext bindContextOnTerminatedThread(ContextTerminalProbe terminalProbe)
            throws InterruptedException {
        return bindContextOnTerminatedThread(() -> new ManagedThreadContext(terminalProbe));
    }

    private <T extends ManagedThreadContext> T bindContextOnTerminatedThread(Supplier<T> contextFactory)
            throws InterruptedException {
        AtomicReference<T> contextRef = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread owner = new Thread(() -> {
            try {
                T context = contextFactory.get();
                context.startSession("leaked-config-context");
                manager.bindNewContext(context);
                contextRef.set(context);
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        }, "tfi-config-test-owner");
        owner.start();
        owner.join(5_000L);

        assertThat(owner.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        return contextRef.get();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for leak cleanup");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for leak cleanup", interrupted);
        }
    }

    private void applyRepeatedly(
            ContextManagerConfig config,
            CountDownLatch start,
            AtomicReference<Throwable> failure) {
        try {
            await(start);
            for (int index = 0; index < 100; index++) {
                manager.apply(config);
            }
        } catch (Throwable thrown) {
            failure.compareAndSet(null, thrown);
        }
    }

    private static void awaitBlocked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(thread.getState()).isEqualTo(Thread.State.BLOCKED);
    }

    private static boolean waitUntilBlockedOrTerminated(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.isAlive()
                && thread.getState() != Thread.State.BLOCKED
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        return thread.getState() == Thread.State.BLOCKED;
    }

    private static Object fieldValue(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class TrackingSchedulerFactory implements ScheduledExecutorFactory {
        private final List<TrackingScheduler> created = new ArrayList<>();
        private RuntimeException nextFailure;
        private RuntimeException nextSchedulingFailure;
        private boolean returnNullTask;
        private CountDownLatch creationEntered;
        private CountDownLatch allowCreation;

        @Override
        public synchronized TrackingScheduler create(String threadName) {
            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;
                throw failure;
            }
            if (creationEntered != null) {
                creationEntered.countDown();
                await(allowCreation);
                creationEntered = null;
                allowCreation = null;
            }
            TrackingScheduler scheduler = new TrackingScheduler(nextSchedulingFailure, returnNullTask);
            nextSchedulingFailure = null;
            returnNullTask = false;
            created.add(scheduler);
            return scheduler;
        }

        synchronized void failNextCreation(RuntimeException failure) {
            nextFailure = failure;
        }

        synchronized void failNextScheduling(RuntimeException failure) {
            nextSchedulingFailure = failure;
        }

        synchronized void returnNullTaskOnNextScheduling() {
            returnNullTask = true;
        }

        synchronized void blockNextCreation(CountDownLatch entered, CountDownLatch allow) {
            creationEntered = entered;
            allowCreation = allow;
        }

        synchronized int createdCount() {
            return created.size();
        }

        synchronized TrackingScheduler latest() {
            return created.getLast();
        }
    }

    private static final class BlockingLeakContext extends ManagedThreadContext {
        private final CountDownLatch selectionEntered;
        private final CountDownLatch allowSelection;

        BlockingLeakContext(CountDownLatch selectionEntered, CountDownLatch allowSelection) {
            super(ContextTerminalProbe.NO_OP);
            this.selectionEntered = selectionEntered;
            this.allowSelection = allowSelection;
        }

        @Override
        public boolean isOwnerThreadAlive() {
            selectionEntered.countDown();
            await(allowSelection);
            return false;
        }
    }

    private static final class TrackingScheduler extends ScheduledThreadPoolExecutor {
        private Runnable callback;
        private final RuntimeException schedulingFailure;
        private final boolean returnNullTask;

        TrackingScheduler(RuntimeException schedulingFailure, boolean returnNullTask) {
            super(1, runnable -> {
                Thread thread = new Thread(runnable, "tfi-config-test-detector");
                thread.setDaemon(true);
                return thread;
            });
            this.schedulingFailure = schedulingFailure;
            this.returnNullTask = returnNullTask;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command, long initialDelay, long delay, TimeUnit unit) {
            if (schedulingFailure != null) {
                throw schedulingFailure;
            }
            callback = command;
            return returnNullTask ? null : new TrackingFuture();
        }

        void runCallback() {
            callback.run();
        }
    }

    private static final class TrackingFuture implements ScheduledFuture<Object> {
        private boolean cancelled;

        @Override
        public long getDelay(TimeUnit unit) {
            return Long.MAX_VALUE;
        }

        @Override
        public int compareTo(Delayed other) {
            return other == this ? 0 : 1;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException("not used by configuration tests");
        }

        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException("not used by configuration tests");
        }
    }
}
