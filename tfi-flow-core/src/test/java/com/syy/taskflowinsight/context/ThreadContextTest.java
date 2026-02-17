package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link ThreadContext} 单元测试。
 *
 * <p>覆盖 create/current/clear/propagate/execute/run 以及
 * 统计 API 和 {@link ThreadContext.ContextStatistics}。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.1
 */
class ThreadContextTest {

    @BeforeEach
    void setup() {
        ThreadContext.clear();
    }

    @AfterEach
    void cleanup() {
        ThreadContext.clear();
    }

    // ==================== create / current ====================

    @Test
    @DisplayName("create - 创建并激活上下文")
    void createActivatesContext() {
        ManagedThreadContext ctx = ThreadContext.create("test");
        assertThat(ctx).isNotNull();
        assertThat(ctx.isClosed()).isFalse();

        ManagedThreadContext current = ThreadContext.current();
        assertThat(current).isSameAs(ctx);

        ThreadContext.clear();
    }

    @Test
    @DisplayName("create - 替换现有上下文")
    void createReplacesExisting() {
        ManagedThreadContext old = ThreadContext.create("old");
        ManagedThreadContext newCtx = ThreadContext.create("new");

        assertThat(old.isClosed()).isTrue();
        assertThat(ThreadContext.current()).isSameAs(newCtx);

        ThreadContext.clear();
    }

    @Test
    @DisplayName("current - 无上下文返回 null")
    void currentNoContext() {
        assertThat(ThreadContext.current()).isNull();
    }

    @Test
    @DisplayName("current - 已关闭的上下文返回 null")
    void currentClosedContextReturnsNull() {
        ManagedThreadContext ctx = ThreadContext.create("test");
        ctx.close();

        assertThat(ThreadContext.current()).isNull();
    }

    // ==================== currentSession / currentTask ====================

    @Test
    @DisplayName("currentSession - 有上下文时返回会话")
    void currentSessionWithContext() {
        ThreadContext.create("session-test");
        Session session = ThreadContext.currentSession();
        assertThat(session).isNotNull();
        ThreadContext.clear();
    }

    @Test
    @DisplayName("currentSession - 无上下文返回 null")
    void currentSessionNoContext() {
        assertThat(ThreadContext.currentSession()).isNull();
    }

    @Test
    @DisplayName("currentTask - 有上下文时返回当前任务")
    void currentTaskWithContext() {
        ThreadContext.create("task-test");
        TaskNode task = ThreadContext.currentTask();
        assertThat(task).isNotNull();
        ThreadContext.clear();
    }

    @Test
    @DisplayName("currentTask - 无上下文返回 null")
    void currentTaskNoContext() {
        assertThat(ThreadContext.currentTask()).isNull();
    }

    // ==================== clear ====================

    @Test
    @DisplayName("clear - 清理上下文后 current 为 null")
    void clearCleansContext() {
        ThreadContext.create("test");
        ThreadContext.clear();
        assertThat(ThreadContext.current()).isNull();
    }

    @Test
    @DisplayName("clear - 无上下文时安全调用")
    void clearNoContextSafe() {
        assertThatNoException().isThrownBy(ThreadContext::clear);
    }

    @Test
    @DisplayName("clear - 多次调用安全")
    void clearMultipleTimesSafe() {
        ThreadContext.create("test");
        ThreadContext.clear();
        assertThatNoException().isThrownBy(ThreadContext::clear);
    }

    // ==================== propagate ====================

    @Test
    @DisplayName("propagate - null 快照返回 null")
    void propagateNullSnapshot() {
        assertThat(ThreadContext.propagate(null)).isNull();
    }

