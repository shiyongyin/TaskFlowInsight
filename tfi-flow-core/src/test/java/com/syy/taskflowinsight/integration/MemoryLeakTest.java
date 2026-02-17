package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.api.TfiFlow;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.context.ZeroLeakThreadLocalManager;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 内存泄漏专项测试
 *
 * <p>验证 ThreadLocal 清理、try-with-resources 安全、线程池复用不泄漏。
 *
 * @author tfi-flow-core Test Team
 * @since 3.0.0
 */
class MemoryLeakTest {

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
    }

    private void forceCleanContext() {
        try {
            java.lang.reflect.Field field = TfiFlow.class.getDeclaredField("cachedFlowProvider");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception ignored) {}
        try {
            ManagedThreadContext ctx = ManagedThreadContext.current();
            if (ctx != null && !ctx.isClosed()) {
                ctx.close();
            }
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("泄漏 - try-with-resources 正确清理上下文")
    void tryWithResourcesCleanup() {
        TfiFlow.startSession("test");

        for (int i = 0; i < 100; i++) {
            try (TaskContext stage = TfiFlow.stage("iteration-" + i)) {
                stage.message("执行第 " + i + " 次");
            }
        }

        TfiFlow.endSession();
        TfiFlow.clear();

        // 清理后不应有残留会话
        assertThat(TfiFlow.getCurrentSession()).isNull();
    }

    @Test
    @DisplayName("泄漏 - 线程池复用不泄漏")
    void threadPoolReuseNoLeak() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                final int iteration = i;
                futures.add(pool.submit(() -> {
                    TfiFlow.startSession("pool-task-" + iteration);
                    try (TaskContext stage = TfiFlow.stage("work")) {
                        stage.message("doing work " + iteration);
                    }
                    TfiFlow.endSession();
                    TfiFlow.clear();
                }));
            }

            // 等待所有任务完成
            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }

        // 线程池关闭后，活跃上下文应较少（取决于具体实现）
        SafeContextManager manager = SafeContextManager.getInstance();
        // 允许一定的活跃上下文（主线程等），但不应该无限增长
        assertThat(manager.getActiveContextCount()).isLessThan(10);
    }

    @Test
    @DisplayName("泄漏 - endSession 后会话不再可达")
    void sessionNotReachableAfterEnd() {
        TfiFlow.startSession("ephemeral");
        try (TaskContext stage = TfiFlow.stage("task")) {
            stage.message("msg");
        }

        TfiFlow.endSession();
        TfiFlow.clear();

        assertThat(TfiFlow.getCurrentSession()).isNull();
        assertThat(TfiFlow.getCurrentTask()).isNull();
    }

    @Test
    @DisplayName("泄漏 - 重复 startSession 不累积")
    void repeatedStartSessionNoCumulation() {
        for (int i = 0; i < 50; i++) {
            TfiFlow.startSession("session-" + i);
            TfiFlow.endSession();
            TfiFlow.clear();
        }

        assertThat(TfiFlow.getCurrentSession()).isNull();
    }

    @Test
    @DisplayName("泄漏 - clear 清理所有状态")
    void clearCleansAllState() {
        TfiFlow.startSession("test");
        try (TaskContext s = TfiFlow.stage("s1")) {
            s.message("m1");
        }

        TfiFlow.clear();

        assertThat(TfiFlow.getCurrentSession()).isNull();
        assertThat(TfiFlow.getCurrentTask()).isNull();
        assertThat(TfiFlow.getTaskStack()).isEmpty();
    }

    @Test
    @DisplayName("ZeroLeak - 健康状态正常")
    void zeroLeakHealthCheck() {
        ZeroLeakThreadLocalManager manager = ZeroLeakThreadLocalManager.getInstance();
        ZeroLeakThreadLocalManager.HealthStatus status = manager.getHealthStatus();

        assertThat(status).isNotNull();
        assertThat(status.getLevel()).isNotNull();
    }

    @Test
    @DisplayName("ZeroLeak - 诊断信息可获取")
    void zeroLeakDiagnostics() {
        ZeroLeakThreadLocalManager manager = ZeroLeakThreadLocalManager.getInstance();
        var diagnostics = manager.getDiagnostics();

        assertThat(diagnostics).containsKeys(
                "contexts.registered",
                "contexts.active",
                "leaks.detected",
                "diagnostic.mode",
                "reflection.available"
        );
    }
}
