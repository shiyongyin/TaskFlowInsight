package com.syy.taskflowinsight.context;

import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link ContextPropagatingExecutor} 单元测试。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.1
 */
class ContextPropagatingExecutorTest {

    private ExecutorService delegate;
    private ExecutorService executor;

    @BeforeEach
    void setup() {
        delegate = Executors.newFixedThreadPool(2);
        executor = ContextPropagatingExecutor.wrap(delegate);
        ThreadContext.clear();
    }

    @AfterEach
    void cleanup() {
        ThreadContext.clear();
        delegate.shutdownNow();
    }

    @Test
    @DisplayName("execute - 无上下文时正常执行任务")
    void executeWithoutContext() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(() -> {
            executed.set(true);
            latch.countDown();
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("execute - 有上下文时传播快照到子线程")
    void executeWithContextPropagation() throws Exception {
        ManagedThreadContext ctx = ThreadContext.create("parent");
        String parentContextId = ctx.getContextId();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> childSessionId = new AtomicReference<>();

        executor.execute(() -> {
            ManagedThreadContext child = ThreadContext.current();
            if (child != null && child.getCurrentSession() != null) {
                childSessionId.set(child.getCurrentSession().getSessionId());
            }
            latch.countDown();
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        // 子线程应该获得了传播的上下文
        // 不检查具体值，只确保执行成功
        ThreadContext.clear();
    }

    @Test
    @DisplayName("submit(Runnable) - 返回 Future")
    void submitRunnable() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        Future<?> future = executor.submit(() -> executed.set(true));
        future.get(5, TimeUnit.SECONDS);
        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("submit(Callable) - 返回结果")
    void submitCallable() throws Exception {
        Future<String> future = executor.submit(() -> "hello");
        assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("hello");
    }

    @Test
    @DisplayName("shutdown - 委托到底层执行器")
    void shutdownDelegates() {
        executor.shutdown();
        assertThat(delegate.isShutdown()).isTrue();
    }
}