    @Test
    @DisplayName("propagate - 跨线程传播上下文")
    void propagateCrossThread() throws Exception {
        ManagedThreadContext original = ThreadContext.create("source");
        ContextSnapshot snapshot = original.createSnapshot();

        AtomicReference<ManagedThreadContext> propagated = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            ManagedThreadContext restored = ThreadContext.propagate(snapshot);
            propagated.set(restored);
            latch.countDown();
            ThreadContext.clear();
        });
        worker.start();
        latch.await(5, TimeUnit.SECONDS);
        worker.join(5000);

        assertThat(propagated.get()).isNotNull();
        assertThat(propagated.get()).isNotSameAs(original);

        ThreadContext.clear();
    }

    // ==================== execute ====================

    @Test
    @DisplayName("execute - 执行带返回值的任务")
    void executeWithReturn() throws Exception {
        String result = ThreadContext.execute("compute", ctx -> {
            assertThat(ctx).isNotNull();
            return "result";
        });
        assertThat(result).isEqualTo("result");
    }

    @Test
    @DisplayName("execute - 任务完成后上下文自动关闭")
    void executeAutoCloses() throws Exception {
        AtomicReference<ManagedThreadContext> captured = new AtomicReference<>();
        ThreadContext.execute("test", ctx -> {
            captured.set(ctx);
            return null;
        });
        assertThat(captured.get().isClosed()).isTrue();
    }

    @Test
    @DisplayName("execute - 异常传播")
    void executeExceptionPropagation() {
        assertThatThrownBy(() ->
                ThreadContext.execute("fail", ctx -> {
                    throw new RuntimeException("boom");
                })
        ).isInstanceOf(RuntimeException.class).hasMessage("boom");
    }

    // ==================== run ====================

    @Test
    @DisplayName("run - 执行无返回值的任务")
    void runTask() throws Exception {
        boolean[] executed = {false};
        ThreadContext.run("task", ctx -> {
            assertThat(ctx).isNotNull();
            executed[0] = true;
        });
        assertThat(executed[0]).isTrue();
    }

    @Test
    @DisplayName("run - 异常传播")
    void runExceptionPropagation() {
        assertThatThrownBy(() ->
                ThreadContext.run("fail", ctx -> {
                    throw new RuntimeException("boom");
                })
        ).isInstanceOf(RuntimeException.class).hasMessage("boom");
    }

    // ==================== 统计 API ====================

    @Test
    @DisplayName("getActiveContextCount - 反映当前活跃数")
    void activeContextCount() {
        int before = ThreadContext.getActiveContextCount();
        ThreadContext.create("test");
        assertThat(ThreadContext.getActiveContextCount()).isGreaterThanOrEqualTo(before);
        ThreadContext.clear();
    }

    @Test
    @DisplayName("getTotalContextsCreated - 单调递增")
    void totalContextsCreated() {
        long before = ThreadContext.getTotalContextsCreated();
        ThreadContext.create("test");
        ThreadContext.clear();
        assertThat(ThreadContext.getTotalContextsCreated()).isGreaterThan(before);
    }

    @Test
    @DisplayName("getTotalPropagations - 传播后增加")
    void totalPropagations() {
        ManagedThreadContext ctx = ThreadContext.create("source");
        ContextSnapshot snapshot = ctx.createSnapshot();
        long before = ThreadContext.getTotalPropagations();

        ThreadContext.propagate(snapshot);

        assertThat(ThreadContext.getTotalPropagations()).isGreaterThan(before);
        ThreadContext.clear();
    }

    @Test
    @DisplayName("detectPotentialLeaks - 正常场景返回 false")
    void detectPotentialLeaksNormal() {
        assertThat(ThreadContext.detectPotentialLeaks()).isFalse();
    }

    // ==================== ContextStatistics ====================

    @Test
    @DisplayName("getStatistics - 返回非空统计信息")
    void getStatistics() {
        ThreadContext.ContextStatistics stats = ThreadContext.getStatistics();
        assertThat(stats).isNotNull();
        assertThat(stats.timestamp).isNotNull();
        assertThat(stats.activeContexts).isGreaterThanOrEqualTo(0);
        assertThat(stats.totalCreated).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("ContextStatistics - toString 格式正确")
    void contextStatisticsToString() {
        ThreadContext.ContextStatistics stats = ThreadContext.getStatistics();
        String str = stats.toString();
        assertThat(str).contains("ContextStatistics");
        assertThat(str).contains("active=");
        assertThat(str).contains("created=");
        assertThat(str).contains("propagations=");
    }
}
