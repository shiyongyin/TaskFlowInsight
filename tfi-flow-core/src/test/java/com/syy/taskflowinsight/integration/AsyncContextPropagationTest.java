package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.context.ContextPropagatingExecutor;
import com.syy.taskflowinsight.context.ContextSnapshot;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.context.TFIAwareExecutor;
import com.syy.taskflowinsight.context.ThreadContext;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * 异步上下文传播集成测试
 *
 * <p>覆盖 {@link TFIAwareExecutor}、{@link ContextPropagatingExecutor}、
 * {@link ContextSnapshot} 的跨线程上下文传播能力。
 *
 * <p>测试重点：
 * <ul>
 *   <li>TFIAwareExecutor：通过 SafeContextManager 包装实现上下文传播</li>
 *   <li>ContextSnapshot：不可变快照的创建、恢复、属性验证</li>
 *   <li>ContextPropagatingExecutor：通过 ThreadContext 包装的上下文传播</li>
 *   <li>跨线程会话可见性与清理</li>
 * </ul>
 *
 * @author tfi-flow-core Test Team
 * @since 4.0.0
 */
class AsyncContextPropagationTest {

    @BeforeEach
    void setup() {
        ProviderRegistry.clearAll();
        TfiFlow.enable();
        forceCleanContext();
    }

    @AfterEach
    void cleanup() {
        forceCleanContext();
        ProviderRegistry.clearAll();
        TfiFlow.enable();
    }

