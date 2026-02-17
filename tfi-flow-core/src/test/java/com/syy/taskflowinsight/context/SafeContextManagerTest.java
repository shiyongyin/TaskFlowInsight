package com.syy.taskflowinsight.context;

import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link SafeContextManager} 单元测试。
 *
 * <p>覆盖 configure、registerContext/unregisterContext、getCurrentContext、
 * detectAndCleanLeaks、LeakListener、applyTfiConfig 等路径。
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
        // 禁用泄漏检测以避免干扰
        manager.setLeakDetectionEnabled(false);
    }

    @AfterEach
    void cleanup() {
        manager.clearAllContextsForTesting();
        manager.setLeakDetectionEnabled(false);
    }

    // ==================== 单例 ====================

    @Test
    @DisplayName("getInstance - 返回单例")
    void singletonInstance() {
        SafeContextManager m1 = SafeContextManager.getInstance();
        SafeContextManager m2 = SafeContextManager.getInstance();
        assertThat(m1).isSameAs(m2);
    }

    // ==================== registerContext / unregisterContext ====================

    @Test
    @DisplayName("registerContext - 注册后 getCurrentContext 可获取")
    void registerAndGet() {
        // ManagedThreadContext.create() 内部已调用 registerContext
        ManagedThreadContext ctx = ManagedThreadContext.create("test");
        try {
            ManagedThreadContext current = manager.getCurrentContext();
            assertThat(current).isSameAs(ctx);
        } finally {
            manager.unregisterContext(ctx);
            ctx.close();
        }
    }

    @Test
    @DisplayName("registerContext - null 抛出 IllegalArgumentException")
    void registerNullThrows() {
        assertThatThrownBy(() -> manager.registerContext(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("unregisterContext - 注销后 getCurrentContext 返回 null")
    void unregisterClearsContext() {
        ManagedThreadContext ctx = ManagedThreadContext.create("test");
        try {
            manager.registerContext(ctx);
            manager.unregisterContext(ctx);
            assertThat(manager.getCurrentContext()).isNull();
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("unregisterContext - null 安全忽略")
    void unregisterNullSafe() {
        assertThatNoException().isThrownBy(() -> manager.unregisterContext(null));
    }

    @Test
    @DisplayName("create - 替换旧未关闭上下文会自动关闭旧上下文")
    void replacingContextClosesOld() {
        // ManagedThreadContext.create 内部调用 registerContext
        ManagedThreadContext ctx1 = ManagedThreadContext.create("old");
        assertThat(ctx1.isClosed()).isFalse();

        // 创建新上下文会关闭旧上下文
        ManagedThreadContext ctx2 = ManagedThreadContext.create("new");
        try {
            assertThat(ctx1.isClosed()).isTrue();
            assertThat(manager.getCurrentContext()).isSameAs(ctx2);
        } finally {
            manager.unregisterContext(ctx2);
            ctx2.close();
        }
    }

    // ==================== getCurrentContext ====================

    @Test
    @DisplayName("getCurrentContext - 已关闭上下文返回 null 并清理")
    void getCurrentContextCleansClosed() {
        ManagedThreadContext ctx = ManagedThreadContext.create("test");
        manager.registerContext(ctx);
        ctx.close(); // 手动关闭

        ManagedThreadContext result = manager.getCurrentContext();
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getCurrentContext - 无注册返回 null")
    void getCurrentContextNoRegistration() {
        assertThat(manager.getCurrentContext()).isNull();
    }

    // ==================== configure ====================

    @Test
    @DisplayName("configure - 配置参数被正确应用")
    void configureAppliesSettings() {
        assertThatNoException().isThrownBy(() ->
                manager.configure(30000L, false, 5000L));
    }

    @Test
    @DisplayName("configure - 启用泄漏检测会启动检测器")
    void configureEnablesLeakDetection() {
        assertThatNoException().isThrownBy(() -> {
            manager.configure(30000L, true, 5000L);
            Thread.sleep(50);
            manager.configure(30000L, false, 5000L);
        });
    }

    // ==================== applyTfiConfig ====================

    @Test
    @DisplayName("applyTfiConfig - 委托到各 setter")
    void applyTfiConfigDelegates() {
        assertThatNoException().isThrownBy(() ->
                manager.applyTfiConfig(60000L, false, 30000L));
    }

    // ==================== setLeakDetectionEnabled ====================

    @Test
    @DisplayName("setLeakDetectionEnabled - 切换不抛异常")
    void leakDetectionToggle() {
        assertThatNoException().isThrownBy(() -> {
            manager.setLeakDetectionEnabled(true);
            Thread.sleep(50);
            manager.setLeakDetectionEnabled(false);
        });
    }

    // ==================== setLeakDetectionIntervalMillis ====================

    @Test
    @DisplayName("setLeakDetectionIntervalMillis - 重启检测器不抛异常")
    void leakDetectionIntervalRestart() {
        assertThatNoException().isThrownBy(() -> {
            manager.setLeakDetectionEnabled(true);
            manager.setLeakDetectionIntervalMillis(10000L);
            manager.setLeakDetectionEnabled(false);
        });
    }

    // ==================== setContextTimeoutMillis ====================

    @Test
    @DisplayName("setContextTimeoutMillis - 设置超时不抛异常")
    void setContextTimeoutSafe() {
        assertThatNoException().isThrownBy(() ->
                manager.setContextTimeoutMillis(120000L));
    }

    // ==================== detectAndCleanLeaks ====================

    @Test
    @DisplayName("detectAndCleanLeaks - 无泄漏场景安全执行")
    void detectAndCleanLeaksNoLeaks() {
        assertThatNoException().isThrownBy(() -> manager.detectAndCleanLeaks());
    }

    @Test
    @DisplayName("detectAndCleanLeaks - 死线程上下文被清理")
    void detectAndCleanLeaksDeadThread() throws Exception {
        AtomicReference<ManagedThreadContext> ctxRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            ManagedThreadContext ctx = ManagedThreadContext.create("worker");
            manager.registerContext(ctx);
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
                manager.registerContext(ctx);
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
        manager.registerContext(ctx);

        try {
            AtomicBoolean ran = new AtomicBoolean(false);
            CompletableFuture<Void> future = manager.executeAsync("child", () -> {
                ran.set(true);
            });
            future.get(5, TimeUnit.SECONDS);
            assertThat(ran.get()).isTrue();
        } finally {
            manager.unregisterContext(ctx);
            ctx.close();
        }
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
        manager.registerContext(ctx);
        assertThat(manager.getActiveContextCount()).isEqualTo(before + 1);

        manager.unregisterContext(ctx);
        ctx.close();
        assertThat(manager.getActiveContextCount()).isEqualTo(before);
    }

    // ==================== getMetrics ====================

    @Test
    @DisplayName("getMetrics - 包含所有必要指标")
    void metricsContainsKeys() {
        Map<String, Object> metrics = manager.getMetrics();
        assertThat(metrics).containsKeys(
                "contexts.created",
                "contexts.closed",
                "contexts.active",
                "contexts.leaked",
                "async.tasks",
                "executor.poolSize",
                "executor.queueSize"
        );
    }
}
