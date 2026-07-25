package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link SafeContextManager} 单元测试。
 *
 * <p>覆盖 getCurrentContext、detectAndCleanLeaks、LeakListener、异步传播与指标路径。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.1
 */
class SafeContextManagerTest {

    private SafeContextManager manager;

    @BeforeEach
    void setup() {
        manager = SafeContextManager.getInstance();
        // 确保干净状态
        manager.clearAllContextsForTesting();
        manager.apply(ContextManagerConfig.defaults());
    }

    @AfterEach
    void cleanup() {
        manager.clearAllContextsForTesting();
        manager.apply(ContextManagerConfig.defaults());
    }

    // ==================== 单例 ====================

    @Test
    @DisplayName("getInstance - 返回单例")
    void singletonInstance() {
        SafeContextManager m1 = SafeContextManager.getInstance();
        SafeContextManager m2 = SafeContextManager.getInstance();
        assertThat(m1).isSameAs(m2);
    }

    // ==================== getCurrentContext ====================

    @Test
    @DisplayName("getCurrentContext - 已关闭上下文返回 null 并清理")
    void getCurrentContextCleansClosed() {
        ManagedThreadContext ctx = ManagedThreadContext.create("test");
        ctx.close(); // 手动关闭

        ManagedThreadContext result = manager.getCurrentContext();
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getCurrentContext - 无注册返回 null")
    void getCurrentContextNoRegistration() {
        assertThat(manager.getCurrentContext()).isNull();
    }

    // ==================== detectAndCleanLeaks ====================

    @Test
    @DisplayName("detectAndCleanLeaks - 无泄漏场景安全执行")
    void detectAndCleanLeaksNoLeaks() {
        assertThatNoException().isThrownBy(() -> manager.detectAndCleanLeaks());
    }

    @Test
    @DisplayName("detectAndCleanLeaks - 存活线程的上下文不被误清理 (Bug E 回归)")
    void detectAndCleanLeaksKeepsAliveContext() {
        // 当前测试线程存活且上下文未超时，detectAndCleanLeaks 不应将其误判为泄漏
        // 显式设置较大超时，避免共享单例被其他测试改写超时阈值造成的偶发失败
        manager.apply(ContextManagerConfig.defaults());
        ManagedThreadContext ctx = ManagedThreadContext.create("alive-ctx");
        try {
            int before = manager.getActiveContextCount();
            assertThat(ctx.isOwnerThreadAlive()).isTrue();

            manager.detectAndCleanLeaks();

            assertThat(ctx.isClosed()).isFalse();
            assertThat(manager.getActiveContextCount()).isEqualTo(before);
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("isOwnerThreadAlive - 当前线程存活返回 true")
    void ownerThreadAliveOnCurrentThread() {
        try (ManagedThreadContext ctx = ManagedThreadContext.create("alive")) {
            assertThat(ctx.isOwnerThreadAlive()).isTrue();
        }
    }

    @Test
    @DisplayName("isOwnerThreadAlive - 创建线程死亡后返回 false")
    void ownerThreadAliveFalseAfterThreadDies() throws Exception {
        AtomicReference<ManagedThreadContext> ctxRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            ctxRef.set(ManagedThreadContext.create("dead-owner"));
            latch.countDown();
        });
        worker.start();
        latch.await(5, TimeUnit.SECONDS);
        worker.join(5000);

        assertThat(worker.isAlive()).isFalse();
        ManagedThreadContext ctx = ctxRef.get();
        try {
            assertThat(ctx.isOwnerThreadAlive()).isFalse();
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("detectAndCleanLeaks - 死线程上下文被清理")
    void detectAndCleanLeaksDeadThread() throws Exception {
        AtomicReference<ManagedThreadContext> ctxRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            ManagedThreadContext ctx = ManagedThreadContext.create("worker");
            ctxRef.set(ctx);
            latch.countDown();
            // 线程结束，不清理上下文
        });
        worker.start();
        latch.await(5, TimeUnit.SECONDS);
        worker.join(5000);

        // 等待线程确实死亡
        assertThat(worker.isAlive()).isFalse();

        // 检测泄漏前的上下文数
        int before = manager.getActiveContextCount();

        // 触发泄漏检测
        manager.detectAndCleanLeaks();

        int after = manager.getActiveContextCount();
        assertThat(after).isLessThanOrEqualTo(before);
    }

    // ==================== LeakListener ====================

    @Test
    @DisplayName("registerLeakListener - 泄漏时通知监听器")
    void leakListenerNotified() throws Exception {
        AtomicBoolean notified = new AtomicBoolean(false);
        SafeContextManager.LeakListener listener = context -> notified.set(true);

        manager.registerLeakListener(listener);
        try {
            // 在工作线程中创建上下文但不清理
            CountDownLatch latch = new CountDownLatch(1);
            Thread worker = new Thread(() -> {
                ManagedThreadContext ctx = ManagedThreadContext.create("leak");
                latch.countDown();
            });
            worker.start();
            latch.await(5, TimeUnit.SECONDS);
            worker.join(5000);

            // 触发泄漏检测
            manager.detectAndCleanLeaks();

            assertThat(notified.get()).isTrue();
        } finally {
            manager.unregisterLeakListener(listener);
        }
    }

    @Test
    @DisplayName("registerLeakListener - null 安全忽略")
    void registerNullListenerSafe() {
        assertThatNoException().isThrownBy(() ->
                manager.registerLeakListener(null));
    }

    @Test
    @DisplayName("unregisterLeakListener - 移除后不再通知")
    void unregisterLeakListener() {
        AtomicBoolean notified = new AtomicBoolean(false);
        SafeContextManager.LeakListener listener = context -> notified.set(true);

        manager.registerLeakListener(listener);
        manager.unregisterLeakListener(listener);

        // 即使有泄漏也不应被通知
        manager.detectAndCleanLeaks();
        assertThat(notified.get()).isFalse();
    }

    // ==================== executeAsync ====================

    @Test
    @DisplayName("executeAsync(Runnable) - 执行成功")
    void executeAsyncRunnable() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        CompletableFuture<Void> future = manager.executeAsync("test", () -> executed.set(true));
        future.get(5, TimeUnit.SECONDS);
        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("executeAsync(Callable) - 返回结果")
    void executeAsyncCallable() throws Exception {
        CompletableFuture<String> future = manager.executeAsync("test", () -> "hello");
        String result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isEqualTo("hello");
    }

    @Test
    @DisplayName("executeAsync - 异常被传播到 Future")
    void executeAsyncException() {
        CompletableFuture<Void> future = manager.executeAsync("fail", () -> {
            throw new RuntimeException("boom");
        });

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("executeAsync - 有活跃上下文时传播快照")
    void executeAsyncWithContextPropagation() throws Exception {
        ManagedThreadContext ctx = ManagedThreadContext.create("parent");

        try {
            AtomicBoolean ran = new AtomicBoolean(false);
            CompletableFuture<Void> future = manager.executeAsync("child", () -> {
                ran.set(true);
            });
            future.get(5, TimeUnit.SECONDS);
            assertThat(ran.get()).isTrue();
        } finally {
            ctx.close();
        }
    }

    @Test
    void executeAsyncCreatesNamedTask() throws Exception {
        ManagedThreadContext parent = ManagedThreadContext.create("named-parent");
        AtomicReference<ManagedThreadContext> childRef = new AtomicReference<>();
        AtomicReference<TaskNode> namedTaskRef = new AtomicReference<>();

        try {
            CompletableFuture<String> future = manager.executeAsync("named-child", () -> {
                ManagedThreadContext child = ManagedThreadContext.current();
                childRef.set(child);
                namedTaskRef.set(child != null ? child.getCurrentTask() : null);
                return "done";
            });

            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("done");
            assertThat(childRef.get()).isNotNull().isNotSameAs(parent);
            assertThat(namedTaskRef.get()).isNotNull();
            assertThat(namedTaskRef.get().getTaskName()).isEqualTo("named-child");
            assertThat(namedTaskRef.get().getStatus().isSuccessful()).isTrue();
            assertThat(childRef.get().isClosed()).isTrue();
        } finally {
            parent.close();
        }
    }

    @Test
    void executeAsyncCompletesNamedTaskBeforeFutureCompletion() throws Exception {
        AtomicReference<ManagedThreadContext> contextRef = new AtomicReference<>();
        AtomicReference<Session> sessionRef = new AtomicReference<>();
        AtomicReference<TaskNode> taskRef = new AtomicReference<>();

        CompletableFuture<String> future = manager.executeAsync("root-async-task", () -> {
            ManagedThreadContext context = ManagedThreadContext.current();
            contextRef.set(context);
            sessionRef.set(context != null ? context.getCurrentSession() : null);
            taskRef.set(context != null ? context.getCurrentTask() : null);
            return "completed";
        });

        assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("completed");
        assertThat(taskRef.get()).isNotNull();
        assertThat(taskRef.get().getTaskName()).isEqualTo("root-async-task");
        assertThat(taskRef.get().getStatus().isSuccessful()).isTrue();
        assertThat(sessionRef.get()).isNotNull();
        assertThat(sessionRef.get().getStatus().isCompleted()).isTrue();
        assertThat(contextRef.get().isClosed()).isTrue();
        assertThat(manager.getActiveContextCount()).isZero();
    }

    @Test
    void concurrentExecuteAsyncCleansEveryContextBeforeFutureCompletion() throws Exception {
        int taskCount = 4;
        CountDownLatch allStarted = new CountDownLatch(taskCount);
        CountDownLatch release = new CountDownLatch(1);
        Set<ManagedThreadContext> observedContexts = ConcurrentHashMap.newKeySet();
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        boolean startedTogether;
        int activeWhileBlocked;
        try {
            for (int index = 0; index < taskCount; index++) {
                int taskIndex = index;
                futures.add(manager.executeAsync("concurrent-" + taskIndex, () -> {
                    ManagedThreadContext context = ManagedThreadContext.current();
                    allStarted.countDown();
                    if (context == null) {
                        throw new AssertionError("executeAsync task has no current Context");
                    }
                    observedContexts.add(context);
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release concurrent tasks");
                    }
                    return taskIndex;
                }));
            }

            startedTogether = allStarted.await(5, TimeUnit.SECONDS);
            activeWhileBlocked = manager.getActiveContextCount();
        } finally {
            release.countDown();
        }
        for (int index = 0; index < futures.size(); index++) {
            assertThat(futures.get(index).get(5, TimeUnit.SECONDS)).isEqualTo(index);
        }

        assertThat(startedTogether).isTrue();
        assertThat(activeWhileBlocked).isEqualTo(taskCount);
        assertThat(observedContexts).hasSize(taskCount).allMatch(ManagedThreadContext::isClosed);
        assertThat(manager.getActiveContextCount()).isZero();
    }

    @Test
    void executeAsyncAttributesFailureToNamedTask() {
        ManagedThreadContext parent = ManagedThreadContext.create("failure-parent");
        RuntimeException businessFailure = new RuntimeException("async business failed");
        AtomicReference<Session> childSessionRef = new AtomicReference<>();
        AtomicReference<TaskNode> namedTaskRef = new AtomicReference<>();

        try {
            CompletableFuture<Void> future = manager.executeAsync("failing-child", () -> {
                ManagedThreadContext child = ManagedThreadContext.current();
                childSessionRef.set(child != null ? child.getCurrentSession() : null);
                namedTaskRef.set(child != null ? child.getCurrentTask() : null);
                throw businessFailure;
            });

            Throwable thrown = catchThrowable(() -> future.get(5, TimeUnit.SECONDS));
            assertThat(thrown).isInstanceOf(java.util.concurrent.ExecutionException.class);
            assertThat(thrown.getCause()).isSameAs(businessFailure);
            assertThat(namedTaskRef.get()).isNotNull();
            assertThat(namedTaskRef.get().getTaskName()).isEqualTo("failing-child");
            assertThat(namedTaskRef.get().getStatus().isFailed()).isTrue();
            assertThat(childSessionRef.get().getStatus().isError()).isTrue();
        } finally {
            parent.close();
        }
    }

    @Test
    void executeAsyncLinksChildSessionWithoutSharingTree() throws Exception {
        ManagedThreadContext parent = ManagedThreadContext.create("linked-parent");
        Session parentSession = parent.getCurrentSession();
        TaskNode parentTask = parent.startTask("parent-step");
        String expectedTaskPath = parentTask.getTaskPath();
        AtomicReference<Session> childSessionRef = new AtomicReference<>();
        AtomicReference<TaskNode> namedTaskRef = new AtomicReference<>();
        AtomicReference<String> parentContextId = new AtomicReference<>();
        AtomicReference<String> parentSessionId = new AtomicReference<>();
        AtomicReference<String> parentTaskPath = new AtomicReference<>();

        try {
            CompletableFuture<Void> future = manager.executeAsync("linked-child", () -> {
                ManagedThreadContext child = ManagedThreadContext.current();
                childSessionRef.set(child.getCurrentSession());
                namedTaskRef.set(child.getCurrentTask());
                parentContextId.set(child.getAttribute("parent.contextId"));
                parentSessionId.set(child.getAttribute("parent.sessionId"));
                parentTaskPath.set(child.getAttribute("parent.taskPath"));
            });

            future.get(5, TimeUnit.SECONDS);
            Session childSession = childSessionRef.get();
            TaskNode namedTask = namedTaskRef.get();
            assertThat(childSession).isNotNull().isNotSameAs(parentSession);
            assertThat(childSession.getRootTask()).isNotSameAs(parentSession.getRootTask());
            assertThat(namedTask.getParent()).isSameAs(childSession.getRootTask());
            assertThat(parentContextId.get()).isEqualTo(parent.getContextId());
            assertThat(parentSessionId.get()).isEqualTo(parentSession.getSessionId());
            assertThat(parentTaskPath.get()).isEqualTo(expectedTaskPath);
            assertThat(parentTask.getChildren()).doesNotContain(namedTask);
            assertThat(namedTask.getStatus().isSuccessful()).isTrue();
            assertThat(childSession.getStatus().isCompleted()).isTrue();
        } finally {
            parent.close();
        }
    }

    @Test
    void executeAsyncRestoresPollutedWorkerAfterFailure() throws Exception {
        ThreadPoolExecutor executor = asyncExecutorForTesting();
        int workerCount = executor.getCorePoolSize();
        runOnEveryAsyncWorker(executor, () ->
                ManagedThreadContext.create("polluted-" + Thread.currentThread().getName()));
        assertThat(manager.getActiveContextCount()).isEqualTo(workerCount);
        RuntimeException businessFailure = new RuntimeException("polluted worker failure");

        try {
            CompletableFuture<Void> future = manager.executeAsync("polluted-failure", () -> {
                throw businessFailure;
            });
            Throwable thrown = catchThrowable(() -> future.get(5, TimeUnit.SECONDS));

            assertThat(thrown).isInstanceOf(java.util.concurrent.ExecutionException.class);
            assertThat(thrown.getCause()).isSameAs(businessFailure);
            assertThat(manager.getActiveContextCount()).isEqualTo(workerCount);
        } finally {
            runOnEveryAsyncWorker(executor, () -> {
                ManagedThreadContext current = manager.getCurrentContext();
                if (current != null) {
                    current.close();
                }
            });
        }
        assertThat(manager.getActiveContextCount()).isZero();
    }

    @Test
    void executeAsyncPreservesBusinessFailureWhenFailureSignalingFails() {
        RuntimeException businessFailure = new RuntimeException("async business failure");
        RuntimeException taskFailure = new RuntimeException("named task cleanup failure");
        RuntimeException scopeFailure = new RuntimeException("scope cleanup failure");
        AtomicReference<ManagedThreadContext> contextRef = new AtomicReference<>();

        CompletableFuture<Void> future = manager.executeAsync("failure-order", () -> {
            ManagedThreadContext context = ManagedThreadContext.current();
            contextRef.set(context);
            installAsyncFailureSequence(context, taskFailure, scopeFailure);
            throw businessFailure;
        });

        Throwable thrown = catchThrowable(() -> future.get(5, TimeUnit.SECONDS));
        assertThat(thrown).isInstanceOf(java.util.concurrent.ExecutionException.class);
        assertThat(thrown.getCause()).isSameAs(businessFailure);
        assertThat(businessFailure.getSuppressed()).containsExactly(taskFailure, scopeFailure);
        assertThat(contextRef.get().isClosed()).isTrue();
        assertThat(manager.getActiveContextCount()).isZero();
    }

    // ==================== wrapRunnable / wrapCallable ====================

    @Test
    @DisplayName("wrapRunnable - 无上下文时正常执行")
    void wrapRunnableNoContext() {
        AtomicBoolean executed = new AtomicBoolean(false);
        Runnable wrapped = manager.wrapRunnable(() -> executed.set(true));
        wrapped.run();
        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("wrapCallable - 无上下文时正常执行")
    void wrapCallableNoContext() throws Exception {
        java.util.concurrent.Callable<String> wrapped =
                manager.wrapCallable(() -> "result");
        assertThat(wrapped.call()).isEqualTo("result");
    }

    // ==================== getActiveContextCount ====================

    @Test
    @DisplayName("getActiveContextCount - 计数正确")
    void activeContextCount() {
        int before = manager.getActiveContextCount();

        ManagedThreadContext ctx = ManagedThreadContext.create("test");
        assertThat(manager.getActiveContextCount()).isEqualTo(before + 1);

        ctx.close();
        assertThat(manager.getActiveContextCount()).isEqualTo(before);
    }

    private ThreadPoolExecutor asyncExecutorForTesting() {
        try {
            Method method = SafeContextManager.class.getDeclaredMethod("getAsyncExecutor");
            method.setAccessible(true);
            return (ThreadPoolExecutor) method.invoke(manager);
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new AssertionError("Unable to access async executor", reflectionFailure);
        }
    }

    private static void runOnEveryAsyncWorker(
            ThreadPoolExecutor executor, Runnable action) throws Exception {
        int workerCount = executor.getCorePoolSize();
        executor.prestartAllCoreThreads();
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workerCount);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int index = 0; index < workerCount; index++) {
            executor.execute(() -> {
                try {
                    action.run();
                } catch (Throwable thrown) {
                    failure.compareAndSet(null, thrown);
                } finally {
                    ready.countDown();
                }
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        failure.compareAndSet(null, new AssertionError("Worker release timed out"));
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failure.compareAndSet(null, interrupted);
                } finally {
                    done.countDown();
                }
            });
        }

        boolean allReady = ready.await(5, TimeUnit.SECONDS);
        release.countDown();
        assertThat(allReady).isTrue();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isNull();
    }

    private static void installAsyncFailureSequence(
            ManagedThreadContext context,
            RuntimeException taskFailure,
            RuntimeException scopeFailure) {
        if (context == null || context.getCurrentTask() == null) {
            throw new AssertionError("Named async task is not active");
        }
        try {
            AtomicInteger messageWrites = new AtomicInteger();
            Field messages = TaskNode.class.getDeclaredField("messages");
            messages.setAccessible(true);
            messages.set(context.getCurrentTask(), new AbstractList<Message>() {
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
                    if (messageWrites.getAndIncrement() == 0) {
                        throw taskFailure;
                    }
                    return true;
                }
            });

            Field terminalProbe = ManagedThreadContext.class.getDeclaredField("terminalProbe");
            terminalProbe.setAccessible(true);
            terminalProbe.set(context, (ContextTerminalProbe) (ignoredContext, ignoredSession) -> {
                throw scopeFailure;
            });
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new AssertionError("Unable to install async failure sequence", reflectionFailure);
        }
    }

}