    /**
     * 强制清理底层上下文状态，避免跨测试泄漏
     */
    private void forceCleanContext() {
        try {
            java.lang.reflect.Field field = TfiFlow.class.getDeclaredField("cachedFlowProvider");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception ignored) {
        }
        try {
            ManagedThreadContext ctx = ManagedThreadContext.current();
            if (ctx != null && !ctx.isClosed()) {
                ctx.close();
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== ContextSnapshot 测试 ====================

    @Test
    @DisplayName("ContextSnapshot - 创建快照包含正确的会话信息")
    void snapshotContainsSessionInfo() {
        ManagedThreadContext context = ManagedThreadContext.create("快照测试");
        try {
            ContextSnapshot snapshot = context.createSnapshot();

            assertThat(snapshot).isNotNull();
            assertThat(snapshot.getContextId()).isEqualTo(context.getContextId());
            assertThat(snapshot.hasSession()).isTrue();
            assertThat(snapshot.getSessionId()).isNotNull();
            assertThat(snapshot.getTimestamp()).isGreaterThan(0);
        } finally {
            context.close();
        }
    }

    @Test
    @DisplayName("ContextSnapshot - 不可变性验证")
    void snapshotImmutability() {
        ManagedThreadContext context = ManagedThreadContext.create("不可变测试");
        try {
            ContextSnapshot s1 = context.createSnapshot();
            ContextSnapshot s2 = context.createSnapshot();

            // 两个快照的 contextId 相同（来自同一个上下文）
            assertThat(s1.getContextId()).isEqualTo(s2.getContextId());
            assertThat(s1.getSessionId()).isEqualTo(s2.getSessionId());
            // 时间戳可能不同
            assertThat(s1.getTimestamp()).isLessThanOrEqualTo(s2.getTimestamp());
        } finally {
            context.close();
        }
    }

    @Test
    @DisplayName("ContextSnapshot - 快照年龄计算")
    void snapshotAgeCalculation() throws InterruptedException {
        ManagedThreadContext context = ManagedThreadContext.create("年龄测试");
        try {
            ContextSnapshot snapshot = context.createSnapshot();

            Thread.sleep(50);

            assertThat(snapshot.getAgeMillis()).isGreaterThanOrEqualTo(40);
            assertThat(snapshot.getAgeNanos()).isGreaterThan(0);
        } finally {
            context.close();
        }
    }

    @Test
    @DisplayName("ContextSnapshot - 无会话时 hasSession 返回 false")
    void snapshotWithoutSession() {
        // 直接构造一个无会话的快照
        ContextSnapshot snapshot = new ContextSnapshot("test-ctx", null, null, System.nanoTime());

        assertThat(snapshot.hasSession()).isFalse();
        assertThat(snapshot.hasTask()).isFalse();
        assertThat(snapshot.getSessionId()).isNull();
    }

    @Test
    @DisplayName("ContextSnapshot - equals 和 hashCode")
    void snapshotEquality() {
        long ts = System.nanoTime();
        ContextSnapshot s1 = new ContextSnapshot("ctx-1", "session-1", "/root/task1", ts);
        ContextSnapshot s2 = new ContextSnapshot("ctx-1", "session-1", "/root/task1", ts);
        ContextSnapshot s3 = new ContextSnapshot("ctx-2", "session-1", "/root/task1", ts);

        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
        assertThat(s1).isNotEqualTo(s3);
    }

    @Test
    @DisplayName("ContextSnapshot - restore 在新线程恢复上下文")
    void snapshotRestoreInNewThread() throws Exception {
        ManagedThreadContext parent = ManagedThreadContext.create("父上下文");
        ContextSnapshot snapshot;
        try {
            snapshot = parent.createSnapshot();
        } finally {
            parent.close();
        }

        AtomicReference<String> childParentContextId = new AtomicReference<>();
        AtomicReference<Boolean> childHasSession = new AtomicReference<>();

        Thread child = new Thread(() -> {
            ManagedThreadContext restored = snapshot.restore();
            try {
                childParentContextId.set(restored.getAttribute("parent.contextId"));
                childHasSession.set(restored.getCurrentSession() != null);
            } finally {
                restored.close();
            }
        });
        child.start();
        child.join(5000);

        assertThat(childParentContextId.get()).isEqualTo(parent.getContextId());
        assertThat(childHasSession.get()).isTrue();
    }

    // ==================== TFIAwareExecutor 测试 ====================

    @Test
    @DisplayName("TFIAwareExecutor - submit(Runnable) 正确传播上下文")
    void tfiAwareExecutorSubmitRunnable() throws Exception {
        TFIAwareExecutor executor = TFIAwareExecutor.newFixedThreadPool(2);
        try {
            TfiFlow.startSession("异步任务");
            try (TaskContext stage = TfiFlow.stage("父任务")) {
                stage.message("设置上下文");

                AtomicReference<Boolean> hasContextInChild = new AtomicReference<>(false);

                Future<?> future = executor.submit(() -> {
                    // 子线程中应有恢复的上下文
                    ManagedThreadContext childCtx = ManagedThreadContext.current();
                    hasContextInChild.set(childCtx != null && !childCtx.isClosed());
                });

                future.get(5, TimeUnit.SECONDS);
                assertThat(hasContextInChild.get()).isTrue();
            }
            TfiFlow.endSession();
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("TFIAwareExecutor - submit(Callable) 返回正确结果")
    void tfiAwareExecutorSubmitCallable() throws Exception {
        TFIAwareExecutor executor = TFIAwareExecutor.newFixedThreadPool(2);
        try {
            TfiFlow.startSession("Callable测试");

            Future<String> future = executor.submit(() -> {
                ManagedThreadContext ctx = ManagedThreadContext.current();
                return ctx != null ? "has-context" : "no-context";
            });

            String result = future.get(5, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("has-context");

            TfiFlow.endSession();
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("TFIAwareExecutor - execute() 上下文传播")
    void tfiAwareExecutorExecute() throws Exception {
        TFIAwareExecutor executor = TFIAwareExecutor.newFixedThreadPool(1);
        try {
            TfiFlow.startSession("execute测试");

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Boolean> hasContext = new AtomicReference<>(false);

            executor.execute(() -> {
                ManagedThreadContext ctx = ManagedThreadContext.current();
                hasContext.set(ctx != null && !ctx.isClosed());
                latch.countDown();
            });

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(hasContext.get()).isTrue();

            TfiFlow.endSession();
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("TFIAwareExecutor - 无上下文时任务正常执行")
    void tfiAwareExecutorNoContext() throws Exception {
        TFIAwareExecutor executor = TFIAwareExecutor.newFixedThreadPool(1);
        try {
            // 不创建会话，直接提交任务
            AtomicReference<Boolean> executed = new AtomicReference<>(false);
            Future<?> future = executor.submit(() -> executed.set(true));
            future.get(5, TimeUnit.SECONDS);

            assertThat(executed.get()).isTrue();
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("TFIAwareExecutor - newThreadPool 工厂方法")
    void tfiAwareExecutorFactory() throws Exception {
        TFIAwareExecutor executor = TFIAwareExecutor.newThreadPool(2, 4, 60, TimeUnit.SECONDS);
        try {
            assertThat(executor).isNotNull();
            assertThat(executor.isShutdown()).isFalse();

            Future<Integer> future = executor.submit(() -> 42);
            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(42);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.isTerminated()).isTrue();
        }
    }

    @Test
    @DisplayName("TFIAwareExecutor - 子线程上下文自动清理")
    void tfiAwareExecutorChildContextCleanup() throws Exception {
        TFIAwareExecutor executor = TFIAwareExecutor.newFixedThreadPool(1);
        try {
            TfiFlow.startSession("清理测试");

            // 第一个任务
            Future<?> f1 = executor.submit(() -> {
                ManagedThreadContext ctx = ManagedThreadContext.current();
                assertThat(ctx).isNotNull();
                // ContextAwareRunnable.run() 在 finally 中会 close 恢复的上下文
            });
            f1.get(5, TimeUnit.SECONDS);

            // 第二个任务（复用同一线程）也应能获得独立上下文
            Future<?> f2 = executor.submit(() -> {
                ManagedThreadContext ctx = ManagedThreadContext.current();
                assertThat(ctx).isNotNull();
            });
            f2.get(5, TimeUnit.SECONDS);

            TfiFlow.endSession();
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ==================== ContextPropagatingExecutor 测试 ====================

    @Test
    @DisplayName("ContextPropagatingExecutor - wrap 工厂方法")
    void contextPropagatingWrap() {
        ExecutorService plain = Executors.newFixedThreadPool(1);
        try {
            ExecutorService wrapped = ContextPropagatingExecutor.wrap(plain);
            assertThat(wrapped).isNotNull();
            assertThat(wrapped).isNotSameAs(plain);

            // 重复 wrap 返回相同实例
            ExecutorService doubleWrapped = ContextPropagatingExecutor.wrap(wrapped);
            assertThat(doubleWrapped).isSameAs(wrapped);
        } finally {
            plain.shutdown();
        }
    }

    @Test
    @DisplayName("ContextPropagatingExecutor - wrap(null) 抛出异常")
    void contextPropagatingWrapNull() {
        assertThatThrownBy(() -> ContextPropagatingExecutor.wrap(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ContextPropagatingExecutor - 基于 ThreadContext 的上下文传播")
    void contextPropagatingWithThreadContext() throws Exception {
        ExecutorService plain = Executors.newFixedThreadPool(1);
        ExecutorService wrapped = ContextPropagatingExecutor.wrap(plain);
        try {
            // 使用 ThreadContext 创建上下文（而非 TfiFlow）
            ManagedThreadContext parentCtx = ThreadContext.create("CPE测试");
            try {
                AtomicReference<String> childParentId = new AtomicReference<>();

                Future<?> future = wrapped.submit(() -> {
                    ManagedThreadContext childCtx = ManagedThreadContext.current();
                    if (childCtx != null) {
                        Object parentIdAttr = childCtx.getAttribute("parent.contextId");
                        childParentId.set(parentIdAttr != null ? parentIdAttr.toString() : null);
                    }
                });

                future.get(5, TimeUnit.SECONDS);
                // ContextPropagatingExecutor 使用 ThreadContext.current() 获取上下文
                // ThreadContext.current() 使用独立的 CONTEXT_HOLDER ThreadLocal
                assertThat(childParentId.get()).isEqualTo(parentCtx.getContextId());
            } finally {
                ThreadContext.clear();
            }
        } finally {
            wrapped.shutdown();
            plain.shutdown();
            plain.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("ContextPropagatingExecutor - 无上下文时任务正常执行")
    void contextPropagatingNoContext() throws Exception {
        ExecutorService plain = Executors.newFixedThreadPool(1);
        ExecutorService wrapped = ContextPropagatingExecutor.wrap(plain);
        try {
            AtomicReference<Boolean> executed = new AtomicReference<>(false);
            Future<?> future = wrapped.submit(() -> executed.set(true));
            future.get(5, TimeUnit.SECONDS);

            assertThat(executed.get()).isTrue();
        } finally {
            wrapped.shutdown();
            plain.shutdown();
            plain.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("ContextPropagatingExecutor - submit(Callable) 返回正确结果")
    void contextPropagatingCallable() throws Exception {
        ExecutorService plain = Executors.newFixedThreadPool(1);
        ExecutorService wrapped = ContextPropagatingExecutor.wrap(plain);
        try {
            Future<Integer> future = wrapped.submit(() -> 100 + 200);
            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(300);
        } finally {
            wrapped.shutdown();
            plain.shutdown();
            plain.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("ContextPropagatingExecutor - 委托生命周期方法")
    void contextPropagatingLifecycle() throws Exception {
        ExecutorService plain = Executors.newFixedThreadPool(1);
        ExecutorService wrapped = ContextPropagatingExecutor.wrap(plain);

        assertThat(wrapped.isShutdown()).isFalse();
        assertThat(wrapped.isTerminated()).isFalse();

        wrapped.shutdown();
        assertThat(wrapped.isShutdown()).isTrue();
        wrapped.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(wrapped.isTerminated()).isTrue();
    }

    // ==================== SafeContextManager 异步执行测试 ====================

    @Test
    @DisplayName("SafeContextManager - executeAsync 传播上下文")
    void safeContextManagerExecuteAsync() throws Exception {
        SafeContextManager manager = SafeContextManager.getInstance();

        ManagedThreadContext parent = ManagedThreadContext.create("异步传播");
        try {
            CompletableFuture<Boolean> future = manager.executeAsync("child-task", () -> {
                ManagedThreadContext childCtx = ManagedThreadContext.current();
                return childCtx != null && childCtx.getCurrentSession() != null;
            });

            Boolean result = future.get(5, TimeUnit.SECONDS);
            assertThat(result).isTrue();
        } finally {
            parent.close();
        }
    }

    @Test
    @DisplayName("SafeContextManager - wrapRunnable 包装正确传播")
    void safeContextManagerWrapRunnable() throws Exception {
        SafeContextManager manager = SafeContextManager.getInstance();

        ManagedThreadContext parent = ManagedThreadContext.create("wrap测试");
        try {
            Runnable wrapped = manager.wrapRunnable(() -> {
                ManagedThreadContext ctx = ManagedThreadContext.current();
                assertThat(ctx).isNotNull();
            });

            // 在新线程中执行
            Thread child = new Thread(wrapped);
            child.start();
            child.join(5000);
        } finally {
            parent.close();
        }
    }

    @Test
    @DisplayName("SafeContextManager - wrapCallable 包装正确传播")
    void safeContextManagerWrapCallable() throws Exception {
        SafeContextManager manager = SafeContextManager.getInstance();
        ExecutorService pool = Executors.newSingleThreadExecutor();

        ManagedThreadContext parent = ManagedThreadContext.create("callable-wrap");
        try {
            Callable<String> wrapped = manager.wrapCallable(() -> {
                ManagedThreadContext ctx = ManagedThreadContext.current();
                return ctx != null ? "propagated" : "missing";
            });

            Future<String> future = pool.submit(wrapped);
            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("propagated");
        } finally {
            parent.close();
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("SafeContextManager - 监控指标正确更新")
    void safeContextManagerMetrics() {
        SafeContextManager manager = SafeContextManager.getInstance();

        var metricsBefore = manager.getMetrics();
        long createdBefore = (long) metricsBefore.get("contexts.created");

        ManagedThreadContext ctx = ManagedThreadContext.create("指标测试");
        try {
            var metricsAfter = manager.getMetrics();
            long createdAfter = (long) metricsAfter.get("contexts.created");
            assertThat(createdAfter).isGreaterThan(createdBefore);
            assertThat((int) metricsAfter.get("contexts.active")).isGreaterThanOrEqualTo(1);
        } finally {
            ctx.close();
        }
    }
}
